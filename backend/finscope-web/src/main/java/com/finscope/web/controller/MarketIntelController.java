package com.finscope.web.controller;

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

import java.util.List;

@RestController
@RequestMapping("/api/market-intel")
public class MarketIntelController {
    private final MarketIntelCapitalService capital;private final MarketIntelRefreshCoordinator refresh;private final CapitalInterpretationFacade agent;private final CapitalInterpretationRepository interpretations;private final MarketIntelRefreshRunRepository refreshRuns;
    public MarketIntelController(MarketIntelCapitalService capital,MarketIntelRefreshCoordinator refresh,CapitalInterpretationFacade agent,CapitalInterpretationRepository interpretations,MarketIntelRefreshRunRepository refreshRuns){this.capital=capital;this.refresh=refresh;this.agent=agent;this.interpretations=interpretations;this.refreshRuns=refreshRuns;}
    @GetMapping("/instruments") public List<Instrument> instruments(){return capital.listStockInstruments();}
    @PostMapping("/instruments/{id}/refresh") public ResponseEntity<MarketIntelRefreshRun> refresh(@PathVariable Long id){return ResponseEntity.accepted().body(refresh.requestRefresh(id));}
    @GetMapping("/refresh-runs/{id}") public MarketIntelRefreshRun refreshRun(@PathVariable Long id){return refreshRuns.findRunById(id).orElseThrow(()->new IllegalArgumentException("market intel refresh run not found: "+id));}
    @GetMapping("/instruments/{id}/capital-behavior") public MarketIntelCapitalView behavior(@PathVariable Long id,@RequestParam(defaultValue="20d")String range,@RequestParam(defaultValue="5m")String granularity){return capital.view(id,range,granularity);}
    @PostMapping("/instruments/{id}/capital-interpretations") public ResponseEntity<CapitalInterpretation> interpret(@PathVariable Long id,@RequestParam(defaultValue="false")boolean force){return ResponseEntity.accepted().body(agent.request(id,force));}
    @GetMapping("/capital-interpretations/{id}") public CapitalInterpretation interpretation(@PathVariable Long id){return interpretations.findById(id).orElseThrow(()->new IllegalArgumentException("capital interpretation not found: "+id));}
}
