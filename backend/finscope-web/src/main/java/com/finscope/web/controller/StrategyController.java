package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
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
    private StrategyHoldingService strategyHoldingService;
    @Resource
    private StrategyPlaybookService strategyPlaybookService;
    @Resource
    private StrategyStockThesisService strategyStockThesisService;
    @Resource
    private StrategyReviewService strategyReviewService;

    /**
     * 查询长期策略总览。
     *
     * @return 策略总览响应，包含持仓配置和组合层面的汇总信息。
     */
    @GetMapping("/overview")
    public ApiResponse<StrategyOverviewResponse> overview() {
        return ApiResponses.success(StrategyOverviewResponse.of(strategyHoldingService.list()));
    }

    /**
     * 新增策略持仓。
     *
     * @param request 持仓新增请求，包含标的代码、类型、角色、目标权重、当前权重和备注。
     * @return 新增后的策略持仓响应。
     */
    @PostMapping("/holdings")
    public ApiResponse<StrategyHoldingResponse> add(@RequestBody AddStrategyHoldingRequest request) {
        return ApiResponses.success(StrategyHoldingResponse.of(strategyHoldingService.add(request.getCode(), request.getType(),
                request.getRole(), request.getTargetWeight(), request.getCurrentWeight(), request.getNote())));
    }

    /**
     * 更新策略持仓。
     *
     * @param id 持仓 ID。
     * @param request 持仓更新请求，包含角色、权重、备注和版本号。
     * @return 更新后的策略持仓响应。
     */
    @PatchMapping("/holdings/{id}")
    public ApiResponse<StrategyHoldingResponse> update(@PathVariable Long id,
                                          @RequestBody UpdateStrategyHoldingRequest request) {
        return ApiResponses.success(StrategyHoldingResponse.of(strategyHoldingService.update(id, request.getRole(),
                request.getTargetWeight(), request.getCurrentWeight(), request.getNote(),
                request.getRevision())));
    }

    /**
     * 删除策略持仓。
     *
     * @param id 持仓 ID。
     * @param revision 当前版本号，用于并发更新校验。
     */
    @DeleteMapping("/holdings/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam long revision) {
        strategyHoldingService.delete(id, revision);
        return ApiResponses.success(null);
    }

    /**
     * 查询策略执行手册列表。
     *
     * @return 策略执行手册视图列表。
     */
    @GetMapping("/playbooks")
    public ApiResponse<List<StrategyPlaybookView>> playbooks() {
        return ApiResponses.success(strategyPlaybookService.list());
    }

    /**
     * 更新策略执行手册状态。
     *
     * @param code 执行手册编码。
     * @param request 执行手册更新请求，包含状态、备注和版本号。
     * @return 更新后的策略执行手册。
     */
    @PutMapping("/playbooks/{code}/status")
    public ApiResponse<StrategyPlaybook> updatePlaybook(@PathVariable String code,
                                           @RequestBody UpdateStrategyPlaybookRequest request) {
        return ApiResponses.success(strategyPlaybookService.update(code, request.getStatus(), request.getNote(), request.getRevision()));
    }

    /**
     * 查询股票研究命题列表。
     *
     * @return 股票研究命题列表。
     */
    @GetMapping("/stock-theses")
    public ApiResponse<List<StrategyStockThesis>> theses() {
        return ApiResponses.success(strategyStockThesisService.list());
    }

    /**
     * 创建股票研究命题。
     *
     * @param request 股票研究命题创建请求，包含标的代码、命题、买入条件、失效条件、观察重点和备注。
     * @return 新创建的股票研究命题。
     */
    @PostMapping("/stock-theses")
    public ApiResponse<StrategyStockThesis> createThesis(@RequestBody CreateStrategyStockThesisRequest request) {
        return ApiResponses.success(strategyStockThesisService.create(request.getCode(), request.getThesis(), request.getBuyConditions(),
                request.getInvalidationConditions(), request.getWatchFocus(), request.getNote()));
    }

    /**
     * 更新股票研究命题。
     *
     * @param id 股票研究命题 ID。
     * @param request 股票研究命题更新请求，包含阶段、命题内容、条件、备注和版本号。
     * @return 更新后的股票研究命题。
     */
    @PatchMapping("/stock-theses/{id}")
    public ApiResponse<StrategyStockThesis> updateThesis(@PathVariable Long id,
                                            @RequestBody UpdateStrategyStockThesisRequest request) {
        return ApiResponses.success(strategyStockThesisService.update(id, request.getStage(), request.getThesis(),
                request.getBuyConditions(), request.getInvalidationConditions(),
                request.getWatchFocus(), request.getNote(), request.getRevision()));
    }

    /**
     * 删除股票研究命题。
     *
     * @param id 股票研究命题 ID。
     * @param revision 当前版本号，用于并发更新校验。
     */
    @DeleteMapping("/stock-theses/{id}")
    public ApiResponse<Void> deleteThesis(@PathVariable Long id, @RequestParam long revision) {
        strategyStockThesisService.delete(id, revision);
        return ApiResponses.success(null);
    }

    /**
     * 查询策略复盘列表。
     *
     * @return 策略复盘记录列表。
     */
    @GetMapping("/reviews")
    public ApiResponse<List<StrategyReview>> reviews() {
        return ApiResponses.success(strategyReviewService.list());
    }

    /**
     * 创建策略复盘记录。
     *
     * @param request 策略复盘创建请求，包含复盘日期、事实、推理和下一步动作。
     * @return 新创建的策略复盘记录。
     */
    @PostMapping("/reviews")
    public ApiResponse<StrategyReview> createReview(@RequestBody CreateStrategyReviewRequest request) {
        return ApiResponses.success(strategyReviewService.create(request.getReviewDate(), request.getFacts(), request.getReasoning(),
                request.getNextAction()));
    }

    /**
     * 删除策略复盘记录。
     *
     * @param id 策略复盘记录 ID。
     */
    @DeleteMapping("/reviews/{id}")
    public ApiResponse<Void> deleteReview(@PathVariable Long id) {
        strategyReviewService.delete(id);
        return ApiResponses.success(null);
    }
}
