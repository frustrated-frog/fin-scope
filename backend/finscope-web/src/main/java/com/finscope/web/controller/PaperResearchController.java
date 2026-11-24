package com.finscope.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.finscope.common.api.ApiResponse;
import com.finscope.domain.quant.paper.PaperResearchInput;
import com.finscope.service.quant.paper.PaperResearchService;
import com.finscope.web.response.ApiResponses;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/quant/paper/versions")
public class PaperResearchController {
    @Resource
    private PaperResearchService service;

    @GetMapping
    public ApiResponse<JsonNode> versions() {
        return ApiResponses.success(service.versions());
    }

    @PostMapping
    public ApiResponse<JsonNode> generate(@RequestBody PaperResearchInput input) {
        return ApiResponses.success(service.generate(input));
    }

    @GetMapping("/{id}")
    public ApiResponse<JsonNode> detail(@PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ApiResponses.success(service.detail(id, day));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<JsonNode> review(@PathVariable long id) {
        return ApiResponses.success(service.review(id));
    }

    @PostMapping("/{id}/deploy")
    public ApiResponse<JsonNode> deploy(@PathVariable long id) {
        return ApiResponses.success(service.deploy(id));
    }
}
