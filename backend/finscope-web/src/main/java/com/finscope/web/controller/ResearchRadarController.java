package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.radar.ResearchRadarService;
import com.finscope.service.radar.ResearchRadarView;
import com.finscope.service.radar.RadarEventWorkspaceService;
import com.finscope.service.radar.RadarResearchLinkService;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.web.request.UpdateRadarEventStateRequest;
import com.finscope.web.request.RadarResearchLinkRequest;
import com.finscope.web.response.ApiResponses;
import com.finscope.service.cache.ViewSnapshotCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/research-radar")
public class ResearchRadarController {

    @Resource
    private ResearchRadarService service;
    @Resource
    private RadarEventWorkspaceService workspace;
    @Resource
    private RadarResearchLinkService researchLinks;
    @Resource
    private ObjectMapper mapper;
    @Resource
    private ViewSnapshotCacheService snapshots;

    private final String ALL = "ALL";

    /**
     * 查询研究雷达视图。
     *
     * @param category 事件分类过滤条件，默认 ALL。
     * @param watchlistOnly 是否仅返回自选标的相关事件，默认 false。
     * @param limit 返回条数上限，默认 20。
     * @param state 事件状态过滤条件，默认 ALL。
     * @param refresh 是否请求后台生产一批新的雷达快照，默认 true；请求本身只读取最近已完成快照。
     * @return 研究雷达视图，包含事件流、统计信息和生产批次状态。
     */
    @GetMapping
    public ApiResponse<JsonNode> radar(@RequestParam(defaultValue="ALL") String category,
                                                @RequestParam(defaultValue="false") boolean watchlistOnly,
                                                @RequestParam(defaultValue="20") int limit,
                                                @RequestParam(defaultValue="ALL") String state,
                                                @RequestParam(defaultValue="false") boolean refresh){
        if (refresh) {
            service.requestRefresh();
        }
        String normalizedCategory = category == null ? ALL : category.trim().toUpperCase(java.util.Locale.ROOT);
        String normalizedState = state == null ? ALL : state.trim().toUpperCase(java.util.Locale.ROOT);
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        String variant = "category=" + normalizedCategory + "&watchlist=" + watchlistOnly
                + "&limit=" + normalizedLimit + "&state=" + normalizedState;
        JsonNode data = snapshots.read("radar", variant).orElseGet(() -> mapper.valueToTree(
                service.loadStored(normalizedCategory, watchlistOnly, normalizedLimit, normalizedState)));
        return ApiResponses.success(data);
    }

    /** 手动请求一轮雷达生产；不会把页面请求变成同步抓源。 */
    @PostMapping("/refresh")
    public ApiResponse<Boolean> refresh() {
        return ApiResponses.success(service.requestRefresh());
    }

    /** 直接查询当前 36 小时缓存窗口内的临时关注清单。 */
    @GetMapping("/followed")
    public ApiResponse<ResearchRadarView> followed(@RequestParam(defaultValue="20") int limit) {
        return ApiResponses.success(service.loadFollowed(limit));
    }

    /**
     * 查询雷达事件详情。
     *
     * @param id 雷达事件 ID。
     * @return 雷达事件详情。
     */
    @GetMapping("/events/{id}")
    public ApiResponse<ResearchRadarView.EventDetail> detail(@PathVariable Long id){
        return ApiResponses.success(service.detail(id));
    }

    /**
     * 请求生成雷达事件解读。
     *
     * @param id 雷达事件 ID。
     * @return 雷达事件解读视图。
     */
    @PostMapping("/events/{id}/interpretation")
    public ApiResponse<ResearchRadarView.InterpretationView> requestInterpretation(@PathVariable Long id){
        return ApiResponses.success(service.requestInterpretation(id));
    }

    /**
     * 更新雷达事件工作台状态。
     *
     * @param id 雷达事件 ID。
     * @param request 状态更新请求，包含是否已读、是否关注和处置结论。
     * @return 更新后的事件工作台状态。
     */
    @PatchMapping("/events/{id}/state")
    public ApiResponse<RadarEventWorkspace.State> updateState(@PathVariable Long id,
                                                              @RequestBody UpdateRadarEventStateRequest request) {
        return ApiResponses.success(workspace.updateState(id, request.getRead(), request.getFollowed(), request.getDisposition()));
    }

    /**
     * 将雷达事件关联到研究运行。
     *
     * @param id 雷达事件 ID。
     * @param runId 研究运行 ID。
     * @param request 关联请求，包含研究问题，可为空。
     * @return 新建立的研究关联。
     */
    @PostMapping("/events/{id}/research-links/{runId}")
    public ApiResponse<RadarEventWorkspace.ResearchLink> linkResearch(@PathVariable Long id,@PathVariable Long runId,
                                                                      @RequestBody(required=false) RadarResearchLinkRequest request){
        return ApiResponses.success(researchLinks.link(id,runId,request==null?null:request.getQuestion()));
    }

    /**
     * 查询雷达通知中心。
     *
     * @param limit 返回条数上限，默认 30。
     * @return 通知中心视图，包含未读通知和汇总信息。
     */
    @GetMapping("/notifications")
    public ApiResponse<RadarEventWorkspaceService.NotificationCenter> notifications(@RequestParam(defaultValue="30") int limit){
        return ApiResponses.success(workspace.notifications(limit));
    }

    /**
     * 标记单条雷达通知为已读。
     *
     * @param id 通知 ID。
     * @return 204 No Content 响应，表示标记成功且无响应体。
     */
    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> readNotification(@PathVariable Long id){workspace.readNotification(id);return ResponseEntity.noContent().build();}

    /**
     * 标记全部雷达通知为已读。
     *
     * @return 204 No Content 响应，表示标记成功且无响应体。
     */
    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> readAllNotifications(){workspace.readAllNotifications();return ResponseEntity.noContent().build();}
}
