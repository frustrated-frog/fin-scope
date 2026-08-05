package com.finscope.web.controller;

import com.finscope.domain.financials.FinancialDocument;
import com.finscope.service.financials.FinancialDocumentService;
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
@RequestMapping("/api/financials/documents")
public class FinancialDocumentContentController {
    private final FinancialDocumentService documents;

    public FinancialDocumentContentController(FinancialDocumentService documents) {
        this.documents = documents;
    }

    /**
     * 下载财务文档 PDF 原文。
     *
     * @param id 财务文档 ID。
     * @return 以 inline 方式展示的财务文档 PDF 文件资源。
     */
    @GetMapping("/{id}/content")
    @ResponseBody
    public ResponseEntity<Resource> content(@PathVariable Long id) {
        FinancialDocument document = documents.get(id);
        Path path = documents.contentPath(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + document.getFileHash() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(path.toFile()));
    }
}
