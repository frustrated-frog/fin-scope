package com.finscope.web.controller;

import com.finscope.domain.financials.BrokerResearchReport;
import com.finscope.service.financials.BrokerResearchService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.file.Path;

@Controller
@RequestMapping("/api/financials/research-reports")
public class BrokerResearchContentController {
    private final BrokerResearchService research;

    public BrokerResearchContentController(BrokerResearchService research) {
        this.research = research;
    }

    @GetMapping("/{id}/content")
    @ResponseBody
    public ResponseEntity<Resource> content(@PathVariable Long id) {
        BrokerResearchReport report = research.require(id);
        Path path = research.contentPath(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + report.getFileHash() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(path.toFile()));
    }
}
