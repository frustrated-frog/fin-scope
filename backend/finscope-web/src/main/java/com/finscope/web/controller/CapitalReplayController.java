package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.marketintel.CapitalReplayService;
import com.finscope.service.marketintel.CapitalReplayView;
import com.finscope.web.response.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapitalReplayController {
    @Autowired
    private CapitalReplayService replay;

    @GetMapping("/api/market-intel/instruments/{id}/capital-replay")
    public ApiResponse<CapitalReplayView> replay(@PathVariable Long id) {
        return ApiResponses.success(replay.view(id));
    }
}
