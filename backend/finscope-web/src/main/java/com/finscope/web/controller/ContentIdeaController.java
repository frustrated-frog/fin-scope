package com.finscope.web.controller;

import com.finscope.domain.research.ContentIdea;
import com.finscope.service.research.ContentIdeaService;
import com.finscope.web.request.UpdateContentIdeaStatusRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/content-ideas")
public class ContentIdeaController {
    @Resource
    private ContentIdeaService contentIdeaService;

    @GetMapping
    public List<ContentIdea> list() {
        return contentIdeaService.list();
    }

    @GetMapping("/{id}")
    public ContentIdea detail(@PathVariable Long id) {
        return contentIdeaService.detail(id);
    }

    @PostMapping("/{id}/status")
    public ContentIdea updateStatus(@PathVariable Long id, @RequestBody UpdateContentIdeaStatusRequest request) {
        return contentIdeaService.updateStatus(id, request.getStatus());
    }
}
