package com.finscope.web.controller;

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

@RestController
@RequestMapping("/api/sector-market")
public class SectorMarketController {
    @Resource
    private SectorMarketService sectorMarketService;
    @Resource
    private WatchlistService watchlistService;

    @GetMapping("/overview")
    public SectorMarketOverviewResponse overview(
            @RequestParam(defaultValue = "INDUSTRY") String category,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "false") boolean refresh) {
        SectorCategory parsed = parseCategory(category, false);
        return SectorMarketOverviewResponse.of(sectorMarketService.overview(parsed, limit, refresh));
    }

    @GetMapping("/search")
    public SectorMarketSearchResponse search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "10") int limit) {
        return SectorMarketSearchResponse.of(
                sectorMarketService.search(query, parseCategory(category, true), limit));
    }

    @GetMapping("/follows")
    public List<FollowedSectorResponse> follows(
            @RequestParam(defaultValue = "false") boolean refresh) {
        return watchlistService.listFollowedSectorsWithQuotes(refresh).stream()
                .map(FollowedSectorResponse::of)
                .collect(Collectors.toList());
    }

    @PutMapping("/follows/{code}")
    public FollowedSectorResponse follow(@PathVariable String code) {
        watchlistService.followSector(code);
        return FollowedSectorResponse.of(watchlistService.followedSectorWithQuote(code));
    }

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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "板块分类必须是 " + supported);
        }
    }
}
