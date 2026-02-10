# /// script
# dependencies = [
#   "websockets",
#   "huggingface-hub",
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
from typing import Optional, List

import websockets

logging.basicConfig(level=logging.INFO, stream=sys.stderr)
logger = logging.getLogger(__name__)

# --- Configuration ---
PORT = 8123

# Multilingual Parakeet-TDT v3 (25 languages)
MODEL_REPO = "istupakov/parakeet-tdt-0.6b-v3-onnx"
MODEL_DIR_NAME = "nemo-parakeet-tdt-0.6b-v3"

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

        if process.stdout is None:
            await process.wait()
            return process.returncode

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

    async def _provision_model(
        self, repo_id: str, dest_dir: Path, allow_patterns: List[str]
    ):
        """Provisions a model from Hugging Face."""
        if dest_dir.exists() and any(dest_dir.glob("*.onnx")):
            # Check if all requested patterns are present (simplified)
            return

        from huggingface_hub import snapshot_download

        await self._send_update(f"Provisioning model from {repo_id}...")

        try:
            # We use snapshot_download to get specific files
            # Note: This runs synchronously, so we run it in an executor
            loop = asyncio.get_running_loop()

            def download():
                return snapshot_download(
                    repo_id=repo_id,
                    local_dir=str(dest_dir),
                    allow_patterns=allow_patterns,
                )

            await loop.run_in_executor(None, download)
            await self._send_update("Model provisioning complete.")
        except Exception as e:
            logger.error(f"Model provisioning failed: {e}")
            await self._send_update(f"Error provisioning model: {e}")
            raise

    async def _run_bootstrap(self):
        try:
            await self._send_update("Detecting Hardware...")

            hw_mode = "cpu"  # default
            gpu_desc = "None"
            extra_to_install = None

            if os.environ.get("VOGON_FORCE_CPU"):
                logger.info("VOGON_FORCE_CPU is set. Ignoring GPU.")
                await self._send_update(
                    "Environment variable VOGON_FORCE_CPU set. Forcing CPU mode."
                )
            else:
                # 1. Check NVIDIA (Linux/Windows)
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
                        if sys.platform == "win32":
                            hw_mode = "nvidia_win"
                            gpu_desc = f"NVIDIA GPU detected"
                            extra_to_install = "windows-gpu"
                        else:
                            hw_mode = "nvidia_linux"
                            gpu_desc = f"NVIDIA GPU detected"
                            extra_to_install = "nvidia-linux"
                except Exception:
                    pass

                # 2. Check AMD (Linux/ROCm)
                if hw_mode == "cpu" and sys.platform == "linux":
                    try:
                        if shutil.which("rocm-smi") or os.path.exists("/dev/kfd"):
                            hw_mode = "amd_linux"
                            gpu_desc = "AMD GPU detected (ROCm)"
                            extra_to_install = "amd-linux"
                    except Exception as e:
                        logger.info(f"AMD/ROCm detection failed: {e}")

                # 3. Check Windows (Unified DirectML for AMD/Intel/NVIDIA)
                if hw_mode == "cpu" and sys.platform == "win32":
                    try:
                        out_names = (
                            subprocess.check_output(
                                "wmic path win32_VideoController get name", shell=True
                            )
                            .decode()
                            .strip()
                            .split("\n")[1:]
                        )
                        for name in out_names:
                            name = name.strip()
                            if name and (
                                "AMD" in name or "Radeon" in name or "Intel" in name
                            ):
                                hw_mode = "windows_gpu"
                                gpu_desc = f"{name} detected (DML)"
                                extra_to_install = "windows-gpu"
                                break
                    except Exception as e:
                        logger.info(f"Windows GPU detection failed: {e}")

            await self._send_update(
                f"Detected Hardware: {gpu_desc}. Preparing environment..."
            )
            await asyncio.sleep(1)

            # --- Model Provisioning ---
            models_dir = BABELFISH_DIR / "models"
            models_dir.mkdir(exist_ok=True)
            model_dest = models_dir / MODEL_DIR_NAME

            # Quantization policy:
            # CPU: All modes (int8, fp16, fp32)
            # GPU: Highest only (fp16/fp32)
            if hw_mode == "cpu":
                allow_patterns = ["*.onnx", "*.onnx.data", "config.json", "*.txt"]
            else:
                # Only high-precision files
                # Usually model.onnx (fp32) and model_fp16.onnx
                # We exclude int8
                allow_patterns = [
                    "model.onnx",
                    "model_fp16.onnx",
                    "model.onnx.data",
                    "config.json",
                    "*.txt",
                ]

            await self._provision_model(MODEL_REPO, model_dest, allow_patterns)

            await self._send_update(f"Syncing dependencies in {BABELFISH_DIR}...")

            # Build UV sync command
            cmd = [UV_CMD, "sync"]

            if extra_to_install:
                cmd.extend(["--extra", extra_to_install])

            # Override index based on hardware
            if hw_mode == "amd_linux":
                await self._send_update("Setting ROCm index for AMD GPU...")
                os.environ["UV_INDEX_PYTORCH_URL"] = (
                    "https://download.pytorch.org/whl/rocm6.2"
                )
            elif hw_mode == "cpu":
                await self._send_update("Using CPU-only index...")
                os.environ["UV_INDEX_PYTORCH_URL"] = (
                    "https://download.pytorch.org/whl/cpu"
                )
            else:
                # Default or CUDA index
                # Note: For ONNX, standard CPU torch is actually fine and smaller.
                # But if they have NVIDIA, maybe they want CUDA torch for other things?
                # Actually, the spec says "Standardize base torch to cpu only".
                # So we always use CPU index for torch to save space.
                os.environ["UV_INDEX_PYTORCH_URL"] = (
                    "https://download.pytorch.org/whl/cpu"
                )

            ret = await self._run_command(cmd, cwd=BABELFISH_DIR)
            if ret != 0:
                await self._send_update("Error: Dependency sync failed. Check logs.")
                await asyncio.sleep(5)

            await self._send_update("Starting Engine...")
            await asyncio.sleep(0.5)

            if self._websocket:
                await self._websocket.close()

            # EXEC Babelfish
            logger.info(f"Exec-ing Babelfish (mode: {hw_mode})...")
            os.chdir(BABELFISH_DIR)

            # Setup environment for ONNX Runtime
            env = os.environ.copy()
            if hw_mode != "cpu" and sys.platform == "linux":
                # Ensure onnxruntime shared libraries are in LD_LIBRARY_PATH for Linux
                venv_capi = list(
                    BABELFISH_DIR.glob(
                        ".venv/lib/python*/site-packages/onnxruntime/capi"
                    )
                )
                if venv_capi:
                    capi_path = venv_capi[0].resolve()
                    current_ld = env.get("LD_LIBRARY_PATH", "")
                    env["LD_LIBRARY_PATH"] = (
                        f"{capi_path}:{current_ld}" if current_ld else str(capi_path)
                    )

            args = [UV_CMD, "run", "babelfish"]

            if hw_mode == "cpu":
                args.append("--cpu")

            if sys.platform == "win32":
                logger.info(f"Windows: Running {' '.join(args)} and waiting...")
                subprocess.call(args, env=env)
                sys.exit(0)
            else:
                logger.info(f"Linux: Exec-ing {' '.join(args)}")
                os.execvpe(UV_CMD, args, env)

        except Exception as e:
            logger.error(f"Bootstrap failed: {e}")
            await self._send_update(f"Error: {e}")


async def main():
    logger.info(f"BOOTSTRAP SERVER STARTED port={PORT}")

    # Cleanup any existing process on this port
    if sys.platform != "win32":
        try:
            subprocess.run(["fuser", "-k", f"{PORT}/tcp"], stderr=subprocess.DEVNULL)
        except Exception:
            pass

    server = BootstrapServer(asyncio.get_event_loop())

    async with websockets.serve(server.handle_connection, "127.0.0.1", PORT):
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
