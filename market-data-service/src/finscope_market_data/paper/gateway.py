"""Job-scoped MCP tools. Only the development sample is shared with Codex."""

from datetime import datetime, timedelta
import json
from pathlib import Path
import sys
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

    @tools.tool()
    def market_sample(symbol: str, count: int = 24) -> list:
        """Read the latest 1–100 completed five-minute bars from the development sample."""
        return panels.get(symbol, [])[-min(100, max(1, count)) :]

    @tools.tool()
    def experiment_memory(query: str = "") -> list:
        """Search previous strategy hypotheses, factor descriptions and observed development results."""
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
        book = new_book(context["capital"])
        panel = {
            c: [
                SimpleNamespace(**{**b, "observed_at": datetime.fromisoformat(b["at"])})
                for b in rows
            ]
            for c, rows in panels.items()
            if rows
        }
        with PythonStrategy(code, context["image"]) as strategy:
            for end, history in frames(panel):
                advance(
                    book,
                    None,
                    history,
                    end,
                    end + timedelta(seconds=3),
                    context["capital"],
                    universe=list(panel),
                    decide=strategy.decide,
                )
        return dict(
            bars=sum(map(len, panel.values())),
            fills=book["fills"],
            fees=book["fees"],
            netReturn=book["equity"] / context["capital"] - 1,
        )

    tools.run(transport="stdio")


if __name__ == "__main__":
    serve(Path(sys.argv[1]))
