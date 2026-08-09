package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.service.learningcard.StockLearningCardService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 提供仅用于学习的股票研究卡，不涉及交易或持仓建议。
 */
@RestController
@RequestMapping("/api/stock-learning-cards")
public class StockLearningCardController {
    @Resource private StockLearningCardService learningCardService;

    @PostMapping("/{code}/runs")
    public ApiResponse<StockLearningCardRun> start(@PathVariable String code) {
        return ApiResponses.success(learningCardService.start(code));
    }

    @GetMapping("/{code}")
    public ApiResponse<StockLearningCardService.StockLearningCardView> get(@PathVariable String code) {
        return ApiResponses.success(learningCardService.get(code));
    }
}
