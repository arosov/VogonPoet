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

# --- Windows DXGI Structures for in-process GPU detection ---
if sys.platform == "win32":

    class GUID(ctypes.Structure):
        _fields_ = [
            ("Data1", ctypes.c_uint32),
            ("Data2", ctypes.c_uint16),
            ("Data3", ctypes.c_uint16),
            ("Data4", ctypes.c_uint8 * 8),
        ]

        def __init__(self, guid_str):
            import uuid

            g = uuid.UUID(guid_str)
            self.Data1 = g.time_low
            self.Data2 = g.time_mid
            self.Data3 = g.time_hi_version
            self.Data4 = (ctypes.c_uint8 * 8)(*g.bytes[8:])

    class DXGI_ADAPTER_DESC1(ctypes.Structure):
        _fields_ = [
            ("Description", ctypes.c_wchar * 128),
            ("VendorId", ctypes.c_uint32),
            ("DeviceId", ctypes.c_uint32),
            ("SubSysId", ctypes.c_uint32),
            ("Revision", ctypes.c_uint32),
            ("DedicatedVideoMemory", ctypes.c_size_t),
            ("DedicatedSystemMemory", ctypes.c_size_t),
            ("SharedSystemMemory", ctypes.c_size_t),
            ("AdapterLuidLow", ctypes.c_uint32),
            ("AdapterLuidHigh", ctypes.c_int32),
            ("Flags", ctypes.c_uint32),
        ]


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
        """Retrieves a list of all detected GPU names using in-process DXGI or WMI fallback."""
        gpus = []
        try:
            if sys.platform == "win32":
                # 1. Prefer DXGI for consistency with the backend
                try:
                    dxgi = ctypes.windll.dxgi
                    factory_iid = GUID("{7b7166ec-21c7-44ae-b21a-c9ae321ae369}")
                    p_factory = ctypes.c_void_p()
                    if (
                        dxgi.CreateDXGIFactory1(
                            ctypes.byref(factory_iid), ctypes.byref(p_factory)
                        )
                        == 0
                    ):

                        def get_func(obj_ptr, index, argtypes):
                            vtable = ctypes.cast(
                                obj_ptr, ctypes.POINTER(ctypes.c_void_p)
                            )[0]
                            func_ptr = ctypes.cast(
                                vtable, ctypes.POINTER(ctypes.c_void_p)
                            )[index]
                            return ctypes.WINFUNCTYPE(
                                ctypes.c_long, ctypes.c_void_p, *argtypes
                            )(func_ptr)

                        for i in range(16):
                            p_adapter = ctypes.c_void_p()
                            # EnumAdapters1 index 12
                            if (
                                get_func(
                                    p_factory, 12, [ctypes.c_uint32, ctypes.c_void_p]
                                )(p_factory, i, ctypes.byref(p_adapter))
                                != 0
                            ):
                                break

                            desc = DXGI_ADAPTER_DESC1()
                            # GetDesc1 index 10
                            if (
                                get_func(p_adapter, 10, [ctypes.c_void_p])(
                                    p_adapter, ctypes.byref(desc)
                                )
                                == 0
                            ):
                                # Filter out software renderers (Microsoft Basic Render Driver)
                                # DXGI_ADAPTER_FLAG_SOFTWARE = 2
                                if not (desc.Flags & 2):
                                    name = desc.Description.strip()
                                    if name:
                                        gpus.append(name)

                            get_func(p_adapter, 2, [])(p_adapter)  # Release
                        get_func(p_factory, 2, [])(p_factory)  # Release
                except Exception:
                    pass

                if gpus:
                    return list(dict.fromkeys(gpus))

                # Fallback to powershell
                out = (
                    subprocess.check_output(
                        [
                            "powershell",
                            "-Command",
                            "Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name",
                        ],
                        stderr=subprocess.DEVNULL,
                    )
                    .decode()
                    .strip()
                    .split("\n")
                )
                for line in out:
                    name = line.strip()
                    if name:
                        gpus.append(name)
            elif sys.platform == "linux":
                if shutil.which("lspci"):
                    # Force English output for lspci
                    env = os.environ.copy()
                    env["LC_ALL"] = "C"
                    out = subprocess.check_output(["lspci"], env=env).decode().lower()
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

        # Check for user preference in config (specifically for DML vs CUDA on Windows)
        config_device = "auto"
        app_data_dir = os.environ.get("VOGON_APP_DATA_DIR")
        if app_data_dir:
            config_path = Path(app_data_dir) / "babelfish.config.json"
            if config_path.exists():
                try:
                    with open(config_path, "r") as f:
                        data = json.load(f)
                        config_device = data.get("hardware", {}).get("device", "auto")
                except Exception:
                    pass

        # Hardware-based Auto-detection
        # We always prefer the best available GPU environment for the hardware,
        # even if the user currently requested CPU mode in the config.
        # This avoids re-syncing environments when switching between CPU/GPU.
        is_nvidia = "NVIDIA" in detected_caps or any(
            "nvidia" in g.lower() for g in all_gpu_names
        )
        is_amd = "AMD (ROCm)" in detected_caps or any(
            "amd" in g.lower() or "ati" in g.lower() for g in all_gpu_names
        )

        if is_nvidia:
            if sys.platform == "win32":
                # On Windows, NVIDIA can run either CUDA or DirectML.
                # DirectML requires a different onnxruntime package.
                # If the user explicitly requested DML, we must use the windows_gpu environment.
                if config_device.startswith("dml"):
                    return {
                        "hw_mode": "windows_gpu",
                        "extra": "windows-gpu",
                        "desc": "NVIDIA GPU (DirectML mode)",
                    }
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
            if sys.platform == "win32":
                return {
                    "hw_mode": "windows_gpu",
                    "extra": "windows-gpu",
                    "desc": "AMD GPU (DirectML)",
                }
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
        # Use cache directory for runtime artifacts
        self.cache_dir = Path(
            os.environ.get("VOGON_APP_CACHE_DIR", str(babelfish_dir / ".cache"))
        )
        self.cache_dir.mkdir(parents=True, exist_ok=True)

        self.marker_file = self.cache_dir / ".last_hw_mode"
        self.d3d12info_dir = self.cache_dir / "d3d12info"

        # One-time cleanup of legacy d3d12info binary artifacts
        if self.d3d12info_dir.exists():
            try:
                shutil.rmtree(self.d3d12info_dir)
            except Exception:
                pass

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
        # Clear VIRTUAL_ENV so uv uses the project .venv instead of bootstrap env
        env.pop("VIRTUAL_ENV", None)
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
        args = [UV_CMD, "run", "--no-sync", "babelfish", "--port", str(PORT)]

        # Check if we should force CPU mode at RUNTIME (not SYNC time)
        force_cpu = str(os.environ.get("VOGON_FORCE_CPU", "")).lower() in (
            "1",
            "true",
            "yes",
            "on",
        )
        if not force_cpu:
            app_data_dir = os.environ.get("VOGON_APP_DATA_DIR")
            if app_data_dir:
                config_path = Path(app_data_dir) / "babelfish.config.json"
                if config_path.exists():
                    try:
                        with open(config_path, "r") as f:
                            data = json.load(f)
                            if data.get("hardware", {}).get("device") == "cpu":
                                force_cpu = True
                    except Exception:
                        pass

        if force_cpu or hw_mode == "cpu":
            args.append("--cpu")

        # Signal completion to the main loop
        if self.completion_future and not self.completion_future.done():
            self.completion_future.set_result((args, launch_env))


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--models-dir", type=str)
    args = parser.parse_args()

    # Write PID file for robust cleanup
    app_data_dir = os.environ.get("VOGON_APP_DATA_DIR")
    if app_data_dir:
        pid_file = Path(app_data_dir) / "babelfish.pid"
        try:
            pid_file.write_text(str(os.getpid()))
        except Exception as e:
            logger.warning(f"Failed to write PID file: {e}")

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
