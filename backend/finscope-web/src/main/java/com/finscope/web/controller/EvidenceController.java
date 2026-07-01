package com.finscope.web.controller;

import com.finscope.domain.research.EvidenceItem;
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

    @GetMapping("/events/{eventId}/evidence")
    public List<EvidenceItem> list(@PathVariable Long eventId) {
        eventClusterService.detail(eventId);
        return evidenceService.listByEventId(eventId);
    }

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

    @GetMapping("/evidence/{id}")
    public EvidenceItem detail(@PathVariable Long id) {
        return evidenceService.detail(id);
    }
}
