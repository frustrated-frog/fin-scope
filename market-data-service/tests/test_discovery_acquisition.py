from __future__ import annotations

from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor
import threading
import time

import pytest

from finscope_market_data.discovery.acquisition import (
    AcquisitionMode,
    ScraplingBrowserFetcher,
    ScraplingSessionFetcher,
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


class RaisingFetcher(RecordingFetcher):
    def __init__(self, error: Exception) -> None:
        super().__init__([])
        self.error = error

    def fetch(self, url: str, timeout_seconds: float) -> tuple[str, str]:
        self.calls.append((url, timeout_seconds))
        raise self.error


class CloseRaisingFetcher(RecordingFetcher):
    def close(self) -> None:
        self.closed = True
        raise RuntimeError("close failed")


class FailingBrowser(CloseRaisingFetcher):
    def fetch(self, url: str, timeout_seconds: float) -> tuple[str, str]:
        self.calls.append((url, timeout_seconds))
        raise TimeoutError("browser failed")


class ConcurrentFetcher(RecordingFetcher):
    def __init__(self, response: tuple[str, str]) -> None:
        super().__init__([])
        self.response = response
        self.active = 0
        self.max_active = 0
        self.lock = threading.Lock()

    def fetch(self, url: str, timeout_seconds: float) -> tuple[str, str]:
        with self.lock:
            self.calls.append((url, timeout_seconds))
            self.active += 1
            self.max_active = max(self.max_active, self.active)
        time.sleep(0.03)
        with self.lock:
            self.active -= 1
        return self.response


class RecordingFactory:
    def __init__(self, fetcher: RecordingFetcher) -> None:
        self.fetcher = fetcher
        self.calls = 0

    def __call__(self) -> RecordingFetcher:
        self.calls += 1
        return self.fetcher


class SequenceFactory:
    def __init__(self, fetchers: list[RecordingFetcher]) -> None:
        self.fetchers = list(fetchers)
        self.calls = 0

    def __call__(self) -> RecordingFetcher:
        self.calls += 1
        return self.fetchers.pop(0)


class FakeScraplingResponse:
    def __init__(self, body: bytes, encoding: str, url: str) -> None:
        self.body = body
        self.encoding = encoding
        self.url = url


class FakeScraplingClient:
    def __init__(self, response: FakeScraplingResponse) -> None:
        self.response = response
        self.get_calls: list[tuple[str, dict[str, float]]] = []
        self.fetch_calls: list[tuple[str, dict[str, float]]] = []

    def get(self, url: str, **kwargs: float) -> FakeScraplingResponse:
        self.get_calls.append((url, kwargs))
        return self.response

    def fetch(self, url: str, **kwargs: float) -> FakeScraplingResponse:
        self.fetch_calls.append((url, kwargs))
        return self.response


class FakeScraplingManager:
    def __init__(self, client: FakeScraplingClient) -> None:
        self.client = client
        self.entered = 0
        self.exited = 0

    def __enter__(self) -> FakeScraplingClient:
        self.entered += 1
        return self.client

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.exited += 1


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


def test_empty_direct_and_session_results_recover_with_browser() -> None:
    session = RecordingFetcher([("<html>empty</html>", URL)])
    browser = RecordingFetcher([("<tr class='stock-row'>ok</tr>", URL)])
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: ("<html>empty</html>", url),
        session_factory=RecordingFactory(session),
        browser_factory=RecordingFactory(browser),
    )

    result = acquirer.fetch(URL, assess=_assess)

    assert result.accepted is True
    assert result.mode is AcquisitionMode.BROWSER
    assert [item.mode for item in result.attempts] == [
        AcquisitionMode.DIRECT_HTTP,
        AcquisitionMode.SESSION_HTTP,
        AcquisitionMode.BROWSER,
    ]


def test_browser_timeout_becomes_diagnostic_and_close_is_idempotent() -> None:
    session = RecordingFetcher([("<html>empty</html>", URL)])
    browser = RaisingFetcher(TimeoutError("browser timed out with secret-cookie"))
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: ("<html>empty</html>", url),
        session_factory=RecordingFactory(session),
        browser_factory=RecordingFactory(browser),
    )

    result = acquirer.fetch(URL, assess=_assess)
    assert browser.closed is True
    acquirer.close()
    acquirer.close()

    assert result.accepted is False
    assert result.mode is AcquisitionMode.BROWSER
    assert result.attempts[-1].succeeded is False
    assert "TimeoutError" in result.attempts[-1].error
    assert "secret-cookie" not in result.attempts[-1].error
    assert session.closed is True


def test_browser_recovery_never_exceeds_configured_concurrency() -> None:
    session = ConcurrentFetcher(("<html>empty</html>", URL))
    browser = ConcurrentFetcher(("<tr class='stock-row'>ok</tr>", URL))
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: ("<html>empty</html>", url),
        session_factory=RecordingFactory(session),
        browser_factory=RecordingFactory(browser),
        browser_max_concurrency=1,
    )

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(
            executor.map(
                lambda url: acquirer.fetch(url, assess=_assess),
                [URL, f"{URL}page/2/"],
            )
        )

    assert all(result.accepted for result in results)
    assert session.max_active == 1
    assert browser.max_active == 1


def test_acquirer_rejects_urls_outside_public_tonghuashun_host() -> None:
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: ("<tr class='stock-row'>ok</tr>", url),
        session_factory=RecordingFactory(RecordingFetcher([])),
        browser_factory=RecordingFactory(RecordingFetcher([])),
    )

    with pytest.raises(ValueError, match="q.10jqka.com.cn"):
        acquirer.fetch("https://example.com/private", assess=_assess)


def test_disabled_recovery_returns_direct_failure_without_scrapling() -> None:
    session_factory = RecordingFactory(RecordingFetcher([]))
    browser_factory = RecordingFactory(RecordingFetcher([]))
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: ("<html>empty</html>", url),
        session_factory=session_factory,
        browser_factory=browser_factory,
        enabled=False,
    )

    result = acquirer.fetch(URL, assess=_assess)

    assert result.accepted is False
    assert result.mode is AcquisitionMode.DIRECT_HTTP
    assert session_factory.calls == 0
    assert browser_factory.calls == 0


def test_idle_http_session_is_closed_and_recreated() -> None:
    clock = [100.0]
    first = RecordingFetcher([("<tr class='stock-row'>first</tr>", URL)])
    second = RecordingFetcher(
        [("<tr class='stock-row'>second</tr>", f"{URL}page/2/")]
    )
    session_factory = SequenceFactory([first, second])
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: ("<html>empty</html>", url),
        session_factory=session_factory,
        browser_factory=RecordingFactory(RecordingFetcher([])),
        idle_timeout_seconds=10,
        now=lambda: clock[0],
    )

    acquirer.fetch(URL, assess=_assess)
    clock[0] += 11
    result = acquirer.fetch(f"{URL}page/2/", assess=_assess)

    assert result.accepted is True
    assert session_factory.calls == 2
    assert first.closed is True


def test_scrapling_session_fetcher_decodes_response_and_closes_manager() -> None:
    response = FakeScraplingResponse("半导体".encode("gbk"), "gbk", URL)
    client = FakeScraplingClient(response)
    manager = FakeScraplingManager(client)
    fetcher = ScraplingSessionFetcher(manager_factory=lambda: manager)

    html, final_url = fetcher.fetch(URL, timeout_seconds=12)
    fetcher.close()

    assert html == "半导体"
    assert final_url == URL
    assert client.get_calls == [(URL, {"timeout": 12})]
    assert manager.entered == 1
    assert manager.exited == 1


def test_scrapling_browser_fetcher_converts_timeout_to_milliseconds() -> None:
    response = FakeScraplingResponse(b"<html>ok</html>", "utf-8", URL)
    client = FakeScraplingClient(response)
    manager = FakeScraplingManager(client)
    fetcher = ScraplingBrowserFetcher(manager_factory=lambda: manager)

    html, final_url = fetcher.fetch(URL, timeout_seconds=12.5)
    fetcher.close()

    assert html == "<html>ok</html>"
    assert final_url == URL
    assert client.fetch_calls == [(URL, {"timeout": 12_500})]
    assert manager.entered == 1
    assert manager.exited == 1


def test_direct_and_session_exceptions_continue_to_browser_recovery() -> None:
    session = RaisingFetcher(ConnectionError("session cookie=private"))
    browser = RecordingFetcher([("<tr class='stock-row'>ok</tr>", URL)])

    def broken_direct(url: str) -> tuple[str, str]:
        raise TimeoutError("direct cookie=private")

    acquirer = TonghuashunPageAcquirer(
        direct_loader=broken_direct,
        session_factory=RecordingFactory(session),
        browser_factory=RecordingFactory(browser),
    )

    result = acquirer.fetch(URL, assess=_assess)

    assert result.accepted is True
    assert result.mode is AcquisitionMode.BROWSER
    assert [item.error for item in result.attempts[:2]] == [
        "TimeoutError",
        "ConnectionError",
    ]


def test_close_releases_browser_when_session_close_fails() -> None:
    session = CloseRaisingFetcher([("<html>empty</html>", URL)])
    browser = RecordingFetcher([("<tr class='stock-row'>ok</tr>", URL)])
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: ("<html>empty</html>", url),
        session_factory=RecordingFactory(session),
        browser_factory=RecordingFactory(browser),
    )
    acquirer.fetch(URL, assess=_assess)

    acquirer.close()

    assert session.closed is True
    assert browser.closed is True


def test_recovery_preference_is_scoped_to_one_sector() -> None:
    another_url = "https://q.10jqka.com.cn/thshy/detail/code/881122/"
    direct_responses = {
        URL: ("<html>login</html>", "https://q.10jqka.com.cn/account/login/"),
        another_url: ("<tr class='stock-row'>direct</tr>", another_url),
    }
    session = RecordingFetcher([("<tr class='stock-row'>session</tr>", URL)])
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: direct_responses[url],
        session_factory=RecordingFactory(session),
        browser_factory=RecordingFactory(RecordingFetcher([])),
    )

    recovered = acquirer.fetch(URL, assess=_assess)
    direct = acquirer.fetch(another_url, assess=_assess)

    assert recovered.mode is AcquisitionMode.SESSION_HTTP
    assert direct.mode is AcquisitionMode.DIRECT_HTTP


def test_browser_fetch_and_close_failures_still_return_diagnostics() -> None:
    browser = FailingBrowser([])
    acquirer = TonghuashunPageAcquirer(
        direct_loader=lambda url: ("<html>empty</html>", url),
        session_factory=lambda: RecordingFetcher([("<html>empty</html>", URL)]),
        browser_factory=lambda: browser,
    )

    result = acquirer.fetch(URL, assess=_assess)

    assert result.accepted is False
    assert result.attempts[-1].error == "TimeoutError"
    assert browser.closed is True
