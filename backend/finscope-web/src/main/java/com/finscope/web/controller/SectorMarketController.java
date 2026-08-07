package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.service.instrument.SectorMarketService;
import com.finscope.service.instrument.WatchlistService;
import com.finscope.web.response.FollowedSectorResponse;
import com.finscope.web.response.SectorMarketOverviewResponse;
import com.finscope.web.response.SectorMarketSearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import com.finscope.common.exception.BizErrorCode;

@RestController
@RequestMapping("/api/sector-market")
public class SectorMarketController {
    @Resource
    private SectorMarketService sectorMarketService;
    @Resource
    private WatchlistService watchlistService;

    /**
     * 查询板块行情总览。
     *
     * @param category 板块分类，默认 INDUSTRY。
     * @param limit 每类返回条数上限，默认 5。
     * @param refresh 是否强制刷新行情数据，默认 false。
     * @return 板块行情总览响应，包含领涨领跌板块和汇总指标。
     */
    @GetMapping("/overview")
    public ApiResponse<SectorMarketOverviewResponse> overview(
            @RequestParam(defaultValue = "INDUSTRY") String category,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "false") boolean refresh) {
        SectorCategory parsed = parseCategory(category, false);
        return ApiResponses.success(SectorMarketOverviewResponse.of(sectorMarketService.overview(parsed, limit, refresh)));
    }

    /**
     * 搜索板块。
     *
     * @param query 搜索关键词。
     * @param category 板块分类，默认 ALL。
     * @param limit 返回条数上限，默认 10。
     * @return 板块搜索响应，包含匹配的板块列表。
     */
    @GetMapping("/search")
    public ApiResponse<SectorMarketSearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponses.success(SectorMarketSearchResponse.of(
                sectorMarketService.search(query, parseCategory(category, true), limit)));
    }

    /**
     * 查询已关注板块列表。
     *
     * @param refresh 是否强制刷新板块行情，默认 false。
     * @return 已关注板块响应列表，包含板块基础信息和最新行情。
     */
    @GetMapping("/follows")
    public ApiResponse<List<FollowedSectorResponse>> follows(
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponses.success(watchlistService.listFollowedSectorsWithQuotes(refresh).stream()
                .map(FollowedSectorResponse::of)
                .collect(Collectors.toList()));
    }

    /**
     * 关注板块。
     *
     * @param code 板块编码。
     * @return 已关注板块响应，包含板块信息和最新行情。
     */
    @PutMapping("/follows/{code}")
    public ApiResponse<FollowedSectorResponse> follow(@PathVariable String code) {
        watchlistService.followSector(code);
        return ApiResponses.success(FollowedSectorResponse.of(watchlistService.followedSectorWithQuote(code)));
    }

    /**
     * 取消关注板块。
     *
     * @param code 板块编码。
     * @return 204 No Content 响应，表示取消关注成功且无响应体。
     */
    @DeleteMapping("/follows/{code}")
    public ResponseEntity<Void> unfollow(@PathVariable String code) {
        watchlistService.unfollowSector(code);
        return ResponseEntity.noContent().build();
    }

    private SectorCategory parseCategory(String value, boolean allowAll) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (allowAll && "ALL".equals(normalized)) return null;
        try {
            return SectorCategory.valueOf(normalized);
        } catch (IllegalArgumentException error) {
            String supported = allowAll ? "INDUSTRY、CONCEPT 或 ALL" : "INDUSTRY 或 CONCEPT";
            throw new BusinessException(BizErrorCode.SECTOR_CATEGORY_UNSUPPORTED, supported);
        }
    }
}
