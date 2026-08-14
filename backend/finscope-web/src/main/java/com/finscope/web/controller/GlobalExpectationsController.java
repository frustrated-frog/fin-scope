package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import com.finscope.service.globalexpectations.GlobalExpectationsService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/global-expectations")
public class GlobalExpectationsController {
    @Resource
    private GlobalExpectationsService globalExpectationsService;

    @GetMapping
    public ApiResponse<List<GlobalExpectationItem>> list() {
        return ApiResponses.success(globalExpectationsService.list());
    }
}
