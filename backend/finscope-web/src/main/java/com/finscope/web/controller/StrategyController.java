package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.service.strategy.StrategyPlaybookService;
import com.finscope.service.strategy.StrategyPlaybookView;
import com.finscope.web.request.strategy.CreateStrategyPlaybookRequest;
import com.finscope.web.request.strategy.UpdateStrategyPlaybookRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {

    @Resource
    private StrategyPlaybookService strategyPlaybookService;

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
     * 查询单个策略执行手册及其规则。
     *
     * @param code 执行手册编码。
     * @return 包含来源信息和有序规则的执行手册详情。
     */
    @GetMapping("/playbooks/{code}")
    public ApiResponse<StrategyPlaybookView> playbook(@PathVariable String code) {
        return ApiResponses.success(strategyPlaybookService.get(code));
    }

    /**
     * 创建数据库驱动的策略执行手册。
     *
     * @param request 策略定义、来源信息及规则。
     * @return 新创建的策略执行手册详情。
     */
    @PostMapping("/playbooks")
    public ApiResponse<StrategyPlaybookView> createPlaybook(@RequestBody CreateStrategyPlaybookRequest request) {
        return ApiResponses.success(strategyPlaybookService.create(request.toPlaybook(), request.getRules()));
    }

    /**
     * 更新策略执行手册状态。
     *
     * @param code 执行手册编码。
     * @param request 执行手册更新请求，包含状态、备注和版本号。
     * @return 更新后的策略执行手册。
     */
    @PutMapping("/playbooks/{code}/status")
    public ApiResponse<StrategyPlaybook> updatePlaybook(@PathVariable String code, @RequestBody UpdateStrategyPlaybookRequest request) {
        return ApiResponses.success(strategyPlaybookService.update(code, request.getStatus(), request.getNote(), request.getRevision()));
    }
}
