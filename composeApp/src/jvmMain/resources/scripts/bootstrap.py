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
from typing import Optional, List, Dict, Any

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


class HardwareDetector:
    @staticmethod
    def detect_nvidia() -> bool:
        """Detects NVIDIA GPU presence by checking for libcuda/nvcuda libraries."""
        lib_name = "nvcuda.dll" if sys.platform == "win32" else "libcuda.so.1"
        try:
            # 1. Try direct loading
            try:
                ctypes.CDLL(lib_name)
                return True
            except Exception:
                pass

            # 2. Try finding via util
            found_path = ctypes.util.find_library(lib_name)
            if found_path:
                try:
                    ctypes.CDLL(found_path)
                    return True
                except Exception:
                    pass
        except Exception:
            pass
        return False

    @staticmethod
    def detect_amd_linux() -> bool:
        """Detects AMD GPU on Linux by checking for ROCm devices or libraries."""
        if sys.platform != "linux":
            return False
        if os.path.exists("/dev/kfd"):
            return True
        try:
            if ctypes.util.find_library("libhsa-runtime64.so.1"):
                return True
        except Exception:
            pass
        return False

    @staticmethod
    def detect_metal() -> bool:
        """Detects Apple Silicon / Metal support."""
        if sys.platform != "darwin":
            return False
        return os.path.exists("/System/Library/Frameworks/Metal.framework")

    @staticmethod
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
        return list(dict.fromkeys(gpus))

    def get_best_mode(self) -> Dict[str, str]:
        detected_caps = []
        if self.detect_nvidia():
            detected_caps.append("NVIDIA")
        if self.detect_amd_linux():
            detected_caps.append("AMD (ROCm)")
        if self.detect_metal():
            detected_caps.append("Apple Metal")

        all_gpu_names = self.get_all_gpus()

        # Config/Env check
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
                    logger.warning(f"Failed to read config: {e}")

        env_forces_cpu = str(os.environ.get("VOGON_FORCE_CPU", "")).lower() in (
            "1",
            "true",
            "yes",
            "on",
        )

        if env_forces_cpu or (not auto_detect and config_device == "cpu"):
            return {"hw_mode": "cpu", "extra": "cpu", "desc": "CPU (Forced)"}

        if not auto_detect and config_device != "auto":
            # Specific mapping
            if config_device.startswith("cuda"):
                if sys.platform == "win32":
                    return {
                        "hw_mode": "nvidia_win",
                        "extra": "nvidia-win",
                        "desc": "NVIDIA (Config)",
                    }
                return {
                    "hw_mode": "nvidia_linux",
                    "extra": "nvidia-linux",
                    "desc": "NVIDIA (Config)",
                }
            if config_device == "rocm":
                return {
                    "hw_mode": "amd_linux",
                    "extra": "amd-linux",
                    "desc": "AMD ROCm (Config)",
                }
            if config_device == "metal":
                return {
                    "hw_mode": "metal",
                    "extra": "cpu",
                    "desc": "Apple Metal (Config)",
                }
            if config_device == "dml":
                return {
                    "hw_mode": "windows_gpu",
                    "extra": "windows-gpu",
                    "desc": "DirectML (Config)",
                }

        # Auto-detection
        is_nvidia = "NVIDIA" in detected_caps or any(
            "nvidia" in g.lower() for g in all_gpu_names
        )
        is_amd = "AMD (ROCm)" in detected_caps or any(
            "amd" in g.lower() or "ati" in g.lower() for g in all_gpu_names
        )

        if is_nvidia:
            if sys.platform == "win32":
                return {
                    "hw_mode": "nvidia_win",
                    "extra": "nvidia-win",
                    "desc": "NVIDIA GPU",
                }
            return {
                "hw_mode": "nvidia_linux",
                "extra": "nvidia-linux",
                "desc": "NVIDIA GPU",
            }
        if is_amd:
            return {
                "hw_mode": "amd_linux",
                "extra": "amd-linux",
                "desc": "AMD ROCm GPU",
            }

        # Smart Mac Detection
        if "Apple Metal" in detected_caps:
            import platform

            arch = platform.machine().lower()
            if "arm" in arch or "aarch64" in arch:
                # Apple Silicon -> CoreML Capable
                return {
                    "hw_mode": "metal",
                    "extra": "cpu",
                    "desc": "Apple Silicon (CoreML capable)",
                }
            else:
                # Intel Mac -> Prefer CPU for stability unless forced
                return {
                    "hw_mode": "cpu",
                    "extra": "cpu",
                    "desc": "Intel Mac (CPU Mode)",
                }

        if sys.platform == "win32" and all_gpu_names:
            return {
                "hw_mode": "windows_gpu",
                "extra": "windows-gpu",
                "desc": "Windows Generic GPU (DirectML)",
            }

        return {"hw_mode": "cpu", "extra": "cpu", "desc": "CPU"}


class EnvironmentManager:
    def __init__(self, babelfish_dir: Path):
        self.babelfish_dir = babelfish_dir
        self.marker_file = babelfish_dir / ".last_hw_mode"

    def check_marker(self, hw_mode: str) -> bool:
        if self.marker_file.exists():
            return self.marker_file.read_text().strip() == hw_mode
        return False

    def write_marker(self, hw_mode: str):
        self.marker_file.write_text(hw_mode)

    async def provision_model(
        self,
        repo_id: str,
        dest_dir: Path,
        allow_patterns: List[str],
        status_callback=None,
    ):
        if dest_dir.exists() and any(dest_dir.glob("*.onnx")):
            return

        from huggingface_hub import snapshot_download

        if status_callback:
            await status_callback(f"Provisioning model from {repo_id}...")

        loop = asyncio.get_running_loop()
        await loop.run_in_executor(
            None,
            lambda: snapshot_download(
                repo_id=repo_id, local_dir=str(dest_dir), allow_patterns=allow_patterns
            ),
        )

    def get_env_with_dll_injection(self, hw_mode: str) -> Dict[str, str]:
        env = os.environ.copy()
        if hw_mode == "cpu":
            return env

        libs_paths = []
        # ORT CAPI
        if sys.platform == "win32":
            capi = list(
                self.babelfish_dir.glob(".venv/Lib/site-packages/onnxruntime/capi")
            )
        else:
            capi = list(
                self.babelfish_dir.glob(
                    ".venv/lib/python*/site-packages/onnxruntime/capi"
                )
            )

        if capi:
            libs_paths.append(str(capi[0].resolve()))

        # NVIDIA libraries
        nv_glob = (
            ".venv/Lib/site-packages/nvidia"
            if sys.platform == "win32"
            else ".venv/lib/python*/site-packages/nvidia"
        )
        nv_roots = list(self.babelfish_dir.glob(nv_glob))
        if nv_roots:
            ext = "*.dll" if sys.platform == "win32" else "*.so*"
            for path in nv_roots[0].rglob(ext):
                parent = str(path.parent.resolve())
                if parent not in libs_paths:
                    libs_paths.append(parent)

        if libs_paths:
            if sys.platform == "win32":
                current = env.get("PATH", "")
                env["PATH"] = ";".join(libs_paths) + ";" + current
            else:
                current = env.get("LD_LIBRARY_PATH", "")
                env["LD_LIBRARY_PATH"] = ":".join(libs_paths) + (
                    ":" + current if current else ""
                )

        return env


class BootstrapServer:
    def __init__(self, models_dir: Optional[Path] = None):
        self.loop = asyncio.get_event_loop()
        self.websocket = None
        self.models_dir = models_dir or (BABELFISH_DIR / "models")
        self.detector = HardwareDetector()
        self.env_manager = EnvironmentManager(BABELFISH_DIR)
        self.completion_future = None

    def set_completion_future(self, future):
        self.completion_future = future

    async def handle_connection(self, websocket):
        if self.websocket:
            await websocket.close(1008, "Only one connection allowed")
            return
        self.websocket = websocket
        try:
            await self.run_bootstrap()
        except Exception as e:
            logger.error(f"Bootstrap failed: {e}")
            await self.send_update(f"Error: {e}")
        finally:
            self.websocket = None

    async def send_update(self, message: str, vad_state: str = "bootstrapping"):
        if not self.websocket:
            return
        data = json.dumps(
            {"type": "status", "message": message, "vad_state": vad_state}
        )
        try:
            await self.websocket.send(data)
        except Exception:
            pass

    async def run_command(self, cmd, cwd=None, env=None):
        process = await asyncio.create_subprocess_exec(
            *cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, cwd=cwd, env=env
        )
        if process.stdout:
            while True:
                line = await process.stdout.readline()
                if not line:
                    break
                line_str = line.decode().strip()
                if line_str:
                    logger.info(f"CMD: {line_str}")
                    await self.send_update(line_str)
        return await process.wait()

    async def run_bootstrap(self):
        await self.send_update("Detecting Hardware...")
        hw = self.detector.get_best_mode()
        hw_mode = hw["hw_mode"]
        extra = hw["extra"]

        await self.send_update(f"Hardware: {hw['desc']}. Target mode: {hw_mode}")

        # Model Provisioning
        self.models_dir.mkdir(parents=True, exist_ok=True)
        model_dest = self.models_dir / MODEL_DIR_NAME
        patterns = ["*.onnx", "*.onnx.data", "config.json", "*.txt"]
        if hw_mode != "cpu":
            patterns = [
                "encoder-model.onnx",
                "decoder_joint-model.onnx",
                "encoder-model.onnx.data",
                "config.json",
                "vocab.txt",
            ]

        await self.env_manager.provision_model(
            MODEL_REPO, model_dest, patterns, self.send_update
        )

        # Dependency Sync
        if not self.env_manager.check_marker(hw_mode):
            await self.send_update(f"Syncing dependencies for {hw_mode}...")

            # Conflict resolution
            ort_variants = [
                "onnxruntime",
                "onnxruntime-gpu",
                "onnxruntime-directml",
                "onnxruntime-rocm",
                "onnxruntime-openvino",
            ]
            await self.run_command(
                [UV_CMD, "pip", "uninstall", *ort_variants], cwd=BABELFISH_DIR
            )

            cmd = [UV_CMD, "sync", "--extra", extra]
            # Force reinstall of correct ORT
            ort_map = {
                "cpu": "onnxruntime",
                "nvidia_win": "onnxruntime-gpu",
                "nvidia_linux": "onnxruntime-gpu",
                "amd_linux": "onnxruntime-rocm",
                "windows_gpu": "onnxruntime-directml",
                "metal": "onnxruntime",
            }
            if hw_mode in ort_map:
                cmd.extend(["--reinstall-package", ort_map[hw_mode]])

            env = os.environ.copy()
            env["UV_INDEX_PYTORCH_URL"] = (
                "https://download.pytorch.org/whl/rocm6.2"
                if hw_mode == "amd_linux"
                else "https://download.pytorch.org/whl/cpu"
            )

            ret = await self.run_command(cmd, cwd=BABELFISH_DIR, env=env)
            if ret == 0:
                self.env_manager.write_marker(hw_mode)
            else:
                await self.send_update("Sync failed!")
                return
        else:
            await self.send_update("Environment matches hardware, skipping sync.")

        await self.send_update("Starting Babelfish...")
        await asyncio.sleep(0.5)
        if self.websocket:
            await self.websocket.close()

        # Final Launch Preparation
        launch_env = self.env_manager.get_env_with_dll_injection(hw_mode)
        # Use the same PORT for the actual server
        args = [UV_CMD, "run", "babelfish", "--port", str(PORT)]
        if hw_mode == "cpu":
            args.append("--cpu")

        # Signal completion to the main loop
        if self.completion_future and not self.completion_future.done():
            self.completion_future.set_result((args, launch_env))


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--models-dir", type=str)
    args = parser.parse_args()

    models_dir = Path(args.models_dir) if args.models_dir else None
    server = BootstrapServer(models_dir)

    # Create a Future to signal completion
    loop = asyncio.get_running_loop()
    completion_future = loop.create_future()
    server.set_completion_future(completion_future)

    async with websockets.serve(server.handle_connection, "127.0.0.1", PORT):
        logger.info(f"BOOTSTRAP SERVER STARTED port={PORT}")
        # Wait for the bootstrap to finish and return the launch command
        launch_args, launch_env = await completion_future

    # Server is now closed, port should be free
    logger.info(f"Bootstrap server stopped. Launching Babelfish on port {PORT}...")

    os.chdir(BABELFISH_DIR)
    if sys.platform == "win32":
        # subprocess.call is blocking, so the script waits for Babelfish to exit
        subprocess.call(launch_args, env=launch_env)
        sys.exit(0)
    else:
        # On Linux/Unix, replace the current process
        os.execvpe(UV_CMD, launch_args, launch_env)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
