package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.radar.RadarRankPoint;
import com.finscope.service.radar.RadarRankHistoryService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/research-radar/events")
public class RadarRankHistoryController {
    @Resource
    private RadarRankHistoryService history;

    @GetMapping("/{id}/rank-history")
    public ApiResponse<List<RadarRankPoint>> history(@PathVariable long id) {
        return ApiResponses.success(history.history(id));
    }
}
