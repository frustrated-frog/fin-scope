package com.finscope.web.controller;

import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.domain.strategy.StrategyReview;
import com.finscope.domain.strategy.StrategyStockThesis;
import com.finscope.service.strategy.StrategyHoldingService;
import com.finscope.service.strategy.StrategyPlaybookService;
import com.finscope.service.strategy.StrategyPlaybookView;
import com.finscope.service.strategy.StrategyReviewService;
import com.finscope.service.strategy.StrategyStockThesisService;
import com.finscope.web.request.strategy.AddStrategyHoldingRequest;
import com.finscope.web.request.strategy.CreateStrategyReviewRequest;
import com.finscope.web.request.strategy.CreateStrategyStockThesisRequest;
import com.finscope.web.request.strategy.UpdateStrategyHoldingRequest;
import com.finscope.web.request.strategy.UpdateStrategyPlaybookRequest;
import com.finscope.web.request.strategy.UpdateStrategyStockThesisRequest;
import com.finscope.web.response.strategy.StrategyHoldingResponse;
import com.finscope.web.response.strategy.StrategyOverviewResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {
    @Resource
    private StrategyHoldingService holdingService;
    @Resource
    private StrategyPlaybookService playbookService;
    @Resource
    private StrategyStockThesisService thesisService;
    @Resource
    private StrategyReviewService reviewService;

    @GetMapping("/overview")
    public StrategyOverviewResponse overview() {
        return StrategyOverviewResponse.of(holdingService.list());
    }

    @PostMapping("/holdings")
    public StrategyHoldingResponse add(@RequestBody AddStrategyHoldingRequest request) {
        return StrategyHoldingResponse.of(holdingService.add(request.getCode(), request.getType(),
                request.getRole(), request.getTargetWeight(), request.getCurrentWeight(), request.getNote()));
    }

    @PatchMapping("/holdings/{id}")
    public StrategyHoldingResponse update(@PathVariable Long id,
                                          @RequestBody UpdateStrategyHoldingRequest request) {
        return StrategyHoldingResponse.of(holdingService.update(id, request.getRole(),
                request.getTargetWeight(), request.getCurrentWeight(), request.getNote(),
                request.getRevision()));
    }

    @DeleteMapping("/holdings/{id}")
    public void delete(@PathVariable Long id, @RequestParam long revision) {
        holdingService.delete(id, revision);
    }

    @GetMapping("/playbooks")
    public List<StrategyPlaybookView> playbooks() {
        return playbookService.list();
    }

    @PutMapping("/playbooks/{code}/status")
    public StrategyPlaybook updatePlaybook(@PathVariable String code,
                                           @RequestBody UpdateStrategyPlaybookRequest request) {
        return playbookService.update(code, request.getStatus(), request.getNote(), request.getRevision());
    }

    @GetMapping("/stock-theses")
    public List<StrategyStockThesis> theses() {
        return thesisService.list();
    }

    @PostMapping("/stock-theses")
    public StrategyStockThesis createThesis(@RequestBody CreateStrategyStockThesisRequest request) {
        return thesisService.create(request.getCode(), request.getThesis(), request.getBuyConditions(),
                request.getInvalidationConditions(), request.getWatchFocus(), request.getNote());
    }

    @PatchMapping("/stock-theses/{id}")
    public StrategyStockThesis updateThesis(@PathVariable Long id,
                                            @RequestBody UpdateStrategyStockThesisRequest request) {
        return thesisService.update(id, request.getStage(), request.getThesis(),
                request.getBuyConditions(), request.getInvalidationConditions(),
                request.getWatchFocus(), request.getNote(), request.getRevision());
    }

    @DeleteMapping("/stock-theses/{id}")
    public void deleteThesis(@PathVariable Long id, @RequestParam long revision) {
        thesisService.delete(id, revision);
    }

    @GetMapping("/reviews")
    public List<StrategyReview> reviews() {
        return reviewService.list();
    }

    @PostMapping("/reviews")
    public StrategyReview createReview(@RequestBody CreateStrategyReviewRequest request) {
        return reviewService.create(request.getReviewDate(), request.getFacts(), request.getReasoning(),
                request.getNextAction());
    }

    @DeleteMapping("/reviews/{id}")
    public void deleteReview(@PathVariable Long id) {
        reviewService.delete(id);
    }
}
