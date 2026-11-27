#!/usr/bin/env python3
"""Install the existing local market service as a per-user macOS LaunchAgent."""

import os
from pathlib import Path
import plistlib
import shutil
import signal
import subprocess
import sys
import time
import urllib.request

LABEL = "com.finscope.paper-market"
ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "market-data-service"
COMMAND = [str(WORK / ".venv/bin/python"), "-m", "uvicorn", "finscope_market_data.app:app",
           "--host", "127.0.0.1", "--port", "8000", "--app-dir", "src"]


def listener():
    output = subprocess.run(["lsof", "-tiTCP:8000", "-sTCP:LISTEN"], capture_output=True, text=True)
    return int(output.stdout.strip()) if output.stdout.strip() else None


def ready():
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for _ in range(40):
        try:
            with opener.open("http://127.0.0.1:8000/ready", timeout=2) as response:
                if response.status == 200:
                    return
        except OSError:
            time.sleep(.5)
    raise RuntimeError("本机行情服务健康检查超时")


def install():
    if sys.platform != "darwin" or not Path(COMMAND[0]).is_file():
        raise SystemExit("需要 macOS 及项目的行情服务虚拟环境")
    domain = f"gui/{os.getuid()}"
    destination = Path.home() / "Library/LaunchAgents" / (LABEL + ".plist")
    logs = Path.home() / "Library/Logs/FinScope"
    logs.mkdir(parents=True, exist_ok=True)
    paths = [str(Path(found).parent) for name in ("docker", "codex") if (found := shutil.which(name))]
    paths += ["/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/bin", "/usr/sbin", "/sbin"]
    config = dict(Label=LABEL, ProgramArguments=COMMAND, WorkingDirectory=str(WORK), KeepAlive=True,
                  RunAtLoad=True, ThrottleInterval=10, StandardOutPath=str(logs / "market.out.log"),
                  StandardErrorPath=str(logs / "market.err.log"),
                  EnvironmentVariables=dict(PATH=":".join(dict.fromkeys(paths)), FINSCOPE_PAPER_LOCAL_RUNNER="1"))
    loaded = subprocess.run(["launchctl", "print", f"{domain}/{LABEL}"], capture_output=True).returncode == 0
    if destination.exists() and plistlib.loads(destination.read_bytes()) != config:
        raise SystemExit(f"已存在不同的启动配置，请先核对 {destination}")
    if loaded:
        ready()
        return
    pid = listener()
    if pid:
        command = subprocess.check_output(["ps", "-p", str(pid), "-o", "command="], text=True).strip()
        if command != " ".join(COMMAND):
            raise SystemExit("端口 8000 由其他服务占用")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(plistlib.dumps(config))
    destination.chmod(0o600)
    if pid:
        os.kill(pid, signal.SIGTERM)
        for _ in range(100):
            if listener() is None:
                break
            time.sleep(.25)
        else:
            raise SystemExit("旧行情服务未释放端口")
    try:
        subprocess.run(["launchctl", "bootstrap", domain, str(destination)], check=True)
        ready()
    except Exception:
        subprocess.run(["launchctl", "bootout", f"{domain}/{LABEL}"], capture_output=True)
        if pid and not listener():
            with (logs / "market.err.log").open("ab") as out:
                subprocess.Popen(COMMAND, cwd=WORK, stdin=subprocess.DEVNULL, stdout=out,
                                 stderr=out, start_new_session=True)
        raise


if __name__ == "__main__":
    install()
    print("本机行情服务已托管，健康检查通过")
