package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.radar.ResearchRadarService;
import com.finscope.service.radar.ResearchRadarView;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/research-radar")
public class ResearchRadarController {
    private final ResearchRadarService service;
    public ResearchRadarController(ResearchRadarService service){this.service=service;}

    @GetMapping
    public ApiResponse<ResearchRadarView> radar(@RequestParam(defaultValue="ALL") String category,
                                                @RequestParam(defaultValue="false") boolean watchlistOnly,
                                                @RequestParam(defaultValue="20") int limit){
        return ApiResponses.success(service.load(category,watchlistOnly,limit));
    }

    @GetMapping("/events/{id}")
    public ApiResponse<ResearchRadarView.EventDetail> detail(@PathVariable Long id){
        return ApiResponses.success(service.detail(id));
    }

    @PostMapping("/events/{id}/interpretation")
    public ApiResponse<ResearchRadarView.InterpretationView> requestInterpretation(@PathVariable Long id){
        return ApiResponses.success(service.requestInterpretation(id));
    }
}
