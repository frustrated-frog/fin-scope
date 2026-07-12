package com.finscope.web.controller;

import com.finscope.domain.research.EventArticleLink;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.response.PageResponse;
import com.finscope.service.research.EventClusterService;
import com.finscope.web.request.MergeEventRequest;
import com.finscope.web.request.MoveEventArticleRequest;
import com.finscope.web.request.UpdateEventStatusRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    @Resource
    private EventClusterService eventClusterService;

    @GetMapping
    public List<EventCluster> list(@RequestParam(required = false) String themeCode,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String noveltyState,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return eventClusterService.list(themeCode, status, noveltyState, dateFrom, dateTo);
    }

    @GetMapping("/paged")
    public PageResponse<EventCluster> listPaged(@RequestParam(required = false) String themeCode,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String noveltyState,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 200) {
            throw new com.finscope.common.exception.BusinessException(com.finscope.common.exception.ErrorCode.BAD_REQUEST,
                    "page must be >= 0 and pageSize must be between 1 and 200");
        }
        return eventClusterService.listPaged(themeCode, status, noveltyState, dateFrom, dateTo, page, pageSize);
    }

    @GetMapping("/{id}")
    public EventCluster detail(@PathVariable Long id) {
        return eventClusterService.detail(id);
    }

    @GetMapping("/{id}/articles")
    public List<EventArticleLink> articles(@PathVariable Long id) {
        return eventClusterService.articles(id);
    }

    @PostMapping("/{id}/status")
    public EventCluster updateStatus(@PathVariable Long id, @RequestBody UpdateEventStatusRequest request) {
        return eventClusterService.updateStatus(id, request == null ? null : request.getStatus());
    }

    @PostMapping("/{sourceId}/merge")
    public EventCluster merge(@PathVariable Long sourceId, @RequestBody MergeEventRequest request) {
        return eventClusterService.merge(sourceId, request == null ? null : request.getTargetEventId());
    }

    @PostMapping("/{sourceEventId}/articles/{articleId}/move")
    public EventCluster moveArticle(@PathVariable Long sourceEventId,
                                    @PathVariable Long articleId,
                                    @RequestBody MoveEventArticleRequest request) {
        return eventClusterService.moveArticle(sourceEventId, articleId,
                request == null ? null : request.getTargetEventId(),
                request == null ? null : request.getCreateNewEvent());
    }
}
