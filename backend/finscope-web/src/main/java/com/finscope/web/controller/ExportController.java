package com.finscope.web.controller;

import com.finscope.service.export.ExportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExportController {
    @Resource
    private ExportService exportService;

    /**
     * 导出系统数据。
     *
     * @return 导出结果 Map，包含导出状态和导出文件等服务层返回信息。
     */
    @PostMapping("/exports")
    public Map<String, Object> exportData() {
        return exportService.exportData();
    }

    /**
     * 导入系统数据。
     *
     * @return 导入能力状态；当前 MVP 返回未实现标记。
     */
    @PostMapping("/imports")
    public Map<String, Object> importData() {
        return Collections.singletonMap("status", "IMPORT_NOT_IMPLEMENTED_IN_MVP");
    }
}
