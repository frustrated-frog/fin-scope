from __future__ import annotations

from collections.abc import Callable

from finscope_market_data.discovery.acquisition import (
    AcquisitionMode,
    TonghuashunPageAcquirer,
)


URL = "https://q.10jqka.com.cn/thshy/detail/code/881121/"


class RecordingFetcher:
    def __init__(
        self,
        responses: list[tuple[str, str]],
    ) -> None:
        self.responses = list(responses)
        self.calls: list[tuple[str, float]] = []
        self.closed = False

    def fetch(self, url: str, timeout_seconds: float) -> tuple[str, str]:
        self.calls.append((url, timeout_seconds))
        return self.responses.pop(0)

    def close(self) -> None:
        self.closed = True


class RecordingFactory:
    def __init__(self, fetcher: RecordingFetcher) -> None:
        self.fetcher = fetcher
        self.calls = 0

    def __call__(self) -> RecordingFetcher:
        self.calls += 1
        return self.fetcher


def _assess(html: str, final_url: str) -> str:
    if "/account/login" in final_url:
        return "登录重定向"
    if "stock-row" not in html:
        return "成分表为空"
    return ""


def _loader(
    responses: list[tuple[str, str]],
    calls: list[str],
) -> Callable[[str], tuple[str, str]]:
    def load(url: str) -> tuple[str, str]:
        calls.append(url)
        return responses.pop(0)

    return load


def test_direct_success_does_not_initialize_scrapling_sessions() -> None:
    direct_calls: list[str] = []
    session_factory = RecordingFactory(RecordingFetcher([]))
    browser_factory = RecordingFactory(RecordingFetcher([]))
    acquirer = TonghuashunPageAcquirer(
        direct_loader=_loader([("<tr class='stock-row'>ok</tr>", URL)], direct_calls),
        session_factory=session_factory,
        browser_factory=browser_factory,
    )

    result = acquirer.fetch(URL, assess=_assess)

    assert result.accepted is True
    assert result.mode is AcquisitionMode.DIRECT_HTTP
    assert [item.mode for item in result.attempts] == [
        AcquisitionMode.DIRECT_HTTP
    ]
    assert direct_calls == [URL]
    assert session_factory.calls == 0
    assert browser_factory.calls == 0


def test_login_redirect_recovers_with_reused_http_session() -> None:
    direct_calls: list[str] = []
    session = RecordingFetcher(
        [
            ("<tr class='stock-row'>page one</tr>", URL),
            ("<tr class='stock-row'>page two</tr>", f"{URL}page/2/"),
        ]
    )
    session_factory = RecordingFactory(session)
    browser_factory = RecordingFactory(RecordingFetcher([]))
    acquirer = TonghuashunPageAcquirer(
        direct_loader=_loader(
            [("<html>login</html>", "https://q.10jqka.com.cn/account/login/")],
            direct_calls,
        ),
        session_factory=session_factory,
        browser_factory=browser_factory,
    )

    first = acquirer.fetch(URL, assess=_assess)
    second = acquirer.fetch(f"{URL}page/2/", assess=_assess)

    assert first.accepted is True
    assert first.mode is AcquisitionMode.SESSION_HTTP
    assert [item.mode for item in first.attempts] == [
        AcquisitionMode.DIRECT_HTTP,
        AcquisitionMode.SESSION_HTTP,
    ]
    assert second.accepted is True
    assert second.mode is AcquisitionMode.SESSION_HTTP
    assert [item.mode for item in second.attempts] == [
        AcquisitionMode.SESSION_HTTP
    ]
    assert direct_calls == [URL]
    assert session_factory.calls == 1
    assert [item[0] for item in session.calls] == [URL, f"{URL}page/2/"]
    assert browser_factory.calls == 0
