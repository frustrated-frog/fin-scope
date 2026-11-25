"""FinScope Python strategy SDK. Only the Docker guest imports generated code."""

import json
import math
import os
import selectors
import shutil
import subprocess
import time
import uuid

IMAGE = "python:3.12-slim"
SDK = """Implement decide(ctx) using Python 3.12 standard library. At each completed five-minute bar,
ctx contains at (Shanghai ISO time), equity, cash, positions (code -> shares), state (your prior JSON object),
bars (available code -> chronological list of up to 49 dicts with open,high,low,close,amount,volume,at).
Volume can be null for archived data and amount can be null for recent quotes. Be robust to short windows and missing symbols. Use deterministic
logic and only ctx. Return a dict weights (available code -> absolute desired portfolio weight, 0 exits,
omitted holds, nonnegative sum <= 1), scores (finite numeric scores for available codes including all
weight keys), state (small JSON dict). Execution, fees and T+1 belong to the simulator.
Module globals do not persist. Write a complete runnable strategy with your own named factor function,
position sizing, entry/exit and rebalance timing. Explain the hypothesis and factor in Chinese."""

GUEST = """
import sys,json,contextlib,traceback
send=sys.stdout
def emit(message):
    print(json.dumps(message,allow_nan=False,separators=(',',':')),file=send,flush=True)
try:
    source=compile(json.loads(input()),'strategy.py','exec')
    compile_error=None
except SyntaxError:
    compile_error=traceback.format_exc(limit=2)[-1600:]
history={}
for line in sys.stdin:
    try:
        if compile_error:
            emit({"error":compile_error})
            continue
        with contextlib.redirect_stdout(sys.stderr):
            ns={}
            exec(source,ns)
            ctx=json.loads(line)
            history={c:(history.get(c,[])+rows)[-49:] for c,rows in ctx['bars'].items()}
            ctx['bars']={c:[dict(b) for b in rows] for c,rows in history.items()}
            result=ns['decide'](ctx)
        emit({'result':result})
    except Exception:
        emit({'error':traceback.format_exc(limit=4)[-1600:]})
"""


def image_id():
    docker = shutil.which("docker")
    if not docker:
        raise ValueError("未找到 Docker CLI")
    result = subprocess.run(
        [docker, "image", "inspect", "--format", "{{.Id}}", IMAGE],
        capture_output=True,
        text=True,
        timeout=10,
    )
    if result.returncode:
        raise ValueError("请启动 Docker 并准备 python:3.12-slim 镜像")
    return result.stdout.strip()


class PythonStrategy:
    def __init__(self, program, image):
        self.program = program
        self.image = image
        self.name = "finscope-strategy-" + uuid.uuid4().hex
        self.process = None
        self.last = {}

    def __enter__(self):
        cmd = [
            "docker",
            "run",
            "--rm",
            "--pull",
            "never",
            "-i",
            "--name",
            self.name,
            "--network",
            "none",
            "--read-only",
            "--cap-drop",
            "ALL",
            "--security-opt",
            "no-new-privileges",
            "--user",
            "65534:65534",
            "--cpus",
            "1",
            "--memory",
            "160m",
            "--memory-swap",
            "160m",
            "--pids-limit",
            "12",
            "--log-driver",
            "none",
            self.image,
            "python",
            "-I",
            "-B",
            "-u",
            "-c",
            GUEST,
        ]
        self.process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            bufsize=0,
        )
        os.set_blocking(self.process.stdin.fileno(), False)
        os.set_blocking(self.process.stdout.fileno(), False)
        try:
            self.send(self.program)
        except BaseException:
            self.__exit__(None, None, None)
            raise
        return self

    def __exit__(self, *_):
        self.process.stdin.close()
        try:
            self.process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait()
        finally:
            self.process.stdout.close()
            subprocess.run(
                ["docker", "rm", "-f", self.name],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=10,
            )

    def send(self, obj):
        data = memoryview(
            (json.dumps(obj, allow_nan=False, separators=(",", ":")) + "\n").encode()
        )
        deadline = time.monotonic() + 10
        with selectors.DefaultSelector() as selector:
            selector.register(self.process.stdin, selectors.EVENT_WRITE)
            while data:
                if (
                    not selector.select(max(0, deadline - time.monotonic()))
                    or time.monotonic() > deadline
                ):
                    raise ValueError("策略输入超时")
                data = data[os.write(self.process.stdin.fileno(), data[:4096]) :]

    def decide(self, histories, book, end, equity):
        bars = {
            c: [
                dict(
                    at=b.observed_at.isoformat(),
                    open=b.open,
                    high=b.high,
                    low=b.low,
                    close=b.close,
                    amount=b.amount,
                    volume=None if getattr(b, "estimated_volume", False) else b.volume,
                )
                for b in series
                if c not in self.last or b.observed_at > self.last[c]
            ]
            for c, series in histories.items()
        }
        self.last = {c: series[-1].observed_at for c, series in histories.items()}
        self.send(
            dict(
                at=end.isoformat(),
                bars=bars,
                equity=equity,
                cash=book["cash"],
                positions={
                    c: sum(q for _, q in p["lots"])
                    for c, p in book["positions"].items()
                },
                state=book.get("strategyState", {}),
            )
        )
        deadline = time.monotonic() + 10
        raw = bytearray()
        with selectors.DefaultSelector() as selector:
            selector.register(self.process.stdout, selectors.EVENT_READ)
            while not raw.endswith(b"\n"):
                if time.monotonic() > deadline or not selector.select(
                    max(0, deadline - time.monotonic())
                ):
                    raise ValueError("策略计算超时")
                chunk = os.read(self.process.stdout.fileno(), 8192)
                if not chunk or len(raw) + len(chunk) > 65536:
                    raise ValueError("策略退出或输出过大")
                raw.extend(chunk)
        reply = json.loads(raw)
        if "error" in reply:
            raise ValueError(str(reply["error"])[:1600])
        result = reply["result"]
        if not isinstance(result, dict) or not {"weights", "scores"}.issubset(result):
            raise ValueError("decide(ctx) 须返回含 weights 与 scores 的对象")
        weights, scores, state = (
            result["weights"],
            result["scores"],
            result.get("state", {}),
        )
        for values in [weights, scores]:
            if not isinstance(values, dict) or not values.keys() <= histories.keys():
                raise ValueError("weights 和 scores 只能包含当前可用标的")
            if not all(
                type(x) in (float, int) and abs(x) <= 1e100 and math.isfinite(x)
                for x in values.values()
            ):
                raise ValueError("权重与得分必须是有限数字")
        if (
            not weights.keys() <= scores.keys()
            or any(w < 0 for w in weights.values())
            or sum(weights.values()) > 1.00000001
        ):
            raise ValueError("目标仓位缺少得分、做空或总权重超过100%")
        if (
            not isinstance(state, dict)
            or len(json.dumps(state, allow_nan=False)) > 8192
        ):
            raise ValueError("state 必须为不超过 8KB 的 JSON 对象")
        book["strategyState"] = state
        return weights, scores
