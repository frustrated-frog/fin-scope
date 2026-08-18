from __future__ import annotations

from datetime import datetime, timedelta
import json

from finscope_market_data.discovery.acquisition import (
    AcquisitionMode,
    TonghuashunPageAcquirer,
)
from finscope_market_data.discovery.constituents import (
    ConstituentSnapshotStore,
    TonghuashunConstituentProvider,
)
from finscope_market_data.discovery.schemas import DiscoverySector


def _sector(expected: int = 3) -> DiscoverySector:
    return DiscoverySector(
        code="881121",
        name="半导体",
        category="INDUSTRY",
        source_code="AKSHARE_TONGHUASHUN_SECTOR_FLOW",
        source_family="TONGHUASHUN",
        source_rank=1,
        expected_constituent_count=expected,
        retrieved_at="2026-08-17T15:30:00",
    )


def _page(values: list[tuple[str, str]], current: int, total: int) -> str:
    rows = "".join(
        "<tr><td><a href='http://stockpage.10jqka.com.cn/"
        f"{code}/'>{code}</a></td><td><a href='http://stockpage.10jqka.com.cn/"
        f"{code}'>{name}</a></td></tr>"
        for code, name in values
    )
    return (
        f"<html><table><tbody>{rows}</tbody></table>"
        f"<span class='page_info'>{current}/{total}</span></html>"
    )


def test_tonghuashun_constituents_are_complete_only_at_expected_count() -> None:
    html = _page(
        [("600584", "长电科技"), ("002156", "通富微电"), ("300672", "国科微")],
        1,
        1,
    )
    provider = TonghuashunConstituentProvider(
        page_loader=lambda url: (html, url)
    )

    result = provider.constituents(_sector())

    assert result.quality_status == "COMPLETE"
    assert result.source_family == "TONGHUASHUN"
    assert result.coverage == 1.0
    assert result.values == (
        ("600584", "SH", "长电科技"),
        ("002156", "SZ", "通富微电"),
        ("300672", "SZ", "国科微"),
    )


def test_tonghuashun_login_redirect_keeps_first_hundred_partial() -> None:
    pages: dict[str, tuple[str, str]] = {}
    for page in range(1, 6):
        values = [
            (f"{600000 + (page - 1) * 20 + index:06d}", f"样本{page}-{index}")
            for index in range(20)
        ]
        suffix = "" if page == 1 else f"page/{page}/"
        url = f"https://q.10jqka.com.cn/thshy/detail/code/881121/{suffix}"
        pages[url] = (_page(values, page, 6), url)
    page_six = "https://q.10jqka.com.cn/thshy/detail/code/881121/page/6/"
    pages[page_six] = ("<html>login</html>", "https://q.10jqka.com.cn/account/login/")
    provider = TonghuashunConstituentProvider(page_loader=lambda url: pages[url])

    result = provider.constituents(_sector(expected=120))

    assert result.quality_status == "PARTIAL"
    assert result.retrieved_count == 100
    assert result.expected_count == 120
    assert result.coverage == 100 / 120
    assert "登录" in result.warning


def test_complete_constituent_snapshot_expires_after_thirty_days(tmp_path) -> None:
    now = datetime(2026, 8, 17, 15, 30)
    store = ConstituentSnapshotStore(
        tmp_path / "constituents.json", now=lambda: now
    )
    provider = TonghuashunConstituentProvider(
        page_loader=lambda url: (
            _page([("600584", "长电科技")], 1, 1),
            url,
        )
    )
    batch = provider.constituents(_sector(expected=1))
    store.save(_sector(expected=1), batch)

    assert store.load(_sector(expected=1)) == batch

    expired = ConstituentSnapshotStore(
        tmp_path / "constituents.json", now=lambda: now + timedelta(days=31)
    )
    assert expired.load(_sector(expected=1)) is None


class StaticFetcher:
    def __init__(self, responses: list[tuple[str, str]]) -> None:
        self.responses = list(responses)

    def fetch(self, url: str, timeout_seconds: float) -> tuple[str, str]:
        return self.responses.pop(0)

    def close(self) -> None:
        return None


def test_session_recovery_uses_same_parser_and_accepts_ninety_five_percent() -> None:
    values = [(f"{600000 + index:06d}", f"样本{index}") for index in range(19)]
    html = _page(values, 1, 1)
    login_url = "https://q.10jqka.com.cn/account/login/"
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: ("<html>login</html>", login_url),
        session_factory=lambda: StaticFetcher([(html, url_for_sector())]),
        browser_factory=lambda: StaticFetcher([]),
    )
    recovered = TonghuashunConstituentProvider(
        page_acquirer=acquirer
    ).constituents(_sector(expected=20))
    direct = TonghuashunConstituentProvider(
        page_loader=lambda url: (html, url)
    ).constituents(_sector(expected=20))

    assert recovered.values == direct.values
    assert recovered.quality_status == "COMPLETE"
    assert recovered.coverage == 0.95
    assert recovered.acquisition_mode == "SESSION_HTTP"
    assert recovered.recovery_used is True
    assert [item.mode for item in recovered.acquisition_attempts] == [
        AcquisitionMode.DIRECT_HTTP,
        AcquisitionMode.SESSION_HTTP,
    ]


def test_snapshot_preserves_recovery_diagnostics(tmp_path) -> None:
    html = _page([("600584", "长电科技")], 1, 1)
    login_url = "https://q.10jqka.com.cn/account/login/"
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: ("<html>login</html>", login_url),
        session_factory=lambda: StaticFetcher([(html, url_for_sector())]),
        browser_factory=lambda: StaticFetcher([]),
    )
    batch = TonghuashunConstituentProvider(
        page_acquirer=acquirer
    ).constituents(_sector(expected=1))
    store = ConstituentSnapshotStore(tmp_path / "constituents.json")

    store.save(_sector(expected=1), batch)

    assert store.load(_sector(expected=1)) == batch


def test_version_one_snapshot_without_diagnostics_remains_readable(tmp_path) -> None:
    path = tmp_path / "constituents.json"
    path.write_text(
        json.dumps(
            {
                "version": 1,
                "sectors": {
                    "881121": {
                        "sector_code": "881121",
                        "sector_name": "半导体",
                        "source_family": "TONGHUASHUN",
                        "values": [["600584", "SH", "长电科技"]],
                        "expected_count": 1,
                        "retrieved_count": 1,
                        "quality_status": "COMPLETE",
                        "coverage": 1.0,
                        "retrieved_at": "2026-08-17T15:30:00",
                        "warning": "",
                        "snapshot_at": datetime.now().isoformat(),
                    }
                },
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    batch = ConstituentSnapshotStore(path).load(_sector(expected=1))

    assert batch is not None
    assert batch.acquisition_mode == "DIRECT_HTTP"
    assert batch.recovery_used is False
    assert batch.acquisition_attempts == ()


def url_for_sector() -> str:
    return "https://q.10jqka.com.cn/thshy/detail/code/881121/"
