package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.marketintel.CapitalInterpretationRepository;
import com.finscope.dao.marketintel.MarketIntelRefreshRunRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalInterpretation;
import com.finscope.domain.marketintel.MarketIntelRefreshRun;
import com.finscope.service.marketintel.CapitalInterpretationFacade;
import com.finscope.service.marketintel.MarketIntelCapitalService;
import com.finscope.service.marketintel.MarketIntelCapitalView;
import com.finscope.service.marketintel.MarketIntelRefreshCoordinator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/market-intel")
public class MarketIntelController {

    @Resource
    private MarketIntelCapitalService capital;
    @Resource
    private MarketIntelRefreshCoordinator refresh;
    @Resource
    private CapitalInterpretationFacade agent;
    @Resource
    private CapitalInterpretationRepository interpretations;
    @Resource
    private MarketIntelRefreshRunRepository refreshRuns;

    @GetMapping("/instruments")
    public ApiResponse<List<Instrument>> instruments() {
        return ApiResponses.success(capital.listStockInstruments());
    }

    @PostMapping("/instruments/{id}/refresh")
    public ResponseEntity<ApiResponse<MarketIntelRefreshRun>> refresh(@PathVariable Long id) {
        return ResponseEntity.accepted().body(ApiResponses.success(refresh.requestRefresh(id)));
    }

    @GetMapping("/refresh-runs/{id}")
    public ApiResponse<MarketIntelRefreshRun> refreshRun(@PathVariable Long id) {
        return ApiResponses.success(refreshRuns.findRunById(id)
                .orElseThrow(() -> new ResourceNotFoundException("市场情报刷新任务不存在：" + id)));
    }

    @GetMapping("/instruments/{id}/capital-behavior")
    public ApiResponse<MarketIntelCapitalView> behavior(@PathVariable Long id, @RequestParam(defaultValue = "20d") String range, @RequestParam(defaultValue = "5m") String granularity) {
        return ApiResponses.success(capital.view(id, range, granularity));
    }

    @PostMapping("/instruments/{id}/capital-interpretations")
    public ResponseEntity<ApiResponse<CapitalInterpretation>> interpret(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.accepted().body(ApiResponses.success(agent.request(id, force)));
    }

    @GetMapping("/capital-interpretations/{id}")
    public ApiResponse<CapitalInterpretation> interpretation(@PathVariable Long id) {
        return ApiResponses.success(interpretations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("资金行为解读不存在：" + id)));
    }
}
