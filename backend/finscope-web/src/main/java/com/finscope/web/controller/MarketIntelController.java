package com.finscope.web.controller;

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
    public List<Instrument> instruments() {
        return capital.listStockInstruments();
    }

    @PostMapping("/instruments/{id}/refresh")
    public ResponseEntity<MarketIntelRefreshRun> refresh(@PathVariable Long id) {
        return ResponseEntity.accepted().body(refresh.requestRefresh(id));
    }

    @GetMapping("/refresh-runs/{id}")
    public MarketIntelRefreshRun refreshRun(@PathVariable Long id) {
        return refreshRuns.findRunById(id)
                .orElseThrow(() -> new ResourceNotFoundException("市场情报刷新任务不存在：" + id));
    }

    @GetMapping("/instruments/{id}/capital-behavior")
    public MarketIntelCapitalView behavior(@PathVariable Long id, @RequestParam(defaultValue = "20d") String range, @RequestParam(defaultValue = "5m") String granularity) {
        return capital.view(id, range, granularity);
    }

    @PostMapping("/instruments/{id}/capital-interpretations")
    public ResponseEntity<CapitalInterpretation> interpret(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.accepted().body(agent.request(id, force));
    }

    @GetMapping("/capital-interpretations/{id}")
    public CapitalInterpretation interpretation(@PathVariable Long id) {
        return interpretations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("资金行为解读不存在：" + id));
    }
}
