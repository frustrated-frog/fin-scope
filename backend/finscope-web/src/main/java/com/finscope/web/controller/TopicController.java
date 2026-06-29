package com.finscope.web.controller;

import com.finscope.domain.topic.Topic;
import com.finscope.domain.topic.TopicDetail;
import com.finscope.service.topic.TopicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/topics")
public class TopicController {
    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public List<Topic> list() {
        return topicService.list();
    }

    @PostMapping
    public Topic create(@RequestBody Topic topic) {
        return topicService.create(topic);
    }

    @GetMapping("/{id}")
    public TopicDetail detail(@PathVariable Long id) {
        return topicService.detail(id);
    }

    @PostMapping("/from-article/{articleId}")
    public Topic createFromArticle(@PathVariable Long articleId) {
        return topicService.createFromArticle(articleId);
    }

    @PostMapping("/from-brief/{date}")
    public List<Topic> createFromBrief(@PathVariable String date) {
        return topicService.createFromBrief(LocalDate.parse(date));
    }

    @PostMapping("/{id}/notes")
    public Topic appendNote(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return topicService.appendNote(id, payload.get("status"), payload.get("note"));
    }
}
