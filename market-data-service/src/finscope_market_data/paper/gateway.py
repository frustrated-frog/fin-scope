"""Job-scoped research tools. The worker exports only the pre-split development panel."""

from datetime import datetime
import gzip
import json
from pathlib import Path
import sys
from types import SimpleNamespace

from mcp.server.fastmcp import FastMCP

from . import tail_model
from .factor_library import bind
from .analysis import coverage, evaluate
from .return_targets import selected_targets


def development_tools(config):
    context = json.loads(config.read_text())
    with gzip.open(context["developmentFile"], "rt") as stream:
        rows = json.load(stream)
    panel = {
        code: [
            SimpleNamespace(**bar, observed_at=datetime.fromisoformat(bar["at"]))
            for bar in bars
        ]
        for code, bars in rows.items()
    }
    tools = FastMCP("FinScope 量化研究")
    runs = 0

    def audit(message):
        with config.with_name("tools.jsonl").open("a") as stream:
            stream.write("工具 · " + message + "\n")

    @tools.tool()
    def describe_dataset() -> dict:
        """Full development coverage per symbol, including complete sessions and known share volume."""
        audit("查询开发段覆盖")
        return coverage(panel)

    @tools.tool()
    def query_bars(
        symbol: str, start: str = "", end: str = "", offset: int = 0, limit: int = 48
    ) -> dict:
        """Page completed development bars by inclusive ISO date range. Offset indexes the filtered rows."""
        bars = [
            b
            for b in rows.get(symbol, [])
            if (not start or b["at"][:10] >= start) and (not end or b["at"][:10] <= end)
        ]
        offset = max(0, offset)
        page = bars[offset : offset + min(200, max(1, limit))]
        audit("按区间读取开发行情")
        return dict(
            total=len(bars),
            bars=[
                {**b, "volume": None if b["estimated_volume"] else b["volume"]}
                for b in page
            ],
            nextOffset=offset + len(page) if offset + len(page) < len(bars) else None,
        )

    @tools.tool()
    def experiment_memory(query: str = "") -> list:
        """Search prior hypotheses, candidate decisions and development results."""
        audit("检索研究记录")
        return [
            m
            for m in context["memory"]
            if query.casefold() in json.dumps(m, ensure_ascii=False).casefold()
        ][:12]

    library = context.get("factorLibrary", {})

    @tools.tool()
    def search_factors(query: str = "") -> list:
        """Find named reusable factor versions allowed at this development cutoff."""
        audit("查询独立因子库")
        return [
            {k: v[k] for k in ("id", "name", "version", "description")}
            for v in library.values()
            if query.casefold() in (v["name"] + " " + v["description"]).casefold()
        ][:40]

    @tools.tool()
    def read_factor(asset_id: str) -> dict:
        """Read the exact source and most recent eligible development evidence for a factor."""
        audit("读取冻结因子版本")
        v = library[asset_id]
        return dict(name=v["name"], source=v["source"], study=v["studies"][0])

    @tools.tool()
    def list_return_targets() -> dict:
        """Available factor evaluation target IDs and their signal, entry and exit definitions."""
        return selected_targets(None)

    def run(code, targets=None):
        nonlocal runs
        if runs >= 4 or len(code) > 32000:
            raise ValueError("每次编写最多四次试跑，源码最多 32000 字")
        runs += 1
        audit(f"第 {runs} 次全开发段试跑")
        try:
            source, _ = bind(code, library)
            return evaluate(
                panel, source, context["image"], context["capital"], targets=targets
            )
        except (ValueError, TypeError, KeyError, ArithmeticError, OSError) as exc:
            audit("试跑失败 · " + str(exc).splitlines()[-1][:220])
            raise

    @tools.tool()
    def run_development_backtest(code: str) -> dict:
        """Run decide(ctx) and factors(bars) over the ENTIRE development interval, including execution costs."""
        return run(code)

    @tools.tool()
    def evaluate_factor(code: str, targets: list[str] | None = None) -> dict:
        """Evaluate factors(bars) for the requested list_return_targets IDs, or all targets when omitted."""
        selected_targets(targets)
        return run(
            code
            + "\ndef decide(ctx):\n    return {'weights':{},'scores':{},'state':{}}",
            targets,
        )["factors"]

    @tools.tool()
    def train_tail_ridge(features: list[str] | None = None, alpha: float = 20.) -> dict:
        """Fit a frozen Ridge model on completed development-only overnight labels. Select 1-4 of return5m, return15m, return60m, return1d, trend20d, trend60d."""
        audit("训练开发段尾盘 Ridge 模型")
        observations = [r for code, bars in panel.items() for r in tail_model.samples(code, bars)]
        model = tail_model.train(observations, "9999-12-31",
                                 tail_model.DEFAULT_FEATURES if features is None else features, alpha)
        return dict(model=model, code=tail_model.source(model), equalWeightCode=tail_model.source(model, equal=True))

    return tools


if __name__ == "__main__":
    development_tools(Path(sys.argv[1])).run(transport="stdio")
