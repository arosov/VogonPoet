import subprocess
import time
import threading
import sys
import os
import signal
import shutil

# Configuration
SERVER_CWD = "../babelfish"
CLIENT_CWD = "."
SERVER_CMD = ["uv", "run", "babelfish"]
CLIENT_CMD = ["./gradlew", ":composeApp:run"]
TIMEOUT_SECONDS = 120
SERVER_READY_MSG = "WebTransport running on"
CLIENT_SUCCESS_MSG = "Received Babelfish Configuration"


def kill_process_on_port(port):
    """Finds and kills any process using the specified port."""
    try:
        # Use lsof to find PID
        result = subprocess.run(
            ["lsof", "-t", f"-i:{port}"], capture_output=True, text=True
        )
        pids = result.stdout.strip().split()
        for pid in pids:
            if pid:
                print(f"Killing lingering process {pid} on port {port}...")
                os.kill(int(pid), signal.SIGKILL)
    except Exception as e:
        print(f"Error cleaning up port {port}: {e}")


def kill_by_name(name):
    try:
        subprocess.run(["pkill", "-f", name], check=False)
    except Exception:
        pass


def stream_output(process, prefix, stop_event, found_success_event):
    """Reads output from a process and prints it with a prefix."""
    try:
        for line in iter(process.stdout.readline, ""):
            if stop_event.is_set():
                break
            if line:
                stripped_line = line.rstrip()
                print(f"[{prefix}] {stripped_line}")
                if prefix == "CLIENT" and CLIENT_SUCCESS_MSG in stripped_line:
                    found_success_event.set()
                if prefix == "SERVER" and SERVER_READY_MSG in stripped_line:
                    found_success_event.set()  # Reusing this event is messy, handled differently below
    except ValueError:
        pass  # Handle closed file


def main():
    print("Starting Test Script...")

    # Cleanup before starting
    print("Cleaning up lingering processes...")
    kill_process_on_port(8123)
    kill_by_name("babelfish")
    # Only kill java/gradle if absolutely necessary as it slows things down, but safe for now
    # kill_by_name("composeApp:run")
    time.sleep(1)  # Wait for OS to release resources

    # 1. Start Server
    print(f"Starting Server in {SERVER_CWD}...")
    server_process = subprocess.Popen(
        SERVER_CMD,
        cwd=SERVER_CWD,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        preexec_fn=os.setsid,  # Create new session group for cleaner cleanup
    )

    server_ready = False
    start_time = time.time()

    # We need to read server output to detect readiness
    # We can't block completely, but checking line by line is okay for the startup phase

    # Temporary reading loop for startup
    while time.time() - start_time < TIMEOUT_SECONDS:
        line = server_process.stdout.readline()
        if not line:
            break
        print(f"[SERVER] {line.strip()}")
        if SERVER_READY_MSG in line:
            server_ready = True
            break

    if not server_ready:
        print("Server failed to start or timed out.")
        try:
            os.killpg(os.getpgid(server_process.pid), signal.SIGTERM)
        except ProcessLookupError:
            pass
        sys.exit(1)

    print("Server Ready! Starting Client...")

    # 2. Start Client
    client_process = subprocess.Popen(
        CLIENT_CMD,
        cwd=CLIENT_CWD,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        preexec_fn=os.setsid,
    )

    stop_event = threading.Event()
    client_success_event = threading.Event()

    # Create threads to stream output from now on
    server_thread = threading.Thread(
        target=stream_output,
        args=(server_process, "SERVER", stop_event, threading.Event()),
    )  # Dummy event for server
    client_thread = threading.Thread(
        target=stream_output,
        args=(client_process, "CLIENT", stop_event, client_success_event),
    )

    server_thread.start()
    client_thread.start()

    # Wait for success or timeout
    # Remaining time
    elapsed = time.time() - start_time
    remaining = TIMEOUT_SECONDS - elapsed

    print(f"Waiting for configuration sync (Timeout: {remaining:.1f}s)...")

    if client_success_event.wait(timeout=remaining):
        print("Success detected. Waiting for full config logs...")
        time.sleep(3)

    stop_event.set()

    # Cleanup
    print("Stopping processes...")
    try:
        if server_process.poll() is None:
            os.killpg(os.getpgid(server_process.pid), signal.SIGTERM)
        if client_process.poll() is None:
            os.killpg(os.getpgid(client_process.pid), signal.SIGTERM)
    except ProcessLookupError:
        pass

    if client_success_event.is_set():
        print("\nSUCCESS: Client received configuration!")
        sys.exit(0)
    else:
        print("\nFAILURE: Timeout reached without client receiving configuration.")
        sys.exit(1)


if __name__ == "__main__":
    main()
