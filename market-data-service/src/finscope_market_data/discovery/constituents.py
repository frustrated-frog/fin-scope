from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from html import unescape
import json
from pathlib import Path
import re
from typing import Callable
from urllib.request import Request, urlopen

from finscope_market_data.discovery.schemas import DiscoverySector


ConstituentValue = tuple[str, str, str]
PageLoader = Callable[[str], tuple[str, str]]


@dataclass(frozen=True)
class ConstituentBatch:
    sector_code: str
    sector_name: str
    source_family: str
    values: tuple[ConstituentValue, ...]
    expected_count: int
    retrieved_count: int
    quality_status: str
    coverage: float
    retrieved_at: str
    warning: str = ""


class TonghuashunConstituentProvider:
    source_family = "TONGHUASHUN"

    def __init__(
        self,
        page_loader: PageLoader | None = None,
        timeout_seconds: float = 15.0,
        max_pages: int = 30,
    ) -> None:
        self.page_loader = page_loader or self._load_page
        self.timeout_seconds = timeout_seconds
        self.max_pages = max(1, max_pages)

    def constituents(self, sector: DiscoverySector) -> ConstituentBatch:
        base = f"https://q.10jqka.com.cn/thshy/detail/code/{sector.code}/"
        values: dict[str, ConstituentValue] = {}
        total_pages = 1
        warning = ""
        for page in range(1, self.max_pages + 1):
            if page > total_pages:
                break
            url = base if page == 1 else f"{base}page/{page}/"
            html, final_url = self.page_loader(url)
            if "/account/login" in final_url:
                warning = "同花顺公开成分页触发登录限制，仅取得部分成分"
                break
            if page == 1:
                total_pages = min(self.max_pages, _page_count(html))
            page_values = _parse_values(html)
            if not page_values:
                warning = "同花顺成分页为空，仅取得部分成分"
                break
            for value in page_values:
                values[value[0]] = value
        ordered = tuple(values.values())
        expected = max(0, sector.expected_constituent_count)
        coverage = min(1.0, len(ordered) / expected) if expected else 0.0
        complete = bool(expected) and len(ordered) >= expected
        if not complete and not warning:
            warning = f"同花顺成分覆盖不足：{len(ordered)}/{expected or '未知'}"
        return ConstituentBatch(
            sector_code=sector.code,
            sector_name=sector.name,
            source_family=self.source_family,
            values=ordered,
            expected_count=expected,
            retrieved_count=len(ordered),
            quality_status="COMPLETE" if complete else "PARTIAL",
            coverage=coverage,
            retrieved_at=datetime.now().isoformat(),
            warning=warning,
        )

    def _load_page(self, url: str) -> tuple[str, str]:
        request = Request(
            url,
            headers={
                "User-Agent": (
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    "AppleWebKit/537.36 Chrome/139.0 Safari/537.36"
                )
            },
        )
        with urlopen(request, timeout=self.timeout_seconds) as response:
            return response.read().decode("gbk", errors="replace"), response.geturl()


class ConstituentSnapshotStore:
    def __init__(
        self,
        path: str | Path,
        ttl_days: int = 30,
        now: Callable[[], datetime] | None = None,
    ) -> None:
        self.path = Path(path)
        self.ttl_days = max(1, ttl_days)
        self.now = now or datetime.now

    def save(self, sector: DiscoverySector, batch: ConstituentBatch) -> None:
        if batch.quality_status != "COMPLETE":
            return
        payload = self._read() or {"version": 1, "sectors": {}}
        payload.setdefault("sectors", {})[sector.code] = {
            "sector_code": batch.sector_code,
            "sector_name": batch.sector_name,
            "source_family": batch.source_family,
            "values": [list(item) for item in batch.values],
            "expected_count": batch.expected_count,
            "retrieved_count": batch.retrieved_count,
            "quality_status": batch.quality_status,
            "coverage": batch.coverage,
            "retrieved_at": batch.retrieved_at,
            "warning": batch.warning,
            "snapshot_at": self.now().isoformat(),
        }
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix(".tmp")
        temporary.write_text(
            json.dumps(payload, ensure_ascii=False, sort_keys=True), encoding="utf-8"
        )
        temporary.replace(self.path)

    def load(self, sector: DiscoverySector) -> ConstituentBatch | None:
        payload = self._read()
        if not payload:
            return None
        try:
            item = payload["sectors"][sector.code]
            snapshot_at = datetime.fromisoformat(str(item["snapshot_at"]))
            if (self.now() - snapshot_at).total_seconds() > self.ttl_days * 86400:
                return None
            expected = int(item["expected_count"])
            if sector.expected_constituent_count and expected < sector.expected_constituent_count:
                return None
            return ConstituentBatch(
                sector_code=str(item["sector_code"]),
                sector_name=str(item["sector_name"]),
                source_family=str(item["source_family"]),
                values=tuple(tuple(value) for value in item["values"]),
                expected_count=expected,
                retrieved_count=int(item["retrieved_count"]),
                quality_status=str(item["quality_status"]),
                coverage=float(item["coverage"]),
                retrieved_at=str(item["retrieved_at"]),
                warning=str(item.get("warning", "")),
            )
        except (KeyError, TypeError, ValueError):
            return None

    def _read(self) -> dict[str, object] | None:
        if not self.path.exists():
            return None
        try:
            value = json.loads(self.path.read_text(encoding="utf-8"))
            return value if isinstance(value, dict) else None
        except (OSError, json.JSONDecodeError):
            return None


def _page_count(html: str) -> int:
    match = re.search(
        r"class=['\"]page_info['\"][^>]*>\s*\d+\s*/\s*(\d+)", html, re.I
    )
    return max(1, int(match.group(1))) if match else 1


def _parse_values(html: str) -> list[ConstituentValue]:
    result: list[ConstituentValue] = []
    for row in re.findall(r"<tr\b[^>]*>(.*?)</tr>", html, re.I | re.S):
        links = re.findall(
            r"<a\b[^>]*href=['\"][^'\"]*stockpage\.10jqka\.com\.cn/"
            r"(\d{6})/?['\"][^>]*>(.*?)</a>",
            row,
            re.I | re.S,
        )
        if len(links) < 2:
            continue
        code = links[0][0]
        name = unescape(re.sub(r"<[^>]+>", "", links[1][1])).strip()
        if name:
            result.append((code, _source_market(code), name))
    return result


def _source_market(code: str) -> str:
    if code.startswith(("4", "8", "92")):
        return "BJ"
    if code.startswith(("5", "6", "9")):
        return "SH"
    return "SZ"

