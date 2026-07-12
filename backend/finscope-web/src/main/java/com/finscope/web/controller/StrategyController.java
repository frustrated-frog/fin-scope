package com.finscope.web.controller;

import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.domain.strategy.StrategyReview;
import com.finscope.domain.strategy.StrategyStockThesis;
import com.finscope.service.strategy.StrategyHoldingService;
import com.finscope.service.strategy.StrategyPlaybookService;
import com.finscope.service.strategy.StrategyPlaybookView;
import com.finscope.service.strategy.StrategyReviewService;
import com.finscope.service.strategy.StrategyStockThesisService;
import com.finscope.web.request.strategy.*;
import com.finscope.web.response.strategy.StrategyHoldingResponse;
import com.finscope.web.response.strategy.StrategyOverviewResponse;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {
    @Resource private StrategyHoldingService holdingService;
    @Resource private StrategyPlaybookService playbookService;
    @Resource private StrategyStockThesisService thesisService;
    @Resource private StrategyReviewService reviewService;

    @GetMapping("/overview") public StrategyOverviewResponse overview(){return StrategyOverviewResponse.of(holdingService.list());}
    @PostMapping("/holdings") public StrategyHoldingResponse add(@RequestBody AddStrategyHoldingRequest r){return StrategyHoldingResponse.of(holdingService.add(r.getCode(),r.getType(),r.getRole(),r.getTargetWeight(),r.getCurrentWeight(),r.getNote()));}
    @PatchMapping("/holdings/{id}") public StrategyHoldingResponse update(@PathVariable Long id,@RequestBody UpdateStrategyHoldingRequest r){return StrategyHoldingResponse.of(holdingService.update(id,r.getRole(),r.getTargetWeight(),r.getCurrentWeight(),r.getNote(),r.getRevision()));}
    @DeleteMapping("/holdings/{id}") public void delete(@PathVariable Long id,@RequestParam long revision){holdingService.delete(id,revision);}
    @GetMapping("/playbooks") public List<StrategyPlaybookView> playbooks(){return playbookService.list();}
    @PutMapping("/playbooks/{code}/status") public StrategyPlaybook updatePlaybook(@PathVariable String code,@RequestBody UpdateStrategyPlaybookRequest r){return playbookService.update(code,r.getStatus(),r.getNote(),r.getRevision());}
    @GetMapping("/stock-theses") public List<StrategyStockThesis> theses(){return thesisService.list();}
    @PostMapping("/stock-theses") public StrategyStockThesis createThesis(@RequestBody CreateStrategyStockThesisRequest r){return thesisService.create(r.getCode(),r.getThesis(),r.getBuyConditions(),r.getInvalidationConditions(),r.getWatchFocus(),r.getNote());}
    @PatchMapping("/stock-theses/{id}") public StrategyStockThesis updateThesis(@PathVariable Long id,@RequestBody UpdateStrategyStockThesisRequest r){return thesisService.update(id,r.getStage(),r.getThesis(),r.getBuyConditions(),r.getInvalidationConditions(),r.getWatchFocus(),r.getNote(),r.getRevision());}
    @DeleteMapping("/stock-theses/{id}") public void deleteThesis(@PathVariable Long id,@RequestParam long revision){thesisService.delete(id,revision);}
    @GetMapping("/reviews") public List<StrategyReview> reviews(){return reviewService.list();}
    @PostMapping("/reviews") public StrategyReview createReview(@RequestBody CreateStrategyReviewRequest r){return reviewService.create(r.getReviewDate(),r.getFacts(),r.getReasoning(),r.getNextAction());}
    @DeleteMapping("/reviews/{id}") public void deleteReview(@PathVariable Long id){reviewService.delete(id);}
}
