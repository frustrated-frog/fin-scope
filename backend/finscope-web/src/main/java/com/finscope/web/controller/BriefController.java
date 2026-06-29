package com.finscope.web.controller;

import com.finscope.domain.brief.Brief;
import com.finscope.domain.research.BriefResearchContext;
import com.finscope.service.brief.BriefService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/briefs")
public class BriefController {
    private final BriefService briefService;

    public BriefController(BriefService briefService) {
        this.briefService = briefService;
    }

    @PostMapping("/generate")
    public Brief generate() {
        return briefService.generateToday();
    }

    @GetMapping
    public List<Brief> list() {
        return briefService.list();
    }

    @GetMapping("/{date}")
    public Brief detail(@PathVariable String date) {
        return briefService.detail(LocalDate.parse(date));
    }

    @GetMapping("/{date}/research-context")
    public BriefResearchContext researchContext(@PathVariable String date) {
        return briefService.researchContext(LocalDate.parse(date));
    }
}
