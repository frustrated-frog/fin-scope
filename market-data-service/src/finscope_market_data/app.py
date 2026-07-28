from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, Query
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse

from finscope_market_data.health import ProviderHealthRegistry
from finscope_market_data.models import DataCapability, DataEnvelope, QualityStatus, StockSymbol
from finscope_market_data.providers.akshare_provider import AkshareProvider
from finscope_market_data.providers.eastmoney import EastmoneyProvider
from finscope_market_data.providers.pytdx_provider import PytdxDailyProvider
from finscope_market_data.providers.sina import SinaQuoteProvider
from finscope_market_data.providers.tencent import TencentQuoteProvider
from finscope_market_data.router import ProviderRouter
from finscope_market_data.settings import Settings
from finscope_market_data.snapshot_store import SnapshotStore


def build_router(settings: Settings | None = None) -> ProviderRouter:
    config = settings or Settings()
    return ProviderRouter(
        providers=[
            TencentQuoteProvider(),
            SinaQuoteProvider(),
            AkshareProvider(),
            PytdxDailyProvider(),
            EastmoneyProvider(),
        ],
        snapshots=SnapshotStore(config.data_dir / "market-data-snapshots.db"),
        health=ProviderHealthRegistry(
            failure_threshold=config.failure_threshold,
            open_seconds=config.circuit_open_seconds,
        ),
        max_retries=config.max_retries,
        retry_delay_seconds=config.retry_delay_seconds,
    )


def create_app(router: ProviderRouter | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        if application.state.router is None:
            application.state.router = build_router()
        try:
            yield
        finally:
            await application.state.router.aclose()

    application = FastAPI(
        title="FinScope Market Data Service",
        version="0.1.0",
        lifespan=lifespan,
    )
    application.state.router = router

    @application.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "UP", "service": "finscope-market-data"}

    @application.get("/ready")
    async def readiness() -> JSONResponse:
        current = _router(application)
        snapshot_ready, snapshot_status = current.snapshots.check_ready()
        providers_ready = bool(current.providers)
        ready = snapshot_ready and providers_ready
        return JSONResponse(
            status_code=200 if ready else 503,
            content={
                "status": "READY" if ready else "NOT_READY",
                "checks": {
                    "snapshot_store": snapshot_status,
                    "providers": "UP" if providers_ready else "NO_PROVIDERS",
                },
            },
        )

    @application.get("/v1/providers/health")
    async def provider_health() -> list[dict[str, Any]]:
        current = _router(application)
        return [item.model_dump(mode="json") for item in current.health.list(current.providers)]

    @application.get("/v1/stocks/{market}/{code}/quote")
    async def quote(market: str, code: str):
        return _response(await _fetch(application, DataCapability.QUOTE, market, code))

    @application.get("/v1/stocks/{market}/{code}/daily-bars")
    async def daily_bars(
        market: str,
        code: str,
        limit: int = Query(default=250, ge=1, le=1000),
    ):
        return _response(
            await _fetch(application, DataCapability.DAILY_BARS, market, code, limit=limit)
        )

    @application.get("/v1/stocks/{market}/{code}/capital-flow")
    async def capital_flow(
        market: str,
        code: str,
        require_minute: bool = Query(default=False),
        provider_mode: bool = Query(default=False),
    ):
        return _response(
            await _fetch(
                application,
                DataCapability.CAPITAL_FLOW,
                market,
                code,
                require_minute=require_minute,
                provider_mode=provider_mode,
            )
        )

    @application.get("/v1/stocks/{market}/{code}/profile")
    async def profile(market: str, code: str):
        return _response(await _fetch(application, DataCapability.PROFILE, market, code))

    @application.get("/v1/stocks/{market}/{code}/financial-statements")
    async def financial_statements(
        market: str,
        code: str,
        period_end: str,
        report_type: str,
        scope: str = "CONSOLIDATED",
    ):
        return _response(
            await _fetch(
                application,
                DataCapability.FINANCIAL_STATEMENTS,
                market,
                code,
                period_end=period_end,
                report_type=report_type,
                scope=scope,
            )
        )

    @application.get("/v1/stocks/{market}/{code}/overview")
    async def overview(
        market: str,
        code: str,
        daily_limit: int = Query(default=120, ge=1, le=1000),
    ):
        symbol = StockSymbol(market=market, code=code)
        current = _router(application)
        capabilities = [
            DataCapability.QUOTE,
            DataCapability.DAILY_BARS,
            DataCapability.CAPITAL_FLOW,
            DataCapability.PROFILE,
        ]
        results = await asyncio.gather(
            *[
                current.fetch(
                    capability,
                    symbol,
                    **({"limit": daily_limit} if capability is DataCapability.DAILY_BARS else {}),
                )
                for capability in capabilities
            ]
        )
        datasets = {
            "quote": results[0].model_dump(mode="json"),
            "daily_bars": results[1].model_dump(mode="json"),
            "capital_flow": results[2].model_dump(mode="json"),
            "profile": results[3].model_dump(mode="json"),
        }
        quality = _overview_quality(results)
        payload = {
            "symbol": symbol.model_dump(mode="json"),
            "quality_status": quality.value,
            "datasets": datasets,
        }
        return JSONResponse(
            status_code=503 if quality is QualityStatus.UNAVAILABLE else 200,
            content=jsonable_encoder(payload),
        )

    return application


async def _fetch(
    application: FastAPI,
    capability: DataCapability,
    market: str,
    code: str,
    **kwargs: Any,
) -> DataEnvelope[Any]:
    return await _router(application).fetch(
        capability,
        StockSymbol(market=market, code=code),
        **kwargs,
    )


def _router(application: FastAPI) -> ProviderRouter:
    current = application.state.router
    if current is None:
        raise RuntimeError("market data router is not initialized")
    return current


def _response(envelope: DataEnvelope[Any]) -> JSONResponse:
    return JSONResponse(
        status_code=503 if envelope.quality_status is QualityStatus.UNAVAILABLE else 200,
        content=jsonable_encoder(envelope.model_dump(mode="json")),
    )


def _overview_quality(results: list[DataEnvelope[Any]]) -> QualityStatus:
    statuses = {result.quality_status for result in results}
    if statuses == {QualityStatus.UNAVAILABLE}:
        return QualityStatus.UNAVAILABLE
    if QualityStatus.UNAVAILABLE in statuses or len(statuses) > 1:
        if statuses <= {QualityStatus.STALE_FALLBACK, QualityStatus.UNAVAILABLE}:
            return QualityStatus.STALE_FALLBACK
        return QualityStatus.PARTIAL_FRESH
    return results[0].quality_status


app = create_app()
