package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.globalmarket.GlobalMarketService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/global-markets")
public class GlobalMarketController {
    @Resource
    private GlobalMarketService service;

    @GetMapping("/{id}")
    public ApiResponse<GlobalMarketService.Result> market(@PathVariable String id) {
        return ApiResponses.success(service.market(id));
    }
}
