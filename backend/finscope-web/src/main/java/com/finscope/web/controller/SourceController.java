package com.finscope.web.controller;

import com.finscope.domain.intake.FetchBatch;
import com.finscope.domain.source.Source;
import com.finscope.service.intake.IntakeService;
import com.finscope.service.source.SourceService;
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

    @GetMapping
    public List<Source> list() {
        return sourceService.list();
    }

    @PostMapping
    public Source create(@RequestBody Source source) {
        return sourceService.create(source);
    }

    @PutMapping("/{id}")
    public Source update(@PathVariable Long id, @RequestBody Source source) {
        return sourceService.update(id, source);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        sourceService.delete(id);
    }

    @PostMapping("/{id}/fetch")
    public FetchBatch fetch(@PathVariable Long id) {
        log.info("旧抓取入口转入摄入候选池 sourceId={}", id);
        return intakeService.intakeFetch(id);
    }

    @PostMapping("/{id}/intake-fetch")
    public FetchBatch intakeFetch(@PathVariable Long id) {
        log.info("开始摄入信息源 sourceId={}", id);
        return intakeService.intakeFetch(id);
    }
}
