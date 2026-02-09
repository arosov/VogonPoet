# /// script
# dependencies = [
#   "aioquic",
#   "cryptography",
# ]
# ///

import asyncio
import logging
import sys
import os
import subprocess
import shutil
import json
from pathlib import Path

from aioquic.asyncio import QuicConnectionProtocol, serve
from aioquic.h3.connection import H3_ALPN, H3Connection
from aioquic.h3.events import (
    H3Event,
    HeadersReceived,
    WebTransportStreamDataReceived,
    DatagramReceived,
)
from aioquic.quic.configuration import QuicConfiguration
from aioquic.quic.events import ProtocolNegotiated, StreamReset, QuicEvent
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
import datetime

logging.basicConfig(level=logging.INFO, stream=sys.stderr)
logger = logging.getLogger(__name__)

# --- Configuration ---
PORT = 8123
MIN_VRAM_MB = 6000

# We assume the layout:
#  <app_root>/
#     bin/uv  (or in PATH)
#     scripts/bootstrap.py  (THIS FILE)
#     babelfish/            (The backend code)
#
# So BABELFISH_DIR is ../babelfish relative to this file in PROD.

SCRIPT_DIR = Path(__file__).resolve().parent

# Default to Prod location
BABELFISH_DIR = SCRIPT_DIR.parent / "babelfish"

# Dev fallback
if not BABELFISH_DIR.exists():
    logger.info("Prod babelfish dir not found, searching for dev location...")
    candidate = SCRIPT_DIR
    found = False
    # Walk up to find the project root containing 'babelfish'
    for _ in range(8):  # Check up to 8 levels
        candidate = candidate.parent
        if (candidate / "babelfish").is_dir():
            BABELFISH_DIR = candidate / "babelfish"
            found = True
            logger.info(f"Found dev babelfish dir at: {BABELFISH_DIR}")
            break

REQUIREMENTS_PATH = BABELFISH_DIR / "requirements_stt.txt"

# Standard UV path (assuming it's in PATH or bundled in bin/)
# For now, we assume 'uv' is in PATH because the launcher put it there or we find it.
UV_CMD = "uv"


# --- Cert Generation (Self-signed for localhost) ---
def generate_cert():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = issuer = x509.Name(
        [
            x509.NameAttribute(NameOID.COMMON_NAME, "localhost"),
        ]
    )
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(datetime.datetime.utcnow())
        .not_valid_after(datetime.datetime.utcnow() + datetime.timedelta(days=1))
        .add_extension(
            x509.SubjectAlternativeName([x509.DNSName("localhost")]),
            critical=False,
        )
        .sign(key, hashes.SHA256())
    )

    cert_pem = cert.public_bytes(serialization.Encoding.PEM)
    key_pem = key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    return cert_pem, key_pem


# --- Bootstrap Logic ---


class BootstrapHandler:
    def __init__(
        self, session_id, http: H3Connection, loop: asyncio.AbstractEventLoop, protocol
    ) -> None:
        self._session_id = session_id
        self._http = http
        self._loop = loop
        self._protocol = protocol
        self._task = None
        self._stream_id = None

    def h3_event_received(self, event: H3Event) -> None:
        if isinstance(event, WebTransportStreamDataReceived):
            # Client initiates by opening a stream and sending data (e.g. "HELLO")
            if self._stream_id is None:
                self._stream_id = event.stream_id
                logger.info(
                    f"Stream {self._stream_id} established. Starting bootstrap."
                )
                self.start_bootstrap()

    def start_bootstrap(self):
        if self._task is None:
            self._task = self._loop.create_task(self._run_bootstrap())

    async def _send_update(self, message: str, vad_state: str = "bootstrapping"):
        if self._stream_id is None:
            return

        data = (
            json.dumps({"type": "status", "message": message, "vad_state": vad_state})
            + "\n"
        )

        try:
            self._http._quic.send_stream_data(
                self._stream_id, data.encode("utf-8"), end_stream=False
            )
            self._protocol.transmit()
        except Exception as e:
            logger.error(f"Failed to send update: {e}")

    async def _run_command(self, cmd, cwd=None, env=None):
        process = await asyncio.create_subprocess_exec(
            *cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, cwd=cwd, env=env
        )

        while True:
            line = await process.stdout.readline()
            if not line:
                break
            line_str = line.decode("utf-8").strip()
            if line_str:
                logger.info(f"CMD: {line_str}")
                # Send log to client, but maybe throttle or filter?
                # For now, send everything that looks like progress
                await self._send_update(f"{line_str}")

        await process.wait()
        return process.returncode

    async def _run_bootstrap(self):
        try:
            await self._send_update("Detecting Hardware...")

            is_nvidia = False

            if os.environ.get("VOGON_FORCE_CPU"):
                logger.info("VOGON_FORCE_CPU is set. Ignoring GPU.")
                await self._send_update(
                    "Environment variable VOGON_FORCE_CPU set. Forcing CPU mode."
                )
            else:
                try:
                    # Query VRAM total in MiB
                    # nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits
                    output = subprocess.check_output(
                        [
                            "nvidia-smi",
                            "--query-gpu=memory.total",
                            "--format=csv,noheader,nounits",
                        ],
                        stderr=subprocess.STDOUT,
                    ).decode("utf-8")

                    # Parse output (can be multiple lines for multiple GPUs)
                    vram_values = [
                        int(x.strip())
                        for x in output.strip().split("\n")
                        if x.strip().isdigit()
                    ]

                    if vram_values:
                        max_vram = max(vram_values)
                        logger.info(
                            f"Detected NVIDIA GPU(s) with VRAM: {vram_values} MiB. Max: {max_vram} MiB"
                        )

                        if max_vram >= MIN_VRAM_MB:
                            is_nvidia = True
                            await self._send_update(
                                f"Found NVIDIA GPU with {max_vram} MiB VRAM (>= {MIN_VRAM_MB} MiB)."
                            )
                        else:
                            await self._send_update(
                                f"Found NVIDIA GPU but VRAM ({max_vram} MiB) is below minimum ({MIN_VRAM_MB} MiB). Falling back to CPU."
                            )
                    else:
                        logger.info("nvidia-smi returned no valid VRAM data.")
                except Exception as e:
                    logger.info(f"NVIDIA detection failed or no GPU found: {e}")
                    # Keep is_nvidia = False

            hw_type = "NVIDIA GPU" if is_nvidia else "CPU"
            await self._send_update(f"Detected {hw_type}. Preparing environment...")

            await asyncio.sleep(1)

            await self._send_update(f"Syncing dependencies in {BABELFISH_DIR}...")

            # Construct 'uv sync' command
            cmd = [UV_CMD, "sync"]

            # If NVIDIA, we want the CUDA index.
            # Note: The babelfish pyproject.toml already has a tool.uv.index for cu124.
            # If we wanted to override it for CPU-only systems, we would do it here.
            if not is_nvidia:
                # Force CPU index if no NVIDIA GPU found
                cmd.extend(
                    ["--extra-index-url", "https://download.pytorch.org/whl/cpu"]
                )
                await self._send_update("No GPU found, using CPU-only index...")

            ret = await self._run_command(cmd, cwd=BABELFISH_DIR)
            if ret != 0:
                await self._send_update("Error: Dependency sync failed. Check logs.")
                # We don't exit, maybe it can still run? Or wait for user?
                await asyncio.sleep(5)

            await self._send_update("Starting Engine...")
            await asyncio.sleep(0.5)

            # Close connection gracefully
            self._http._quic.send_stream_data(self._stream_id, b"", end_stream=True)
            self._protocol.transmit()
            self._http._quic.close(error_code=0)
            self._protocol.transmit()

            # Allow time for close packet to send
            await asyncio.sleep(0.5)

            # EXEC Babelfish
            logger.info("Exec-ing Babelfish...")

            # We use 'uv run' to run the babelfish script using the environment we just synced.
            # cwd should be BABELFISH_DIR
            os.chdir(BABELFISH_DIR)

            # Replace current process
            # On Linux, os.execvp replaces the process image (same PID).
            # On Windows, we use subprocess.run to wait for the child,
            # ensuring VogonPoet's handle on THIS process remains valid.

            args = [UV_CMD, "run", "babelfish.py"]

            if sys.platform == "win32":
                logger.info(f"Windows: Running {' '.join(args)} and waiting...")
                # We use subprocess.call to block. When this returns, the script exits.
                # If VogonPoet kills this script, Windows usually kills the child process tree
                # if started correctly (or we can use Job Objects if we need to be perfect).
                subprocess.call(args)
                sys.exit(0)
            else:
                logger.info(f"Linux: Exec-ing {' '.join(args)}")
                os.execvp(UV_CMD, args)

        except Exception as e:
            logger.error(f"Bootstrap failed: {e}")
            await self._send_update(f"Error: {e}")


# --- WebTransport Protocol (Same as raw_aioquic_server.py but uses BootstrapHandler) ---


class WebTransportProtocol(QuicConnectionProtocol):
    def __init__(self, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self._http: H3Connection = None
        self._handler: BootstrapHandler = None

    def quic_event_received(self, event: QuicEvent) -> None:
        if isinstance(event, ProtocolNegotiated):
            self._http = H3Connection(self._quic, enable_webtransport=True)
        elif isinstance(event, StreamReset) and self._handler is not None:
            pass  # Handle stream reset if needed

        if self._http is not None:
            for h3_event in self._http.handle_event(event):
                self._h3_event_received(h3_event)

    def _h3_event_received(self, event: H3Event) -> None:
        if isinstance(event, HeadersReceived):
            headers = {}
            for header, value in event.headers:
                headers[header] = value

            method = headers.get(b":method")
            protocol = headers.get(b":protocol")

            if method == b"CONNECT" and protocol == b"webtransport":
                self._handshake_webtransport(event.stream_id, headers)
            else:
                self._send_response(event.stream_id, 400, end_stream=True)

        if self._handler:
            self._handler.h3_event_received(event)

    def _handshake_webtransport(self, stream_id: int, request_headers: dict) -> None:
        path = request_headers.get(b":path")
        if path == b"/config" or path == b"/":  # Match Babelfish path
            # Create the handler
            self._handler = BootstrapHandler(
                stream_id, self._http, asyncio.get_event_loop(), self
            )
            self._send_response(stream_id, 200)
        else:
            self._send_response(stream_id, 404, end_stream=True)

    def _send_response(
        self, stream_id: int, status_code: int, end_stream=False
    ) -> None:
        headers = [(b":status", str(status_code).encode())]
        if status_code == 200:
            headers.append((b"sec-webtransport-http3-draft", b"draft02"))
        self._http.send_headers(
            stream_id=stream_id, headers=headers, end_stream=end_stream
        )


async def main():
    cert_path = SCRIPT_DIR / "bootstrap_server.crt"
    key_path = SCRIPT_DIR / "bootstrap_server.key"

    if not cert_path.exists() or not key_path.exists():
        logger.info("Generating new self-signed certificate...")
        cert_pem, key_pem = generate_cert()
        with open(cert_path, "wb") as f:
            f.write(cert_pem)
        with open(key_path, "wb") as f:
            f.write(key_pem)
    else:
        logger.info(f"Loading existing certificate from {cert_path}")

    configuration = QuicConfiguration(
        alpn_protocols=H3_ALPN,
        is_client=False,
        max_datagram_frame_size=65536,
        idle_timeout=3.0,
    )
    configuration.load_cert_chain(str(cert_path), str(key_path))

    logger.info(f"BOOTSTRAP SERVER STARTED port={PORT}")

    await serve(
        "127.0.0.1",
        PORT,
        configuration=configuration,
        create_protocol=WebTransportProtocol,
    )

    # Keep running until bootstrap finishes (which will kill the process)
    await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
