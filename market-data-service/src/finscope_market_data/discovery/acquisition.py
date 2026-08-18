from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from enum import Enum
import time
from typing import Protocol


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


class TonghuashunPageAcquirer:
    def __init__(
        self,
        direct_loader: PageLoader,
        session_factory: ManagedPageFetcherFactory,
        browser_factory: ManagedPageFetcherFactory,
        enabled: bool = True,
        session_timeout_seconds: float = 15.0,
    ) -> None:
        self.direct_loader = direct_loader
        self.session_factory = session_factory
        self.browser_factory = browser_factory
        self.enabled = enabled
        self.session_timeout_seconds = session_timeout_seconds
        self._session: ManagedPageFetcher | None = None
        self._preferred_mode = AcquisitionMode.DIRECT_HTTP

    def fetch(
        self,
        url: str,
        assess: PageAssessment,
    ) -> AcquisitionResult:
        attempts: list[AcquisitionAttempt] = []
        if self._preferred_mode is AcquisitionMode.SESSION_HTTP:
            return self._fetch_session(url, assess, attempts)

        started = time.monotonic()
        html, final_url = self.direct_loader(url)
        failure_reason = assess(html, final_url)
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
        return self._fetch_session(url, assess, attempts)

    def _fetch_session(
        self,
        url: str,
        assess: PageAssessment,
        attempts: list[AcquisitionAttempt],
    ) -> AcquisitionResult:
        if self._session is None:
            self._session = self.session_factory()
        started = time.monotonic()
        html, final_url = self._session.fetch(url, self.session_timeout_seconds)
        failure_reason = assess(html, final_url)
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
