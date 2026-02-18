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
import argparse
import json
import ctypes
import ctypes.util
from pathlib import Path
from typing import Optional, List

import websockets

logging.basicConfig(level=logging.INFO, stream=sys.stderr)
logger = logging.getLogger(__name__)

# --- Configuration ---
PORT = 8123
_ws_server = None

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


# --- Hardware Detection Utilities ---


def detect_nvidia() -> bool:
    """Detects NVIDIA GPU presence by checking for libcuda/nvcuda libraries."""
    lib_name = "nvcuda.dll" if sys.platform == "win32" else "libcuda.so.1"
    try:
        # 1. Try direct loading (most reliable if in standard path)
        try:
            ctypes.CDLL(lib_name)
            logger.info(f"NVIDIA: Successfully loaded {lib_name}")
            return True
        except Exception:
            pass

        # 2. Try finding via util
        found_path = ctypes.util.find_library(lib_name)
        if found_path:
            try:
                ctypes.CDLL(found_path)
                logger.info(f"NVIDIA: Successfully loaded {found_path}")
                return True
            except Exception:
                pass
    except Exception as e:
        logger.debug(f"NVIDIA detection error: {e}")
    return False


def detect_amd_linux() -> bool:
    """Detects AMD GPU on Linux by checking for ROCm devices or libraries."""
    if sys.platform != "linux":
        return False

    if os.path.exists("/dev/kfd"):
        logger.info("AMD: Found /dev/kfd")
        return True

    try:
        if ctypes.util.find_library("libhsa-runtime64.so.1"):
            logger.info("AMD: Found libhsa-runtime64.so.1")
            return True
    except Exception:
        pass

    return False


def detect_metal() -> bool:
    """Detects Apple Silicon / Metal support."""
    if sys.platform != "darwin":
        return False
    return os.path.exists("/System/Library/Frameworks/Metal.framework")


def get_all_gpus() -> List[str]:
    """Retrieves a list of all detected GPU names."""
    gpus = []
    try:
        if sys.platform == "win32":
            out = (
                subprocess.check_output(
                    "wmic path win32_VideoController get name", shell=True
                )
                .decode()
                .strip()
                .split("\n")
            )
            for line in out[1:]:
                name = line.strip()
                if name:
                    gpus.append(name)
        elif sys.platform == "linux":
            if shutil.which("lspci"):
                out = subprocess.check_output(["lspci"]).decode().lower()
                for line in out.split("\n"):
                    if "vga" in line or "3d controller" in line:
                        if "nvidia" in line:
                            gpus.append("NVIDIA GPU")
                        elif "amd" in line or "ati" in line:
                            gpus.append("AMD GPU")
                        elif "intel" in line:
                            gpus.append("Intel Graphics")
    except Exception:
        pass
    return list(dict.fromkeys(gpus))  # Deduplicate


# --- Bootstrap Logic ---


class BootstrapServer:
    def __init__(
        self, loop: asyncio.AbstractEventLoop, models_dir: Optional[Path] = None
    ) -> None:
        self._loop = loop
        self._task = None
        self._websocket = None
        self._models_dir = models_dir or (BABELFISH_DIR / "models")

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
            return

        from huggingface_hub import snapshot_download

        await self._send_update(f"Provisioning model from {repo_id}...")

        try:
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

            hw_mode = "cpu"
            extra_to_install = "cpu"

            detected_caps = []
            if detect_nvidia():
                detected_caps.append("NVIDIA")
            if detect_amd_linux():
                detected_caps.append("AMD (ROCm)")
            if detect_metal():
                detected_caps.append("Apple Metal")

            all_gpu_names = get_all_gpus()

            # Check for config file to see if a specific device is requested
            config_device = "auto"
            auto_detect = True
            app_data_dir = os.environ.get("VOGON_APP_DATA_DIR")
            if app_data_dir:
                config_path = Path(app_data_dir) / "babelfish.config.json"
                if config_path.exists():
                    try:
                        with open(config_path, "r") as f:
                            data = json.load(f)
                            hw_config = data.get("hardware", {})
                            config_device = hw_config.get("device", "auto")
                            auto_detect = hw_config.get("auto_detect", True)
                    except Exception as e:
                        logger.warning(f"Failed to read config for bootstrap: {e}")

            env_forces_cpu = str(os.environ.get("VOGON_FORCE_CPU", "")).lower() in (
                "1",
                "true",
                "yes",
                "on",
            )

            if env_forces_cpu or (not auto_detect and config_device == "cpu"):
                logger.info("CPU mode requested via config or env. Forcing CPU mode.")
                gpu_desc = "CPU (Forced)"
                hw_mode = "cpu"
                extra_to_install = "cpu"
            elif not auto_detect and (
                config_device
                in (
                    "cuda",
                    "rocm",
                    "dml",
                    "metal",
                )
                or config_device.startswith("cuda:")
            ):
                logger.info(
                    f"Specific GPU requested: {config_device}. Forcing GPU mode."
                )
                gpu_desc = config_device.upper()
                if config_device.startswith("cuda") or config_device == "cuda":
                    if sys.platform == "win32":
                        hw_mode = "nvidia_win"
                        extra_to_install = "windows-gpu"
                    else:
                        hw_mode = "nvidia_linux"
                        extra_to_install = "nvidia-linux"
                elif config_device == "rocm":
                    hw_mode = "amd_linux"
                    extra_to_install = "amd-linux"
                elif config_device == "metal":
                    hw_mode = "metal"
                    extra_to_install = "cpu"
                elif config_device == "dml":
                    hw_mode = "windows_gpu"
                    extra_to_install = "windows-gpu"
            else:
                # Selection logic (Priority: NVIDIA > AMD > Metal > DML)
                is_nvidia = "NVIDIA" in detected_caps or any(
                    "nvidia" in g.lower() for g in all_gpu_names
                )
                is_amd = "AMD (ROCm)" in detected_caps or any(
                    "amd" in g.lower() or "ati" in g.lower() for g in all_gpu_names
                )

                if is_nvidia:
                    logger.info(
                        "NVIDIA GPU detected, selecting nvidia_linux/nvidia_win mode"
                    )
                    if sys.platform == "win32":
                        hw_mode = "nvidia_win"
                        extra_to_install = "nvidia-win"
                    else:
                        hw_mode = "nvidia_linux"
                        extra_to_install = "nvidia-linux"
                elif is_amd:
                    logger.info("AMD GPU detected, selecting amd_linux mode")
                    hw_mode = "amd_linux"
                    extra_to_install = "amd-linux"
                elif "Apple Metal" in detected_caps:
                    logger.info("Apple Silicon detected, selecting metal mode")
                    hw_mode = "metal"
                    extra_to_install = "cpu"
                elif sys.platform == "win32" and all_gpu_names:
                    logger.info(
                        "Generic Windows GPU detected, selecting windows_gpu mode"
                    )
                    hw_mode = "windows_gpu"
                    extra_to_install = "windows-gpu"
                else:
                    logger.info("No GPU detected, staying in CPU mode")
                    hw_mode = "cpu"
                    extra_to_install = "cpu"

                # Build descriptive string
                if hw_mode == "cpu":
                    gpu_desc = "CPU"
                else:
                    gpu_desc = " + ".join(
                        all_gpu_names if all_gpu_names else detected_caps
                    )

            logger.info(f"Final Decision: hw_mode={hw_mode}, extra={extra_to_install}")
            await self._send_update(
                f"Detected Hardware: {gpu_desc}. Preparing environment for {hw_mode}..."
            )
            await asyncio.sleep(1)

            # --- Model Provisioning ---
            models_dir = self._models_dir
            models_dir.mkdir(parents=True, exist_ok=True)
            model_dest = models_dir / MODEL_DIR_NAME

            if hw_mode == "cpu":
                allow_patterns = ["*.onnx", "*.onnx.data", "config.json", "*.txt"]
            else:
                # Only high-precision files
                # We exclude int8
                allow_patterns = [
                    "encoder-model.onnx",
                    "decoder_joint-model.onnx",
                    "encoder-model.onnx.data",
                    "config.json",
                    "vocab.txt",
                ]

            await self._provision_model(MODEL_REPO, model_dest, allow_patterns)

            await self._send_update(f"Syncing dependencies in {BABELFISH_DIR}...")

            # --- Conflict Resolution ---
            # All onnxruntime variants (cpu, gpu, directml, etc.) provide the same 'onnxruntime'
            # python package and conflict. uv sync sometimes leaves broken files when switching.
            # We force a clean state by uninstalling all variants before sync.
            ort_variants = [
                "onnxruntime",
                "onnxruntime-gpu",
                "onnxruntime-directml",
                "onnxruntime-rocm",
                "onnxruntime-openvino",
            ]
            try:
                logger.info("Ensuring clean onnxruntime state...")
                await self._run_command(
                    [UV_CMD, "pip", "uninstall", *ort_variants],
                    cwd=BABELFISH_DIR,
                )
            except Exception as e:
                logger.debug(f"Conflict resolution cleanup note: {e}")

            # Build UV sync command
            cmd = [UV_CMD, "sync"]
            if extra_to_install:
                cmd.extend(["--extra", extra_to_install])

            # Force reinstall of the specific ORT package to prevent "broken" installs
            # where the package directory exists but is empty or wrong.
            if extra_to_install == "cpu":
                cmd.extend(["--reinstall-package", "onnxruntime"])
            elif extra_to_install == "nvidia-linux":
                cmd.extend(["--reinstall-package", "onnxruntime-gpu"])
            elif extra_to_install == "nvidia-win":
                cmd.extend(["--reinstall-package", "onnxruntime-gpu"])
            elif extra_to_install == "amd-linux":
                cmd.extend(["--reinstall-package", "onnxruntime-rocm"])
            elif extra_to_install == "windows-gpu":
                cmd.extend(["--reinstall-package", "onnxruntime-directml"])

            # Index override
            if hw_mode == "amd_linux":
                os.environ["UV_INDEX_PYTORCH_URL"] = (
                    "https://download.pytorch.org/whl/rocm6.2"
                )
            else:
                os.environ["UV_INDEX_PYTORCH_URL"] = (
                    "https://download.pytorch.org/whl/cpu"
                )

            ret = await self._run_command(cmd, cwd=BABELFISH_DIR)
            if ret != 0:
                await self._send_update("Error: Dependency sync failed. Check logs.")
                await asyncio.sleep(5)

            await self._send_update("Starting Babelfish...")
            await asyncio.sleep(0.5)

            if self._websocket:
                await self._websocket.close()

            # EXEC Babelfish
            logger.info(f"Exec-ing Babelfish (mode: {hw_mode})...")
            os.chdir(BABELFISH_DIR)

            env = os.environ.copy()

            # --- DLL Path Fix for NVIDIA on Windows & Linux ---
            if (
                hw_mode in ("nvidia_win", "nvidia_linux", "amd_linux")
                and hw_mode != "cpu"
            ):
                # Ensure onnxruntime and shared libraries are visible
                libs_paths = []

                # 1. ORT CAPI
                # Linux Path
                venv_capi_linux = list(
                    BABELFISH_DIR.glob(
                        ".venv/lib/python*/site-packages/onnxruntime/capi"
                    )
                )
                if venv_capi_linux:
                    libs_paths.append(str(venv_capi_linux[0].resolve()))

                # Windows Path (Lib instead of lib)
                if sys.platform == "win32":
                    venv_capi_win = list(
                        BABELFISH_DIR.glob(".venv/Lib/site-packages/onnxruntime/capi")
                    )
                    if venv_capi_win:
                        libs_paths.append(str(venv_capi_win[0].resolve()))

                # 2. NVIDIA libraries (if installed via pip)
                # We search for any directory inside 'nvidia' package that contains .dll (Win) or .so (Linux)
                site_packages_glob_linux = ".venv/lib/python*/site-packages/nvidia"
                site_packages_glob_win = ".venv/Lib/site-packages/nvidia"

                nvidia_root = list(BABELFISH_DIR.glob(site_packages_glob_linux))
                if not nvidia_root and sys.platform == "win32":
                    nvidia_root = list(BABELFISH_DIR.glob(site_packages_glob_win))

                if nvidia_root:
                    # Recursively find dirs with DLLs/SOs
                    # For performance, limit depth if needed, but rglob is usually fine
                    ext = "*.dll" if sys.platform == "win32" else "*.so*"
                    found_dirs = set()
                    for path in nvidia_root[0].rglob(ext):
                        parent = str(path.parent.resolve())
                        if parent not in found_dirs:
                            found_dirs.add(parent)
                            libs_paths.append(parent)

                # 3. Apply to Environment
                if libs_paths:
                    if sys.platform == "win32":
                        # On Windows, add to PATH
                        current_path = env.get("PATH", "")
                        new_path = ";".join(libs_paths)
                        env["PATH"] = f"{new_path};{current_path}"
                        logger.info(f"Windows: Added {len(libs_paths)} paths to PATH")
                    else:
                        # On Linux, add to LD_LIBRARY_PATH
                        current_ld = env.get("LD_LIBRARY_PATH", "")
                        new_ld = ":".join(libs_paths)
                        env["LD_LIBRARY_PATH"] = (
                            f"{new_ld}:{current_ld}" if current_ld else new_ld
                        )
                        logger.info(
                            f"Linux: Added {len(libs_paths)} paths to LD_LIBRARY_PATH"
                        )

            args = [UV_CMD, "run", "babelfish", "--port", "8124"]
            if hw_mode == "cpu" and (
                env_forces_cpu or (not auto_detect and config_device == "cpu")
            ):
                args.append("--cpu")

            if sys.platform == "win32":
                logger.info("Starting Babelfish on port 8124...")
                subprocess.call(args, env=env)
                sys.exit(0)
            else:
                os.execvpe(UV_CMD, args, env)

        except Exception as e:
            logger.error(f"Bootstrap failed: {e}")
            await self._send_update(f"Error: {e}")


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--models-dir", type=str, help="Directory to store AI models")
    args = parser.parse_args()

    models_dir = Path(args.models_dir) if args.models_dir else None

    server = BootstrapServer(asyncio.get_event_loop(), models_dir=models_dir)
    global _ws_server
    _ws_server = websockets.serve(server.handle_connection, "127.0.0.1", PORT)
    async with _ws_server:
        logger.info(f"BOOTSTRAP SERVER STARTED port={PORT}")
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
