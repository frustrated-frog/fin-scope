package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.quant.backtest.ExecutableReplayService;
import com.finscope.web.request.quant.RunExecutableReplayRequest;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.quant.ExecutableReplayResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/quant/executable-replays")
public class ExecutableReplayController {
    @Resource
    private ExecutableReplayService replayService;

    @PostMapping
    public ApiResponse<ExecutableReplayResponse> replay(@RequestBody RunExecutableReplayRequest request) {
        return ApiResponses.success(ExecutableReplayResponse.of(replayService.replay(request.toInput())));
    }
}
