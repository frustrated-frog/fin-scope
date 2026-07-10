package com.finscope.web.controller;

import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.service.instrument.WatchlistItemView;
import com.finscope.service.instrument.WatchlistService;
import com.finscope.web.request.AddWatchlistItemRequest;
import com.finscope.web.response.WatchlistItemResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/watchlist")
@Slf4j
public class WatchlistController {
    @Resource
    private WatchlistService watchlistService;

    @GetMapping
    public List<WatchlistItemResponse> list() {
        List<WatchlistItemView> views = watchlistService.listWithQuotes();
        return views.stream().map(WatchlistItemResponse::of).collect(Collectors.toList());
    }

    @PostMapping
    public WatchlistItem add(@RequestBody AddWatchlistItemRequest request) {
        log.info("添加自选 code={} type={}", request.getCode(), request.getType());
        return watchlistService.add(request.getCode(), request.getType(), request.getGroupName());
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable Long id) {
        watchlistService.remove(id);
    }
}