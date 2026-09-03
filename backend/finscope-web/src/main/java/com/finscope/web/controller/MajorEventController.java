package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.service.majorevent.MajorEventService;
import com.finscope.web.request.CreateMajorEventRequest;
import com.finscope.web.request.UpdateMajorEventRequest;
import com.finscope.web.response.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/major-events")
public class MajorEventController {
    @Resource
    private MajorEventService service;

    /**
     * 查询重大事件列表。
     *
     * @param originType 事件来源类型过滤条件，可为空。
     * @param category 事件分类过滤条件，可为空。
     * @param from 起始日期过滤条件，可为空。
     * @param to 结束日期过滤条件，可为空。
     * @return 符合过滤条件的重大事件列表。
     */
    @GetMapping
    public ApiResponse<List<MajorEvent>> list(@RequestParam(required = false) String originType,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(required = false) LocalDate from,
                                               @RequestParam(required = false) LocalDate to) {
        return ApiResponses.success(service.list(originType, category, from, to));
    }

    /**
     * 创建重大事件。
     *
     * @param request 重大事件创建请求。
     * @return 新创建的重大事件。
     */
    @PostMapping
    public ApiResponse<MajorEvent> create(@RequestBody CreateMajorEventRequest request) {
        return ApiResponses.success(service.create(request.toCommand()));
    }

    /**
     * 更新重大事件。
     *
     * @param id 重大事件 ID。
     * @param request 重大事件更新请求，包含发生日期和备注。
     * @return 更新后的重大事件。
     */
    @PatchMapping("/{id}")
    public ApiResponse<MajorEvent> update(@PathVariable Long id, @RequestBody UpdateMajorEventRequest request) {
        return ApiResponses.success(service.update(id, request.getOccurredDate(), request.getNote()));
    }

    /**
     * 删除重大事件。
     *
     * @param id 重大事件 ID。
     * @return 204 No Content 响应，表示删除成功且无响应体。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
