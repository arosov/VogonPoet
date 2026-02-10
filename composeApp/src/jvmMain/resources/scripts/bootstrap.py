# /// script
# dependencies = [
#   "websockets",
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
from typing import Optional

import websockets

logging.basicConfig(level=logging.INFO, stream=sys.stderr)
logger = logging.getLogger(__name__)

# --- Configuration ---
PORT = 8123
MIN_VRAM_MB = 6000

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

UV_CMD = "uv"


# --- Bootstrap Logic ---


class BootstrapServer:
    def __init__(self, loop: asyncio.AbstractEventLoop) -> None:
        self._loop = loop
        self._task = None
        self._websocket = None

    async def handle_connection(self, websocket):
        if self._websocket is not None:
            await websocket.close(1008, "Only one connection allowed during bootstrap")
            return

        logger.info(f"New connection established. Starting bootstrap.")
        self._websocket = websocket

        try:
            # Client initiates by sending "HELLO" or we just start
            self.start_bootstrap()

            # Keep connection open until bootstrap finishes or client disconnects
            async for _ in websocket:
                pass
        except websockets.exceptions.ConnectionClosed:
            logger.info("Connection closed during bootstrap.")
        finally:
            if self._websocket == websocket:
                self._websocket = None

    def start_bootstrap(self):
        if self._task is None:
            self._task = self._loop.create_task(self._run_bootstrap())

    async def _send_update(self, message: str, vad_state: str = "bootstrapping"):
        if self._websocket is None:
            return

        data = json.dumps(
            {"type": "status", "message": message, "vad_state": vad_state}
        )

        try:
            await self._websocket.send(data)
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
                    output = subprocess.check_output(
                        [
                            "nvidia-smi",
                            "--query-gpu=memory.total",
                            "--format=csv,noheader,nounits",
                        ],
                        stderr=subprocess.STDOUT,
                    ).decode("utf-8")

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

            hw_type = "NVIDIA GPU" if is_nvidia else "CPU"
            await self._send_update(f"Detected {hw_type}. Preparing environment...")

            await asyncio.sleep(1)

            await self._send_update(f"Syncing dependencies in {BABELFISH_DIR}...")

            cmd = [UV_CMD, "sync"]

            if not is_nvidia:
                cmd.extend(
                    ["--extra-index-url", "https://download.pytorch.org/whl/cpu"]
                )
                await self._send_update("No GPU found, using CPU-only index...")

            ret = await self._run_command(cmd, cwd=BABELFISH_DIR)
            if ret != 0:
                await self._send_update("Error: Dependency sync failed. Check logs.")
                await asyncio.sleep(5)

            await self._send_update("Starting Engine...")
            await asyncio.sleep(0.5)

            if self._websocket:
                await self._websocket.close()

            # EXEC Babelfish
            logger.info("Exec-ing Babelfish...")
            os.chdir(BABELFISH_DIR)

            args = [UV_CMD, "run", "babelfish"]

            if not is_nvidia:
                args.append("--cpu")

            if sys.platform == "win32":
                logger.info(f"Windows: Running {' '.join(args)} and waiting...")
                subprocess.call(args)
                sys.exit(0)
            else:
                logger.info(f"Linux: Exec-ing {' '.join(args)}")
                os.execvp(UV_CMD, args)

        except Exception as e:
            logger.error(f"Bootstrap failed: {e}")
            await self._send_update(f"Error: {e}")


async def main():
    logger.info(f"BOOTSTRAP SERVER STARTED port={PORT}")

    server = BootstrapServer(asyncio.get_event_loop())

    async with websockets.serve(server.handle_connection, "127.0.0.1", PORT):
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
