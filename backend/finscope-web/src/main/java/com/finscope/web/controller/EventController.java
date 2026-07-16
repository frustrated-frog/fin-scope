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

    /**
     * 查询事件列表。
     *
     * @param themeCode 主题编码过滤条件，可为空。
     * @param status 事件状态过滤条件，可为空。
     * @param noveltyState 新颖性状态过滤条件，可为空。
     * @param dateFrom 事件起始日期过滤条件，可为空。
     * @param dateTo 事件结束日期过滤条件，可为空。
     * @return 符合过滤条件的事件聚类列表。
     */
    @GetMapping
    public List<EventCluster> list(@RequestParam(required = false) String themeCode,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String noveltyState,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return eventClusterService.list(themeCode, status, noveltyState, dateFrom, dateTo);
    }

    /**
     * 分页查询事件列表。
     *
     * @param themeCode 主题编码过滤条件，可为空。
     * @param status 事件状态过滤条件，可为空。
     * @param noveltyState 新颖性状态过滤条件，可为空。
     * @param dateFrom 事件起始日期过滤条件，可为空。
     * @param dateTo 事件结束日期过滤条件，可为空。
     * @param page 页码，从 0 开始。
     * @param pageSize 每页条数，范围为 1 到 200。
     * @return 分页后的事件聚类结果，包含记录列表和分页元数据。
     */
    @GetMapping("/paged")
    public PageResponse<EventCluster> listPaged(@RequestParam(required = false) String themeCode,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String noveltyState,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 200) {
            throw new com.finscope.common.exception.BusinessException(com.finscope.common.exception.ErrorCode.REQUEST_PARAMETER_INVALID,
                    "page must be >= 0 and pageSize must be between 1 and 200");
        }
        return eventClusterService.listPaged(themeCode, status, noveltyState, dateFrom, dateTo, page, pageSize);
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件聚类 ID。
     * @return 指定事件聚类详情。
     */
    @GetMapping("/{id}")
    public EventCluster detail(@PathVariable Long id) {
        return eventClusterService.detail(id);
    }

    /**
     * 查询事件关联文章。
     *
     * @param id 事件聚类 ID。
     * @return 该事件下的文章关联列表。
     */
    @GetMapping("/{id}/articles")
    public List<EventArticleLink> articles(@PathVariable Long id) {
        return eventClusterService.articles(id);
    }

    /**
     * 更新事件状态。
     *
     * @param id 事件聚类 ID。
     * @param request 状态更新请求，包含目标事件状态。
     * @return 更新后的事件聚类。
     */
    @PostMapping("/{id}/status")
    public EventCluster updateStatus(@PathVariable Long id, @RequestBody UpdateEventStatusRequest request) {
        return eventClusterService.updateStatus(id, request == null ? null : request.getStatus());
    }

    /**
     * 合并事件。
     *
     * @param sourceId 源事件聚类 ID。
     * @param request 合并请求，包含目标事件聚类 ID。
     * @return 合并后的目标事件聚类。
     */
    @PostMapping("/{sourceId}/merge")
    public EventCluster merge(@PathVariable Long sourceId, @RequestBody MergeEventRequest request) {
        return eventClusterService.merge(sourceId, request == null ? null : request.getTargetEventId());
    }

    /**
     * 移动事件下的文章。
     *
     * @param sourceEventId 当前文章所在的源事件聚类 ID。
     * @param articleId 待移动文章 ID。
     * @param request 移动请求，包含目标事件 ID 或是否创建新事件的标记。
     * @return 移动文章后受影响的事件聚类。
     */
    @PostMapping("/{sourceEventId}/articles/{articleId}/move")
    public EventCluster moveArticle(@PathVariable Long sourceEventId,
                                    @PathVariable Long articleId,
                                    @RequestBody MoveEventArticleRequest request) {
        return eventClusterService.moveArticle(sourceEventId, articleId,
                request == null ? null : request.getTargetEventId(),
                request == null ? null : request.getCreateNewEvent());
    }
}
