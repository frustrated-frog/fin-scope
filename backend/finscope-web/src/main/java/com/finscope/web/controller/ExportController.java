package com.finscope.web.controller;

import com.finscope.service.export.ExportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExportController {
    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/exports")
    public Map<String, Object> exportData() {
        return exportService.exportData();
    }

    @PostMapping("/imports")
    public Map<String, Object> importData() {
        return Collections.singletonMap("status", "IMPORT_NOT_IMPLEMENTED_IN_MVP");
    }
}
