from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from datetime import date, timedelta
from typing import Any, Literal

from fastapi import FastAPI, HTTPException, Query
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse

from finscope_market_data.forecast.schemas import SingleStockForecastRequest
from finscope_market_data.forecast.service import build_forecast
from finscope_market_data.forecast.panel import PanelArtifactStore
from finscope_market_data.forecast.context import build_aligned_context
from finscope_market_data.discovery.providers import TonghuashunHotSectorProvider
from finscope_market_data.discovery.trading_scope import TradingScopePolicy
from finscope_market_data.discovery.schemas import (
    DiscoveryEvaluationRequest,
    DiscoveryRequest,
)
from finscope_market_data.discovery.evaluation import evaluate_discovery_outcomes
from finscope_market_data.discovery.service import (
    DiscoveryBarsSnapshot,
    StockDiscoveryService,
)
from finscope_market_data.health import ProviderHealthRegistry
from finscope_market_data.models import DataCapability, DataEnvelope, QualityStatus, StockSymbol
from finscope_market_data.providers.akshare_provider import AkshareProvider
from finscope_market_data.providers.eastmoney import EastmoneyProvider
from finscope_market_data.providers.fuyao import (
    FuyaoAsyncApiClient,
    FuyaoConstituentProvider,
    FuyaoMarketDumpClient,
    FuyaoMarketDataProvider,
    FuyaoSyncApiClient,
)
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient
from finscope_market_data.providers.index_daily import (
    EastmoneyIndexDailyProvider,
    SinaIndexDailyProvider,
)
from finscope_market_data.providers.pytdx_provider import PytdxQuoteProvider
from finscope_market_data.providers.sina import SinaCapitalFlowProvider, SinaQuoteProvider
from finscope_market_data.providers.tencent import TencentQuoteProvider
from finscope_market_data.router import ProviderRouter
from finscope_market_data.settings import Settings
from finscope_market_data.sectors import TonghuashunSectorService
from finscope_market_data.sector_history import TonghuashunSectorHistoryService
from finscope_market_data.snapshot_store import SnapshotStore
from finscope_market_data.breadth import MarketBreadthService


def build_router(settings: Settings | None = None) -> ProviderRouter:
    config = settings or Settings()
    providers: list[Any] = [
        EastmoneyIndexDailyProvider(),
        SinaIndexDailyProvider(),
        TencentQuoteProvider(),
        SinaQuoteProvider(),
        SinaCapitalFlowProvider(),
        AkshareProvider(),
        PytdxQuoteProvider(),
        EastmoneyProvider(),
    ]
    if config.fuyao_api_key.strip():
        providers.insert(
            0,
            FuyaoMarketDataProvider(
                FuyaoAsyncApiClient(
                    api_key=config.fuyao_api_key,
                    base_url=config.fuyao_base_url,
                    http=ProviderHttpClient(
                        timeout_seconds=config.fuyao_timeout_seconds,
                        minimum_interval_seconds=(
                            config.fuyao_minimum_interval_seconds
                        ),
                    ),
                )
            ),
        )
    return ProviderRouter(
        providers=providers,
        snapshots=SnapshotStore(config.data_dir / "market-data-snapshots.db"),
        health=ProviderHealthRegistry(
            failure_threshold=config.failure_threshold,
            open_seconds=config.circuit_open_seconds,
        ),
        max_retries=config.max_retries,
        retry_delay_seconds=config.retry_delay_seconds,
        daily_bar_retry_cooldown_seconds=config.daily_bar_retry_cooldown_seconds,
    )


def create_app(
    router: ProviderRouter | None = None,
    discovery: StockDiscoveryService | None = None,
    sectors: TonghuashunSectorService | None = None,
    sector_history: TonghuashunSectorHistoryService | None = None,
    breadth: MarketBreadthService | None = None,
    market_dumps: Any | None = None,
    settings: Settings | None = None,
) -> FastAPI:
    config = settings or Settings()
    panel_store = PanelArtifactStore(config.data_dir / "quant")

    @asynccontextmanager
    async def lifespan(application: FastAPI):
        if application.state.router is None:
            application.state.router = build_router(config)
        if application.state.discovery is None:
            constituent_providers: list[object] = []
            if config.fuyao_api_key.strip():
                constituent_providers.append(
                    FuyaoConstituentProvider(
                        FuyaoSyncApiClient(
                            api_key=config.fuyao_api_key,
                            base_url=config.fuyao_base_url,
                            timeout_seconds=config.fuyao_timeout_seconds,
                            minimum_interval_seconds=(
                                config.fuyao_minimum_interval_seconds
                            ),
                        )
                    )
                )
            application.state.discovery = StockDiscoveryService(
                providers=[
                    TonghuashunHotSectorProvider(),
                ],
                constituent_providers=constituent_providers,
                market=_RouterDiscoveryMarket(application.state.router),
                universe_snapshot_path=config.data_dir / "stock-discovery-universe.json",
                constituent_snapshot_path=(
                    config.data_dir / "stock-discovery-constituents.json"
                ),
                panel_store=panel_store,
            )
        if application.state.sectors is None:
            application.state.sectors = TonghuashunSectorService()
        if application.state.sector_history is None:
            application.state.sector_history = TonghuashunSectorHistoryService()
        if application.state.breadth is None:
            application.state.breadth = MarketBreadthService(
                snapshot_store=application.state.router.snapshots
            )
        if application.state.market_dumps is None:
            application.state.market_dumps = FuyaoMarketDumpClient(
                FuyaoAsyncApiClient(
                    api_key=config.fuyao_api_key,
                    base_url=config.fuyao_base_url,
                    http=ProviderHttpClient(
                        timeout_seconds=config.fuyao_timeout_seconds,
                        minimum_interval_seconds=(
                            config.fuyao_minimum_interval_seconds
                        ),
                    ),
                )
            )
        try:
            yield
        finally:
            close_errors: list[Exception] = []
            close_discovery = getattr(application.state.discovery, "close", None)
            if callable(close_discovery):
                try:
                    await asyncio.to_thread(close_discovery)
                except Exception as error:
                    close_errors.append(error)
            close_market_dumps = getattr(
                application.state.market_dumps, "aclose", None
            )
            if callable(close_market_dumps):
                try:
                    await close_market_dumps()
                except Exception as error:
                    close_errors.append(error)
            try:
                await application.state.router.aclose()
            except Exception as error:
                close_errors.append(error)
            if close_errors:
                raise close_errors[0]

    application = FastAPI(
        title="FinScope Market Data Service",
        version="0.1.0",
        lifespan=lifespan,
    )
    application.state.router = router
    application.state.discovery = discovery
    application.state.sectors = sectors
    application.state.sector_history = sector_history
    application.state.breadth = breadth
    application.state.market_dumps = market_dumps

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

    @application.get("/v1/market-dumps/{kind}/download-url")
    async def market_dump_download_url(
        kind: Literal["daily-k", "daily-k-10d", "adjustment-factors"],
    ) -> JSONResponse:
        try:
            result = await application.state.market_dumps.download_url(kind)
        except ProviderError as error:
            raise HTTPException(
                status_code=503,
                detail={
                    "error_type": error.error_type,
                    "message": str(error),
                    "retryable": error.retryable,
                },
            ) from error
        return JSONResponse(
            status_code=200,
            content=jsonable_encoder(result),
            headers={"Cache-Control": "no-store, private"},
        )

    @application.get("/v1/sectors/{category}")
    async def sectors_catalog(
        category: Literal["INDUSTRY", "CONCEPT"],
    ) -> JSONResponse:
        result = await asyncio.to_thread(application.state.sectors.fetch, category)
        return JSONResponse(
            status_code=200,
            content=jsonable_encoder(result.model_dump(mode="json")),
        )

    @application.get("/v1/sectors/INDUSTRY/history")
    async def sector_history(
        business_date: date | None = Query(default=None),
        window: int = Query(default=60, ge=20, le=120),
    ) -> JSONResponse:
        requested = business_date or _previous_weekday(date.today())
        result = await asyncio.to_thread(
            application.state.sector_history.fetch,
            requested,
            window,
        )
        return JSONResponse(
            status_code=200,
            content=jsonable_encoder(result.model_dump(mode="json")),
        )

    @application.get("/v1/markets/CN-A/breadth")
    async def market_breadth(
        business_date: date | None = Query(default=None),
    ) -> JSONResponse:
        requested = business_date
        if requested is None:
            requested = await asyncio.to_thread(
                application.state.breadth.latest_trade_date
            )
        result = await asyncio.to_thread(
            application.state.breadth.fetch,
            requested,
        )
        return JSONResponse(
            status_code=200,
            content=jsonable_encoder(result.model_dump(mode="json")),
        )

    @application.post("/v1/quant/single-stock-forecasts")
    async def single_stock_forecast(request: SingleStockForecastRequest) -> JSONResponse:
        market = _market_for_code(request.code)
        symbol = StockSymbol(market=market, code=request.code)
        envelope = await _router(application).fetch(
            DataCapability.DAILY_BARS,
            symbol,
            limit=5000,
        )
        if envelope.data is not None and any(
            bar.adjustment != "QFQ" for bar in envelope.data
        ):
            envelope = await _router(application).fetch(
                DataCapability.DAILY_BARS,
                symbol,
                limit=5000,
                force_refresh=True,
            )
        if envelope.data is None:
            raise HTTPException(status_code=503, detail="前复权历史行情当前不可用")
        market_symbol = StockSymbol(market="SH", code="000300")
        market_envelope = await _router(application).fetch(
            DataCapability.DAILY_BARS,
            market_symbol,
            limit=5000,
        )
        market_bars = market_envelope.data or []
        context = build_aligned_context(envelope.data, market_bars=market_bars)
        context_warnings = list(envelope.warnings)
        if context.market_coverage < 0.95:
            context_warnings.append("沪深300上下文覆盖不足，相关特征已按中性值降级")
        context_warnings.append("第一批未启用行业代理指数，行业因子按中性值降级")
        scope = TradingScopePolicy().classify(request.code)
        if scope.reason == "NO_STAR_MARKET_PERMISSION":
            context_warnings.append("该标的属于科创板，当前账户不可交易，本报告仅供研究")
        result = build_forecast(
            envelope.data,
            instrument_code=f"{request.code}.{market}",
            source_code=envelope.source_code or "UNKNOWN",
            source_family=envelope.source_family or "UNKNOWN",
            quality_status=envelope.quality_status.value,
            warnings=context_warnings,
            horizon_days=request.horizon_days,
            context=context,
            panel_artifact=panel_store.load(request.horizon_days),
        )
        return JSONResponse(
            status_code=200,
            content=jsonable_encoder(result.model_dump(mode="json", by_alias=True)),
        )

    @application.post("/v1/quant/stock-discoveries")
    async def stock_discovery(request: DiscoveryRequest) -> JSONResponse:
        result = await application.state.discovery.discover(request)
        return JSONResponse(
            status_code=200,
            content=jsonable_encoder(result.model_dump(mode="json")),
        )

    @application.post("/v1/quant/stock-discovery-evaluations")
    async def stock_discovery_evaluation(
        request: DiscoveryEvaluationRequest,
    ) -> JSONResponse:
        result = evaluate_discovery_outcomes(request)
        return JSONResponse(
            status_code=200,
            content=jsonable_encoder(result.model_dump(mode="json")),
        )

    @application.get("/v1/stocks/{market}/{code}/quote")
    async def quote(
        market: str,
        code: str,
        provider_family: str | None = Query(default=None),
        provider_mode: bool = Query(default=False),
    ):
        return _response(
            await _fetch(
                application,
                DataCapability.QUOTE,
                market,
                code,
                provider_family=provider_family,
                provider_mode=provider_mode,
            )
        )

    @application.get("/v1/stocks/{market}/{code}/daily-bars")
    async def daily_bars(
        market: str,
        code: str,
        limit: int = Query(default=250, ge=1, le=5000),
        refresh: bool = Query(default=False),
    ):
        return _response(
            await _fetch(
                application,
                DataCapability.DAILY_BARS,
                market,
                code,
                limit=limit,
                force_refresh=refresh,
            )
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
        daily_limit: int = Query(default=120, ge=1, le=5000),
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


def _previous_weekday(value: date) -> date:
    current = value
    while current.weekday() > 4:
        current -= timedelta(days=1)
    return current


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


def _market_for_code(code: str) -> str:
    if code.startswith(("6", "5", "9")):
        return "SH"
    if code.startswith(("4", "8")):
        return "BJ"
    return "SZ"


class _RouterDiscoveryMarket:
    def __init__(self, router: ProviderRouter) -> None:
        self.router = router

    async def bars(self, market: str, code: str):
        envelope = await self.router.fetch(
            DataCapability.DAILY_BARS,
            StockSymbol(market=market, code=code),
            limit=1500,
        )
        return DiscoveryBarsSnapshot(
            bars=envelope.data or [],
            quality_status=envelope.quality_status.value,
            stale_age_seconds=envelope.stale_age_seconds,
            warnings=tuple(envelope.warnings),
        )


app = create_app()
