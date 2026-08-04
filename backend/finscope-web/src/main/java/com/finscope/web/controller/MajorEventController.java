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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/major-events")
public class MajorEventController {
    private final MajorEventService service;

    public MajorEventController(MajorEventService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<MajorEvent>> list(@RequestParam(required = false) String originType,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(required = false) LocalDate from,
                                               @RequestParam(required = false) LocalDate to) {
        return ApiResponses.success(service.list(originType, category, from, to));
    }

    @PostMapping
    public ApiResponse<MajorEvent> create(@RequestBody CreateMajorEventRequest request) {
        return ApiResponses.success(service.create(request.toCommand()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<MajorEvent> update(@PathVariable Long id, @RequestBody UpdateMajorEventRequest request) {
        return ApiResponses.success(service.update(id, request.getOccurredDate(), request.getNote()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
