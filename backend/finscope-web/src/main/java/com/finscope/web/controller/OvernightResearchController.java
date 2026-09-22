package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.quant.overnight.OvernightCapturePlan;
import com.finscope.domain.quant.overnight.OvernightCaptureState;
import com.finscope.domain.quant.overnight.OvernightResearchReport;
import com.finscope.domain.quant.overnight.OvernightValidationSummary;
import com.finscope.service.quant.overnight.OvernightResearchService;
import com.finscope.web.request.quant.OvernightCaptureRequest;
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

    @GetMapping("/capture")
    public ApiResponse<OvernightCaptureState> captureState() {
        return ApiResponses.success(service.captureState());
    }

    @PostMapping("/capture")
    public ApiResponse<OvernightCapturePlan> configureCapture(@RequestBody OvernightCaptureRequest request) {
        return ApiResponses.success(service.configureCapture(request.toPlan()));
    }

    @GetMapping("/validation")
    public ApiResponse<OvernightValidationSummary> validation() {
        return ApiResponses.success(service.validation());
    }

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
