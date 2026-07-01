package com.finscope.web.controller;

import com.finscope.domain.research.EventArticleLink;
import com.finscope.domain.research.EventCluster;
import com.finscope.service.research.EventClusterService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{id}")
    public EventCluster detail(@PathVariable Long id) {
        return eventClusterService.detail(id);
    }

    @GetMapping("/{id}/articles")
    public List<EventArticleLink> articles(@PathVariable Long id) {
        return eventClusterService.articles(id);
    }
}
