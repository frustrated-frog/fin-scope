package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.cache.ViewRevision;
import com.finscope.service.cache.ViewRevisionService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.sse.ViewRevisionSseRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 页面生产版本的 SSE 通知与断线后的轻量 reconciliation 接口。 */
@RestController
@RequestMapping("/api/view-revisions")
public class ViewRevisionController {
    private static final List<String> DEFAULT_SCOPES = Arrays.asList("news", "radar", "dashboard");
    private final ViewRevisionService revisions;
    private final ViewRevisionSseRegistry stream;

    public ViewRevisionController(ViewRevisionService revisions, ViewRevisionSseRegistry stream) {
        this.revisions = revisions;
        this.stream = stream;
    }

    @GetMapping
    public ApiResponse<List<ViewRevision>> current(@RequestParam(required = false) String scopes) {
        return ApiResponses.success(revisions.current(parseScopes(scopes)));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return stream.subscribe();
    }

    private List<String> parseScopes(String scopes) {
        if (scopes == null || scopes.trim().isEmpty()) return DEFAULT_SCOPES;
        List<String> values = new ArrayList<String>();
        for (String value : scopes.split(",")) {
            if (value != null && !value.trim().isEmpty()) values.add(value.trim());
        }
        return values.isEmpty() ? DEFAULT_SCOPES : values;
    }
}
