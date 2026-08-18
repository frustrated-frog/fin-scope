from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from enum import Enum
import threading
import time
from typing import Any, Protocol
from urllib.parse import urlparse


PageAssessment = Callable[[str, str], str]
PageLoader = Callable[[str], tuple[str, str]]


class AcquisitionMode(str, Enum):
    DIRECT_HTTP = "DIRECT_HTTP"
    SESSION_HTTP = "SESSION_HTTP"
    BROWSER = "BROWSER"


@dataclass(frozen=True)
class AcquisitionAttempt:
    mode: AcquisitionMode
    succeeded: bool
    duration_ms: int
    error: str = ""


@dataclass(frozen=True)
class AcquisitionResult:
    html: str
    final_url: str
    mode: AcquisitionMode
    accepted: bool
    attempts: tuple[AcquisitionAttempt, ...]
    failure_reason: str = ""


class ManagedPageFetcher(Protocol):
    def fetch(self, url: str, timeout_seconds: float) -> tuple[str, str]: ...

    def close(self) -> None: ...


ManagedPageFetcherFactory = Callable[[], ManagedPageFetcher]
ScraplingManagerFactory = Callable[[], Any]


class ScraplingSessionFetcher:
    def __init__(
        self,
        manager_factory: ScraplingManagerFactory | None = None,
    ) -> None:
        self._manager = (manager_factory or self._default_manager_factory)()
        self._client = self._manager.__enter__()

    def fetch(self, url: str, timeout_seconds: float) -> tuple[str, str]:
        response = self._client.get(url, timeout=timeout_seconds)
        return _scrapling_response(response)

    def close(self) -> None:
        if self._manager is not None:
            self._manager.__exit__(None, None, None)
            self._manager = None
            self._client = None

    @staticmethod
    def _default_manager_factory() -> Any:
        from scrapling.fetchers import FetcherSession

        return FetcherSession(
            impersonate="chrome",
            stealthy_headers=True,
            retries=0,
        )


class ScraplingBrowserFetcher:
    def __init__(
        self,
        manager_factory: ScraplingManagerFactory | None = None,
    ) -> None:
        self._manager = (manager_factory or self._default_manager_factory)()
        self._client = self._manager.__enter__()

    def fetch(self, url: str, timeout_seconds: float) -> tuple[str, str]:
        response = self._client.fetch(
            url,
            timeout=round(timeout_seconds * 1000),
        )
        return _scrapling_response(response)

    def close(self) -> None:
        if self._manager is not None:
            self._manager.__exit__(None, None, None)
            self._manager = None
            self._client = None

    @staticmethod
    def _default_manager_factory() -> Any:
        from scrapling.fetchers import DynamicSession

        return DynamicSession(
            headless=True,
            disable_resources=True,
            max_pages=1,
        )


class TonghuashunPageAcquirer:
    def __init__(
        self,
        direct_loader: PageLoader,
        session_factory: ManagedPageFetcherFactory,
        browser_factory: ManagedPageFetcherFactory,
        enabled: bool = True,
        session_timeout_seconds: float = 15.0,
        browser_timeout_seconds: float = 20.0,
        browser_max_concurrency: int = 1,
        idle_timeout_seconds: float = 300.0,
        now: Callable[[], float] | None = None,
    ) -> None:
        self.direct_loader = direct_loader
        self.session_factory = session_factory
        self.browser_factory = browser_factory
        self.enabled = enabled
        self.session_timeout_seconds = session_timeout_seconds
        self.browser_timeout_seconds = browser_timeout_seconds
        self.idle_timeout_seconds = idle_timeout_seconds
        self.now = now or time.monotonic
        self._session: ManagedPageFetcher | None = None
        self._browser: ManagedPageFetcher | None = None
        self._session_last_used: float | None = None
        self._browser_last_used: float | None = None
        self._preferred_mode = AcquisitionMode.DIRECT_HTTP
        self._session_lock = threading.Lock()
        self._browser_lock = threading.Lock()
        self._browser_slots = threading.BoundedSemaphore(browser_max_concurrency)

    def fetch(
        self,
        url: str,
        assess: PageAssessment,
    ) -> AcquisitionResult:
        self._validate_url(url)
        attempts: list[AcquisitionAttempt] = []
        if self._preferred_mode is AcquisitionMode.BROWSER:
            return self._fetch_browser(url, assess, attempts)
        if self._preferred_mode is AcquisitionMode.SESSION_HTTP:
            session_result = self._fetch_session(url, assess, attempts)
            if session_result.accepted or not self.enabled:
                return session_result
            return self._fetch_browser(url, assess, attempts)

        started = time.monotonic()
        html = ""
        final_url = url
        try:
            html, final_url = self.direct_loader(url)
            failure_reason = assess(html, final_url)
        except Exception as error:
            failure_reason = type(error).__name__
        attempts.append(
            AcquisitionAttempt(
                mode=AcquisitionMode.DIRECT_HTTP,
                succeeded=not failure_reason,
                duration_ms=round((time.monotonic() - started) * 1000),
                error=failure_reason,
            )
        )
        if not failure_reason or not self.enabled:
            return AcquisitionResult(
                html=html,
                final_url=final_url,
                mode=AcquisitionMode.DIRECT_HTTP,
                accepted=not failure_reason,
                attempts=tuple(attempts),
                failure_reason=failure_reason,
            )
        session_result = self._fetch_session(url, assess, attempts)
        if session_result.accepted:
            return session_result
        return self._fetch_browser(url, assess, attempts)

    def _fetch_session(
        self,
        url: str,
        assess: PageAssessment,
        attempts: list[AcquisitionAttempt],
    ) -> AcquisitionResult:
        html = ""
        final_url = url
        started = time.monotonic()
        try:
            with self._session_lock:
                current_time = self.now()
                if (
                    self._session is not None
                    and self._session_last_used is not None
                    and current_time - self._session_last_used
                    > self.idle_timeout_seconds
                ):
                    self._session.close()
                    self._session = None
                if self._session is None:
                    self._session = self.session_factory()
                html, final_url = self._session.fetch(
                    url, self.session_timeout_seconds
                )
                self._session_last_used = current_time
            failure_reason = assess(html, final_url)
        except Exception as error:
            failure_reason = type(error).__name__
        attempts.append(
            AcquisitionAttempt(
                mode=AcquisitionMode.SESSION_HTTP,
                succeeded=not failure_reason,
                duration_ms=round((time.monotonic() - started) * 1000),
                error=failure_reason,
            )
        )
        if not failure_reason:
            self._preferred_mode = AcquisitionMode.SESSION_HTTP
        return AcquisitionResult(
            html=html,
            final_url=final_url,
            mode=AcquisitionMode.SESSION_HTTP,
            accepted=not failure_reason,
            attempts=tuple(attempts),
            failure_reason=failure_reason,
        )

    def _fetch_browser(
        self,
        url: str,
        assess: PageAssessment,
        attempts: list[AcquisitionAttempt],
    ) -> AcquisitionResult:
        html = ""
        final_url = url
        started = time.monotonic()
        try:
            with self._browser_slots:
                with self._browser_lock:
                    current_time = self.now()
                    if (
                        self._browser is not None
                        and self._browser_last_used is not None
                        and current_time - self._browser_last_used
                        > self.idle_timeout_seconds
                    ):
                        self._browser.close()
                        self._browser = None
                    if self._browser is None:
                        self._browser = self.browser_factory()
                    html, final_url = self._browser.fetch(
                        url, self.browser_timeout_seconds
                    )
                    self._browser_last_used = current_time
            failure_reason = assess(html, final_url)
        except Exception as error:
            failure_reason = type(error).__name__
        attempts.append(
            AcquisitionAttempt(
                mode=AcquisitionMode.BROWSER,
                succeeded=not failure_reason,
                duration_ms=round((time.monotonic() - started) * 1000),
                error=failure_reason,
            )
        )
        if not failure_reason:
            self._preferred_mode = AcquisitionMode.BROWSER
        return AcquisitionResult(
            html=html,
            final_url=final_url,
            mode=AcquisitionMode.BROWSER,
            accepted=not failure_reason,
            attempts=tuple(attempts),
            failure_reason=failure_reason,
        )

    def close(self) -> None:
        with self._session_lock:
            if self._session is not None:
                self._session.close()
                self._session = None
                self._session_last_used = None
        with self._browser_lock:
            if self._browser is not None:
                self._browser.close()
                self._browser = None
                self._browser_last_used = None
        self._preferred_mode = AcquisitionMode.DIRECT_HTTP

    def _validate_url(self, url: str) -> None:
        parsed = urlparse(url)
        if parsed.scheme != "https" or parsed.hostname != "q.10jqka.com.cn":
            raise ValueError("只允许采集 https://q.10jqka.com.cn 公共页面")


def _scrapling_response(response: Any) -> tuple[str, str]:
    encoding = str(response.encoding or "utf-8")
    html = bytes(response.body).decode(encoding, errors="replace")
    return html, str(response.url)
