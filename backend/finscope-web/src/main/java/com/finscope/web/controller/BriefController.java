package com.finscope.web.controller;

import com.finscope.domain.brief.Brief;
import com.finscope.service.brief.BriefService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/briefs")
public class BriefController {

    @Resource
    private BriefService briefService;

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
}
