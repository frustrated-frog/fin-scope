package com.finscope.web.controller;

import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.ThesisFinding;
import com.finscope.service.research.ResearchThesisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import com.finscope.web.response.ResearchThesisDetailResponse;

@RestController
@RequestMapping("/api/research/theses")
public class ResearchThesisController {
    @Resource
    private ResearchThesisService researchThesisService;

    @GetMapping
    public List<ResearchThesis> list() {
        return researchThesisService.list();
    }

    @PostMapping
    public ResearchThesis create(@RequestBody ResearchThesis thesis) {
        return researchThesisService.create(thesis);
    }

    @GetMapping("/{id}")
    public ResearchThesisDetailResponse detail(@PathVariable Long id) {
        ResearchThesisService.ThesisDetail detail = researchThesisService.detailWithResearch(id);
        return new ResearchThesisDetailResponse(detail.thesis, detail.findings, detail.runs, detail.outputs);
    }

    @PutMapping("/{id}")
    public ResearchThesis update(@PathVariable Long id, @RequestBody ResearchThesis thesis) {
        return researchThesisService.update(id, thesis);
    }

    @GetMapping("/{id}/findings")
    public List<ThesisFinding> findings(@PathVariable Long id) {
        return researchThesisService.findings(id);
    }

    @PostMapping("/{id}/findings")
    public ThesisFinding addFinding(@PathVariable Long id, @RequestBody ThesisFinding finding) {
        return researchThesisService.addFinding(id, finding);
    }
}
