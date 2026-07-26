package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.service.research.mission.ResearchToolRegistry;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/research")
public class ResearchCapabilityController {
    private final ResearchToolRegistry toolRegistry;

    public ResearchCapabilityController(ResearchToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping("/tools")
    public ApiResponse<List<ResearchToolDescriptor>> tools() {
        return ApiResponses.success(toolRegistry.list());
    }
}
