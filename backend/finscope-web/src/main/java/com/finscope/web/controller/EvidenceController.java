package com.finscope.web.controller;

import com.finscope.common.exception.BusinessException;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.response.PageResponse;
import com.finscope.service.research.EventClusterService;
import com.finscope.service.research.EvidenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api")
public class EvidenceController {

    @Resource
    private EventClusterService eventClusterService;
    @Resource
    private EvidenceService evidenceService;

    /**
     * 查询指定事件的证据列表。
     *
     * @param eventId 事件聚类 ID。
     * @return 该事件下的证据条目列表。
     */
    @GetMapping("/events/{eventId}/evidence")
    public List<EvidenceItem> list(@PathVariable Long eventId) {
        eventClusterService.detail(eventId);
        return evidenceService.listByEventId(eventId);
    }

    /**
     * 查询证据列表。
     *
     * @param eventId 事件聚类 ID 过滤条件，可为空。
     * @param sourceTier 来源层级过滤条件，可为空。
     * @param evidenceType 证据类型过滤条件，可为空。
     * @param minConfidence 最低置信度过滤条件，可为空。
     * @return 符合过滤条件的证据条目列表。
     */
    @GetMapping("/evidence")
    public List<EvidenceItem> listAll(@RequestParam(required = false) Long eventId,
                                      @RequestParam(required = false) String sourceTier,
                                      @RequestParam(required = false) String evidenceType,
                                      @RequestParam(required = false) Integer minConfidence) {
        if (eventId != null) {
            eventClusterService.detail(eventId);
        }
        return evidenceService.listAll(eventId, sourceTier, evidenceType, minConfidence);
    }

    /**
     * 分页查询证据列表。
     *
     * @param eventId 事件聚类 ID 过滤条件，可为空。
     * @param sourceTier 来源层级过滤条件，可为空。
     * @param evidenceType 证据类型过滤条件，可为空。
     * @param minConfidence 最低置信度过滤条件，可为空。
     * @param page 页码，从 0 开始。
     * @param pageSize 每页条数，范围为 1 到 200。
     * @return 分页后的证据条目结果，包含记录列表和分页元数据。
     */
    @GetMapping("/evidence/paged")
    public PageResponse<EvidenceItem> listPaged(@RequestParam(required = false) Long eventId,
                                                @RequestParam(required = false) String sourceTier,
                                                @RequestParam(required = false) String evidenceType,
                                                @RequestParam(required = false) Integer minConfidence,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int pageSize) {
        if (eventId != null) {
            eventClusterService.detail(eventId);
        }
        if (page < 0 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(com.finscope.common.exception.ErrorCode.REQUEST_PARAMETER_INVALID,
                    "页码不能小于 0，且每页数量必须在 1 到 200 之间");
        }
        return evidenceService.listPaged(eventId, sourceTier, evidenceType, minConfidence, page, pageSize);
    }

    /**
     * 查询证据详情。
     *
     * @param id 证据条目 ID。
     * @return 指定证据条目详情。
     */
    @GetMapping("/evidence/{id}")
    public EvidenceItem detail(@PathVariable Long id) {
        return evidenceService.detail(id);
    }
}
