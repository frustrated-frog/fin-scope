package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.service.instrument.WatchlistKlineService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/** 自选标的日 K 线数据资源。 */
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistKlineController {
    @Resource
    private WatchlistKlineService watchlistKlineService;

    /**
     * 查询标的日 K 线。
     *
     * @param code  六位证券代码。
     * @param limit 请求根数，默认 120，上限 250。
     * @return 按交易日升序排列的日线记录。
     */
    @GetMapping("/{code}/daily-bars")
    public ApiResponse<List<DailyBarPoint>> dailyBars(@PathVariable String code,
                                                      @RequestParam(defaultValue = "120") int limit,
                                                      @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponses.success(watchlistKlineService.dailyBars(code, limit, refresh));
    }
}
