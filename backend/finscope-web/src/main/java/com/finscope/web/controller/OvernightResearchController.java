package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.quant.overnight.OvernightResearchReport;
import com.finscope.service.quant.overnight.OvernightResearchService;
import com.finscope.web.request.quant.OvernightResearchRequest;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/quant/overnight")
public class OvernightResearchController {
    @Resource
    private OvernightResearchService service;

    @PostMapping("/generate")
    public ApiResponse<OvernightResearchReport> generate(@RequestBody OvernightResearchRequest request) {
        return ApiResponses.success(service.generate(request.toInput()));
    }

    @GetMapping("/history")
    public ApiResponse<List<OvernightResearchReport>> history() {
        return ApiResponses.success(service.history(false));
    }

    @PostMapping("/settle")
    public ApiResponse<List<OvernightResearchReport>> settle() {
        return ApiResponses.success(service.history(true));
    }
}
