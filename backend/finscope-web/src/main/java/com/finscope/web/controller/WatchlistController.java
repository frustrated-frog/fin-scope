package com.finscope.web.controller;

import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.service.instrument.WatchlistItemView;
import com.finscope.service.instrument.WatchlistService;
import com.finscope.web.request.AddWatchlistItemRequest;
import com.finscope.web.request.UpdateWatchlistGroupRequest;
import com.finscope.web.response.WatchlistItemResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/watchlist")
@Slf4j
public class WatchlistController {
    @Resource
    private WatchlistService watchlistService;

    /**
     * 查询自选列表。
     *
     * @param refresh 是否强制刷新自选标的行情。
     * @return 自选响应列表，包含标的基础信息、分组和最新行情。
     */
    @GetMapping
    public List<WatchlistItemResponse> list(@RequestParam(defaultValue = "false") boolean refresh) {
        List<WatchlistItemView> views = refresh
                ? watchlistService.listWithQuotes(true)
                : watchlistService.listWithQuotes();
        return views.stream().map(WatchlistItemResponse::of).collect(Collectors.toList());
    }

    /**
     * 添加自选标的。
     *
     * @param request 自选新增请求，包含标的代码、类型和分组名称。
     * @return 新添加的自选标的。
     */
    @PostMapping
    public WatchlistItem add(@RequestBody AddWatchlistItemRequest request) {
        log.info("添加自选 code={} type={}", request.getCode(), request.getType());
        return watchlistService.add(request.getCode(), request.getType(), request.getGroupName());
    }

    /**
     * 移除自选标的。
     *
     * @param id 自选记录 ID。
     * @return 204 No Content 响应，表示移除成功且无响应体。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        watchlistService.remove(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新自选标的分组。
     *
     * @param id 自选记录 ID。
     * @param request 分组更新请求，包含目标分组名称。
     * @return 204 No Content 响应，表示更新成功且无响应体。
     */
    @PatchMapping("/{id}/group")
    public ResponseEntity<Void> updateGroup(@PathVariable Long id, @RequestBody UpdateWatchlistGroupRequest request) {
        log.info("更新自选分组 id={} group={}", id, request.getGroupName());
        watchlistService.updateGroup(id, request.getGroupName());
        return ResponseEntity.noContent().build();
    }
}
