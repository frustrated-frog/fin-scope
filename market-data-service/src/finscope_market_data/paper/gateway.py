"""Job-scoped MCP tools. Only the development sample is shared with Codex."""

from contextlib import contextmanager
from datetime import datetime, timedelta
import json
from pathlib import Path
import sys
import time
from statistics import correlation, fmean, StatisticsError
from types import SimpleNamespace

from mcp.server.fastmcp import FastMCP

from .engine import advance, new_book
from .python_runtime import PythonStrategy
from .research import frames


def serve(path):
    context = json.loads(path.read_text())
    panels = context["sample"]
    tools = FastMCP("FinScope 量化研究")
    runs = 0

    def audit(message):
        with path.with_name("tools.jsonl").open("a") as stream:
            stream.write(message + "\n")

    @contextmanager
    def capture_diagnostic():
        try:
            yield
        except (ValueError, TypeError, KeyError, ArithmeticError, OSError) as error:
            lines = str(error).strip().splitlines()
            audit(
                "工具 · 候选试跑失败 · "
                + (lines[-1] if lines else type(error).__name__)[:240]
            )
            raise

    @tools.tool()
    def market_sample(symbol: str, count: int = 24) -> list:
        """Read the latest 1–100 completed five-minute bars from the development sample."""
        audit("工具 · 查阅开发段五分钟行情")
        return [
            {**row, "volume": None if row["estimated_volume"] else row["volume"]}
            for row in panels.get(symbol, [])[-min(100, max(1, count)) :]
        ]

    @tools.tool()
    def experiment_memory(query: str = "") -> list:
        """Search previous strategy hypotheses, factor descriptions and observed development results."""
        audit("工具 · 检索历史实验")
        return [
            r
            for r in context["memory"]
            if query.lower() in json.dumps(r, ensure_ascii=False).lower()
        ][:12]

    @tools.tool()
    def run_strategy(code: str) -> dict:
        """Run a complete decide(ctx) strategy on the bounded development sample, with real sandbox diagnostics."""
        nonlocal runs
        if len(code) > 32000 or runs >= 4:
            raise ValueError("每次编写可试跑四个候选，每份代码最多 32000 字")
        runs += 1
        audit(f"工具 · 第 {runs} 个候选进入开发段试跑")
        deadline = time.monotonic() + 55
        previous = {}
        bar_correlations = []
        book = new_book(context["capital"])
        panel = {
            c: [
                SimpleNamespace(**{**b, "observed_at": datetime.fromisoformat(b["at"])})
                for b in rows
            ]
            for c, rows in panels.items()
            if rows
        }
        with capture_diagnostic(), PythonStrategy(code, context["image"]) as strategy:
            for end, history in frames(panel):
                if time.monotonic() > deadline:
                    raise ValueError("开发样本试跑超时")
                # Next-bar returns evaluate only scores already made on the previous bar.
                pairs = [
                    (score, history[c][-1].close / price - 1)
                    for c, (at, price, score) in previous.items()
                    if c in history and end - at == timedelta(minutes=5)
                ]
                if len(pairs) >= 3:
                    try:
                        bar_correlations.append(correlation(*zip(*pairs)))
                    except StatisticsError:
                        pass
                point = advance(
                    book,
                    None,
                    history,
                    end,
                    end + timedelta(seconds=3),
                    context["capital"],
                    universe=list(panel),
                    decide=strategy.decide,
                )
                previous = {
                    c: (end, history[c][-1].close, s)
                    for c, s in point["scores"].items()
                    if s is not None
                }
        audit(f"工具 · 开发样本试跑完成，成交 {book['fills']} 笔")
        return dict(
            meanNextBarFactorIC=fmean(bar_correlations) if bar_correlations else None,
            factorObservations=len(bar_correlations),
            bars=sum(map(len, panel.values())),
            fills=book["fills"],
            fees=book["fees"],
            netReturn=book["equity"] / context["capital"] - 1,
        )

    tools.run(transport="stdio")


if __name__ == "__main__":
    serve(Path(sys.argv[1]))
