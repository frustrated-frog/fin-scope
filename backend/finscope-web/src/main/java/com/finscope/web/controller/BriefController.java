package com.finscope.web.controller;

import com.finscope.domain.brief.Brief;
import com.finscope.domain.research.BriefResearchContext;
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

    /**
     * 生成当天简报。
     *
     * @return 新生成或更新后的当天简报内容。
     */
    @PostMapping("/generate")
    public Brief generate() {
        return briefService.generateToday();
    }

    /**
     * 查询简报列表。
     *
     * @return 按服务层规则排序的简报列表。
     */
    @GetMapping
    public List<Brief> list() {
        return briefService.list();
    }

    /**
     * 查询指定日期简报详情。
     *
     * @param date 简报日期，格式为 yyyy-MM-dd。
     * @return 指定日期的简报详情。
     */
    @GetMapping("/{date}")
    public Brief detail(@PathVariable String date) {
        return briefService.detail(LocalDate.parse(date));
    }

    /**
     * 查询指定日期简报的研究上下文。
     *
     * @param date 简报日期，格式为 yyyy-MM-dd。
     * @return 研究上下文，包含可用于事件研究的主题、线索和文章信息。
     */
    @GetMapping("/{date}/research-context")
    public BriefResearchContext researchContext(@PathVariable String date) {
        return briefService.researchContext(LocalDate.parse(date));
    }
}
