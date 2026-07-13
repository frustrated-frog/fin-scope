package com.finscope.web.controller;

import com.finscope.domain.intake.FetchBatch;
import com.finscope.domain.source.Source;
import com.finscope.service.intake.IntakeService;
import com.finscope.service.intake.IntakeFetchTaskService;
import com.finscope.service.source.SourceService;
import com.finscope.service.task.TaskView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/sources")
@Slf4j
public class SourceController {
    @Resource
    private SourceService sourceService;
    @Resource
    private IntakeService intakeService;
    @Resource
    private IntakeFetchTaskService intakeFetchTaskService;

    /**
     * 查询信息源列表。
     *
     * @return 信息源列表。
     */
    @GetMapping
    public List<Source> list() {
        return sourceService.list();
    }

    /**
     * 创建信息源。
     *
     * @param source 信息源创建内容，包含名称、类型、地址和配置。
     * @return 新创建的信息源。
     */
    @PostMapping
    public Source create(@RequestBody Source source) {
        return sourceService.create(source);
    }

    /**
     * 幂等安装一组无需密钥的推荐新闻源。
     *
     * @return 本次安装或更新后的新闻源。
     */
    @PostMapping("/recommended-news")
    public List<Source> installRecommendedNewsSources() {
        return sourceService.installRecommendedNewsSources();
    }

    /**
     * 更新信息源。
     *
     * @param id 信息源 ID。
     * @param source 信息源更新内容。
     * @return 更新后的信息源。
     */
    @PutMapping("/{id}")
    public Source update(@PathVariable Long id, @RequestBody Source source) {
        return sourceService.update(id, source);
    }

    /**
     * 删除信息源。
     *
     * @param id 信息源 ID。
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        sourceService.delete(id);
    }

    /**
     * 旧版同步抓取信息源并写入摄入候选池。
     *
     * @param id 信息源 ID。
     * @return 抓取批次结果。
     * @deprecated 请使用 /api/sources/{id}/intake-fetch。
     */
    @PostMapping("/{id}/fetch")
    @Deprecated
    public FetchBatch fetch(@PathVariable Long id) {
        log.info("[已废弃] 旧抓取入口转入摄入候选池，请使用 /intake-fetch sourceId={}", id);
        return intakeService.intakeFetch(id);
    }

    /**
     * 同步抓取信息源并写入摄入候选池。
     *
     * @param id 信息源 ID。
     * @return 抓取批次结果，包含本次抓取和候选生成情况。
     */
    @PostMapping("/{id}/intake-fetch")
    public FetchBatch intakeFetch(@PathVariable Long id) {
        log.info("开始摄入信息源 sourceId={}", id);
        return intakeService.intakeFetch(id);
    }

    /**
     * 异步抓取信息源并写入摄入候选池。
     *
     * @param id 信息源 ID。
     * @return 已创建的异步任务视图，用于查询抓取进度。
     */
    @PostMapping("/{id}/intake-fetch-async")
    public TaskView intakeFetchAsync(@PathVariable Long id) {
        log.info("提交信息源异步摄入 sourceId={}", id);
        return intakeFetchTaskService.submit(id);
    }
}
