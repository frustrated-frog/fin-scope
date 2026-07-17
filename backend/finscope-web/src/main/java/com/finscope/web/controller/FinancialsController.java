package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.financials.FinancialDocument;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.domain.instrument.Instrument;
import com.finscope.service.financials.FinancialDocumentService;
import com.finscope.service.financials.FinancialQueryService;
import com.finscope.service.financials.FinancialRefreshService;
import com.finscope.service.financials.FinancialInterpretationFacade;
import com.finscope.web.request.financials.FinancialInterpretationRequest;
import com.finscope.web.request.financials.FinancialRefreshRequest;
import com.finscope.web.response.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/financials")
@Validated
public class FinancialsController {
    private final FinancialQueryService query;
    private final FinancialRefreshService refresh;
    private final FinancialDocumentService documents;
    private final FinancialInterpretationFacade interpretations;

    public FinancialsController(FinancialQueryService query,
                                FinancialRefreshService refresh,
                                FinancialDocumentService documents,
                                FinancialInterpretationFacade interpretations) {
        this.query = query;
        this.refresh = refresh;
        this.documents = documents;
        this.interpretations = interpretations;
    }

    @GetMapping("/instruments")
    public ApiResponse<List<Instrument>> instruments() {
        return ApiResponses.success(query.listInstruments());
    }

    @GetMapping("/instruments/{id}/reports")
    public ApiResponse<List<FinancialReport>> reports(@PathVariable Long id) {
        return ApiResponses.success(query.listReports(id));
    }

    @GetMapping("/reports/{id}")
    public ApiResponse<FinancialReportView> report(@PathVariable Long id) {
        return ApiResponses.success(query.view(id));
    }

    @PostMapping("/instruments/{id}/refresh")
    public ResponseEntity<ApiResponse<FinancialReportView>> refresh(
            @PathVariable Long id,
            @Valid @RequestBody FinancialRefreshRequest request) {
        FinancialReportView result = refresh.refresh(
                id, request.getPeriodEnd(), request.getReportType());
        return ResponseEntity.accepted().body(ApiResponses.success(result));
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FinancialDocument> upload(
            @RequestParam Long instrumentId,
            @RequestParam(required = false) Long reportId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponses.success(documents.store(
                instrumentId, reportId, file.getOriginalFilename(),
                file.getInputStream(), file.getSize()));
    }

    @GetMapping("/reports/{id}/documents")
    public ApiResponse<List<FinancialDocument>> documents(@PathVariable Long id) {
        return ApiResponses.success(documents.listByReport(id));
    }

    @PostMapping("/reports/{id}/interpretations")
    public ResponseEntity<ApiResponse<FinancialInterpretation>> interpret(
            @PathVariable Long id,
            @RequestBody(required = false) FinancialInterpretationRequest request) {
        FinancialInterpretation result = interpretations.request(
                id, request != null && request.isForce());
        return ResponseEntity.accepted().body(ApiResponses.success(result));
    }

    @GetMapping("/reports/{id}/interpretations/latest")
    public ApiResponse<FinancialInterpretation> latestInterpretation(@PathVariable Long id) {
        return ApiResponses.success(interpretations.latest(id));
    }

    @GetMapping("/reports/{id}/interpretations")
    public ApiResponse<List<FinancialInterpretation>> interpretationHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.success(interpretations.history(id, limit));
    }

    @GetMapping("/interpretations/{id}")
    public ApiResponse<FinancialInterpretation> interpretation(@PathVariable Long id) {
        return ApiResponses.success(interpretations.get(id));
    }

    @GetMapping("/interpretations/{id}/evidence")
    public ApiResponse<List<FinancialEvidence>> interpretationEvidence(@PathVariable Long id) {
        return ApiResponses.success(interpretations.evidence(id));
    }

}
