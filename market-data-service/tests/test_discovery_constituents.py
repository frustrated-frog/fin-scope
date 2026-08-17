from __future__ import annotations

from datetime import datetime, timedelta

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
