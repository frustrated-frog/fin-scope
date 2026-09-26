from __future__ import annotations

import asyncio
import os
from typing import Any

import httpx

from finscope_market_data.providers.base import ProviderError


def configure_direct_market_access() -> None:
    """Bypass inherited proxies for local services and domestic market sources.

    AKShare creates its own requests sessions, so configure their proxy bypass
    once for this process before starting concurrent acquisition workers.
    """
    hosts = [
        "localhost", "127.0.0.1", "::1", "eastmoney.com", "sina.com.cn",
        "sinajs.cn", "sina.cn", "gtimg.cn", "qq.com", "10jqka.com.cn",
        "cninfo.com.cn",
    ]
    existing = ",".join(os.environ.get(key, "") for key in ("no_proxy", "NO_PROXY"))
    entries = list(dict.fromkeys(value.strip() for value in existing.split(",") if value.strip()))
    entries.extend(host for host in hosts if host not in entries)
    bypass = ",".join(entries)
    os.environ["NO_PROXY"] = bypass
    os.environ["no_proxy"] = bypass


class ProviderHttpClient:
    def __init__(
        self,
        timeout_seconds: float = 8.0,
        client: Any | None = None,
        minimum_interval_seconds: float = 0.0,
    ) -> None:
        self.timeout = httpx.Timeout(timeout_seconds, connect=min(timeout_seconds, 4.0))
        self.headers = {
            "User-Agent": "Mozilla/5.0 FinScope-Market-Data/0.1",
        }
        self._client = client or httpx.AsyncClient(
            trust_env=False,
            timeout=self.timeout,
            headers=self.headers,
            limits=httpx.Limits(max_connections=20, max_keepalive_connections=10),
        )
        self._minimum_interval_seconds = max(0.0, minimum_interval_seconds)
        self._request_lock = asyncio.Lock()
        self._last_request_started_at: float | None = None

    async def get_text(
        self,
        provider_code: str,
        url: str,
        *,
        headers: dict[str, str] | None = None,
        params: dict[str, Any] | None = None,
        encoding: str | None = None,
    ) -> str:
        try:
            async with self._request_lock:
                loop = asyncio.get_running_loop()
                if self._last_request_started_at is not None:
                    remaining = (
                        self._minimum_interval_seconds
                        - (loop.time() - self._last_request_started_at)
                    )
                    if remaining > 0:
                        await asyncio.sleep(remaining)
                self._last_request_started_at = loop.time()
                response = await self._client.get(url, headers=headers, params=params)
        except httpx.TimeoutException as error:
            raise ProviderError("TIMEOUT", f"{provider_code} request timed out") from error
        except httpx.HTTPError as error:
            raise ProviderError("CONNECTION_ERROR", f"{provider_code} request failed: {error}") from error
        if response.status_code < 200 or response.status_code >= 300:
            retryable = response.status_code in {429, 500, 502, 503, 504}
            raise ProviderError(
                f"HTTP_{response.status_code}",
                f"{provider_code} returned HTTP {response.status_code}",
                retryable,
            )
        if encoding:
            return response.content.decode(encoding, errors="replace")
        return response.text

    async def get_json(self, provider_code: str, url: str, **kwargs: Any) -> dict[str, Any]:
        text = await self.get_text(provider_code, url, **kwargs)
        try:
            import json

            payload = json.loads(text)
        except ValueError as error:
            raise ProviderError("SCHEMA_DRIFT", f"{provider_code} returned invalid JSON", False) from error
        if not isinstance(payload, dict):
            raise ProviderError("SCHEMA_DRIFT", f"{provider_code} returned a non-object payload", False)
        return payload

    async def aclose(self) -> None:
        await self._client.aclose()
