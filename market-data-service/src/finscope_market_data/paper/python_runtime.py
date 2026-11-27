"""FinScope Python strategy SDK. Only the Docker guest imports generated code."""

import json
import math
import os
import selectors
import shutil
import subprocess
import time
import uuid

from .timeframes import Timeframes
from .engine import COMMISSION_RATE, MIN_COMMISSION, SELL_TAX, SLIPPAGE, position_details

IMAGE = "python:3.12-slim"
SDK = """Implement decide(ctx) using Python 3.12 standard library. At each completed five-minute bar,
ctx contains at (Shanghai ISO time), equity, cash, positions (code -> shares), state (your prior JSON object), positionDetails (each held code: shares, sellable, frozen, averageCost, oldestLotDate, holdingCalendarDays), session (MORNING or AFTERNOON),
costs (commissionRate, minimumCommissionCny, sellTaxRate, slippageRate). Respect sellable before targeting exits;

bars (available code -> chronological list of up to 49 dicts with open,high,low,close,amount,volume,at).
Access bar["multiTimeframe"] for completed return5m, return15m, return60m, return1d and 20/60-session trend20d/trend60d. Missing features mean insufficient complete trading intervals. Daily values update at 15:00.
Volume can be null for archived data and amount can be null for recent quotes. Be robust to short windows and missing symbols. Use deterministic
logic and only ctx. Return a dict weights (available code -> absolute desired portfolio weight, 0 exits,
omitted holds, nonnegative sum <= 1), scores (finite numeric scores for available codes including all
weight keys), state (small JSON dict). Execution, fees and T+1 belong to the simulator.
Module globals do not persist. Write a complete runnable strategy with your own named factor function,
position sizing, entry/exit and rebalance timing. For new research implement factors(bars):
return 1-4 named independent numeric factors, {factor_name:{available_code:finite_number}}.
Use these same factor functions in decide(ctx); expose each raw factor before mixing/ranking.
With short windows return the named factor mapped to {}. No portfolio inputs to factors(). Explain the hypothesis and factor in Chinese."""

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
            for c,rows in history.items():
                last_ex=next((i for i in range(len(rows)-1,-1,-1) if rows[i].get('exDate')),None)
                if last_ex is not None:
                    history[c]=rows[last_ex:]
            ctx['bars']={c:[dict(b) for b in rows] for c,rows in history.items()}
            signals={}
            report=ctx.pop('_factorReport',False)
            result=ns['decide'](ctx)
            if report:
                factor_ns={}
                exec(source,factor_ns)
                signals=factor_ns['factors']({c:[dict(b) for b in rows] for c,rows in history.items()})
        emit({'result':result,'factors':signals})
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
    def __init__(self, program, image, *, collect_factors=False):
        self.collect_factors = collect_factors
        self.factor_values = {}
        self.factor_names = None
        self.program = program
        self.image = image
        self.name = "finscope-strategy-" + uuid.uuid4().hex
        self.process = None
        self.last = {}
        self.features = {} if "multiTimeframe" in program else None

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

    def bar_features(self, code, bar):
        if self.features is None:
            return {}
        if hasattr(bar, "multiTimeframe"):
            return {"multiTimeframe": bar.multiTimeframe}
        if code not in self.features:
            self.features[code] = (Timeframes(), {})
        state, recent = self.features[code]
        at = bar.observed_at
        if at not in recent:
            recent[at] = state.update(at, bar)
            if len(recent) > 49:
                del recent[next(iter(recent))]
        return {"multiTimeframe": recent[at]}

    def decide(self, histories, book, end, equity):
        bars = {
            c: [
                dict(
                    at=b.observed_at.isoformat(),
                    **self.bar_features(c, b),
                    open=b.open,
                    high=b.high,
                    low=b.low,
                    close=b.close,
                    amount=b.amount,
                    **({"exDate": True} if getattr(b, "actions", []) and not getattr(b, "windowClipped", False) else {}),
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
                _factorReport=self.collect_factors,
                at=end.isoformat(),
                bars=bars,
                session="MORNING" if end.hour < 12 else "AFTERNOON",
                positionDetails=position_details(book, end),
                costs=dict(commissionRate=COMMISSION_RATE, minimumCommissionCny=MIN_COMMISSION,
                           sellTaxRate=SELL_TAX, slippageRate=SLIPPAGE),
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
        if self.collect_factors:
            values = reply.get("factors")
            if not isinstance(values, dict) or not 1 <= len(values) <= 4:
                raise ValueError("factors(bars) 须返回一至四个命名因子")
            for name, scores in values.items():
                if (
                    not name.strip()
                    or len(name) > 80
                    or not isinstance(scores, dict)
                    or not scores.keys() <= histories.keys()
                ):
                    raise ValueError("因子名称或标的无效")
                if not all(
                    type(v) in (int, float) and abs(v) <= 1e100 and math.isfinite(v)
                    for v in scores.values()
                ):
                    raise ValueError("独立因子须返回有限数字，缺失标的可省略")
            if self.factor_names is not None and self.factor_names != values.keys():
                raise ValueError("同一版本的因子名称须保持一致")
            self.factor_names = set(values)
            self.factor_values = values
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
