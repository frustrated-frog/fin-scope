package com.finscope.web.controller;

import com.finscope.domain.topic.Topic;
import com.finscope.domain.topic.TopicDetail;
import com.finscope.service.topic.TopicService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/topics")
public class TopicController {
    @Resource
    private TopicService topicService;

    /**
     * 查询主题列表。
     *
     * @return 主题列表。
     */
    @GetMapping
    public List<Topic> list() {
        return topicService.list();
    }

    /**
     * 创建主题。
     *
     * @param topic 主题创建内容，包含主题名称、描述和相关配置。
     * @return 新创建的主题。
     */
    @PostMapping
    public Topic create(@RequestBody Topic topic) {
        return topicService.create(topic);
    }

    /**
     * 查询主题详情。
     *
     * @param id 主题 ID。
     * @return 主题详情，包含主题基础信息和关联内容。
     */
    @GetMapping("/{id}")
    public TopicDetail detail(@PathVariable Long id) {
        return topicService.detail(id);
    }

    /**
     * 删除主题。
     *
     * @param id 主题 ID。
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        topicService.delete(id);
    }

    /**
     * 从文章创建主题。
     *
     * @param articleId 文章 ID。
     * @return 基于文章内容生成或关联的主题。
     */
    @PostMapping("/from-article/{articleId}")
    public Topic createFromArticle(@PathVariable Long articleId) {
        return topicService.createFromArticle(articleId);
    }

    /**
     * 从指定日期简报创建主题。
     *
     * @param date 简报日期，格式为 yyyy-MM-dd。
     * @return 从该简报中生成或关联的主题列表。
     */
    @PostMapping("/from-brief/{date}")
    public List<Topic> createFromBrief(@PathVariable String date) {
        return topicService.createFromBrief(LocalDate.parse(date));
    }

    /**
     * 追加主题笔记。
     *
     * @param id 主题 ID。
     * @param payload 笔记请求体，包含 status 和 note 字段。
     * @return 追加笔记后的主题。
     */
    @PostMapping("/{id}/notes")
    public Topic appendNote(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return topicService.appendNote(id, payload.get("status"), payload.get("note"));
    }
}
