package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.marketintel.MarketIntelRefreshRunRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalInterpretation;
import com.finscope.domain.marketintel.MarketIntelRefreshRun;
import com.finscope.service.marketintel.CapitalInterpretationFacade;
import com.finscope.service.marketintel.MarketIntelCapitalService;
import com.finscope.service.marketintel.MarketIntelCapitalView;
import com.finscope.service.marketintel.DragonTigerView;
import com.finscope.service.marketintel.MarketIntelDragonTigerService;
import com.finscope.service.marketintel.MarketIntelRefreshCoordinator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market-intel")
public class MarketIntelController {
    private final MarketIntelCapitalService capital;
    private final MarketIntelRefreshCoordinator refresh;
    private final CapitalInterpretationFacade agent;
    private final MarketIntelRefreshRunRepository refreshRuns;
    private final MarketIntelDragonTigerService dragonTiger;

    public MarketIntelController(MarketIntelCapitalService capital,
                                 MarketIntelRefreshCoordinator refresh,
                                 CapitalInterpretationFacade agent,
                                 MarketIntelRefreshRunRepository refreshRuns,
                                 MarketIntelDragonTigerService dragonTiger) {
        this.capital = capital;
        this.refresh = refresh;
        this.agent = agent;
        this.refreshRuns = refreshRuns;
        this.dragonTiger = dragonTiger;
    }

    /**
     * 查询市场情报可用的股票标的列表。
     *
     * @return 股票标的列表。
     */
    @GetMapping("/instruments")
    public ApiResponse<List<Instrument>> instruments() {
        return ApiResponses.success(capital.listStockInstruments());
    }

    /**
     * 触发标的市场情报刷新。
     *
     * @param id 标的 ID。
     * @return 202 Accepted 响应，响应体为市场情报刷新任务。
     */
    @PostMapping("/instruments/{id}/refresh")
    public ResponseEntity<ApiResponse<MarketIntelRefreshRun>> refresh(@PathVariable Long id) {
        return ResponseEntity.accepted().body(ApiResponses.success(refresh.requestRefresh(id)));
    }

    /**
     * 查询市场情报刷新任务状态。
     *
     * @param id 刷新任务 ID。
     * @return 市场情报刷新任务详情。
     */
    @GetMapping("/refresh-runs/{id}")
    public ApiResponse<MarketIntelRefreshRun> refreshRun(@PathVariable Long id) {
        return ApiResponses.success(refreshRuns.findRunById(id)
                .orElseThrow(() -> new ResourceNotFoundException("市场情报刷新任务不存在：" + id)));
    }

    /**
     * 查询标的资金行为分析。
     *
     * @param id 标的 ID。
     * @param range 统计区间，默认 20d。
     * @param granularity 数据粒度，默认 5m。
     * @return 资金行为视图，包含资金流向和行为特征。
     */
    @GetMapping("/instruments/{id}/capital-behavior")
    public ApiResponse<MarketIntelCapitalView> behavior(@PathVariable Long id, @RequestParam(defaultValue = "20d") String range, @RequestParam(defaultValue = "5m") String granularity) {
        return ApiResponses.success(capital.view(id, range, granularity));
    }

    /**
     * 查询标的龙虎榜数据。
     *
     * @param id 标的 ID。
     * @param days 回溯天数，默认 120。
     * @return 龙虎榜视图，包含上榜记录和席位信息。
     */
    @GetMapping("/instruments/{id}/dragon-tiger")
    public ApiResponse<DragonTigerView> dragonTiger(
            @PathVariable Long id,
            @RequestParam(defaultValue = "120") int days) {
        return ApiResponses.success(dragonTiger.view(id, days));
    }

    /**
     * 请求生成标的资金解读。
     *
     * @param id 标的 ID。
     * @param force 是否强制重新生成解读，默认 false。
     * @return 202 Accepted 响应，响应体为生成中的资金解读。
     */
    @PostMapping("/instruments/{id}/capital-interpretations")
    public ResponseEntity<ApiResponse<CapitalInterpretation>> interpret(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.accepted().body(ApiResponses.success(agent.request(id, force)));
    }

    /**
     * 查询标的最新资金解读。
     *
     * @param id 标的 ID。
     * @return 该标的的最新资金解读。
     */
    @GetMapping("/instruments/{id}/capital-interpretations/latest")
    public ApiResponse<CapitalInterpretation> latestInterpretation(@PathVariable Long id) {
        return ApiResponses.success(agent.latest(id));
    }

    /**
     * 查询资金解读详情。
     *
     * @param id 资金解读 ID。
     * @return 指定资金解读详情。
     */
    @GetMapping("/capital-interpretations/{id}")
    public ApiResponse<CapitalInterpretation> interpretation(@PathVariable Long id) {
        return ApiResponses.success(agent.get(id));
    }
}
