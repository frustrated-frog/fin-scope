package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.financials.FinancialDocument;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.domain.financials.BrokerResearchReport;
import com.finscope.domain.financials.BrokerResearchReportView;
import com.finscope.domain.financials.BrokerResearchSyncResult;
import com.finscope.domain.instrument.Instrument;
import com.finscope.service.financials.FinancialDocumentService;
import com.finscope.service.financials.FinancialQueryService;
import com.finscope.service.financials.FinancialRefreshService;
import com.finscope.service.financials.FinancialInterpretationFacade;
import com.finscope.service.financials.BrokerResearchService;
import com.finscope.service.financials.BrokerResearchSyncService;
import com.finscope.web.request.financials.BrokerResearchImportRequest;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.time.LocalDate;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/financials")
@Validated
public class FinancialsController {

    @Resource
    private FinancialQueryService query;
    @Resource
    private FinancialRefreshService refresh;
    @Resource
    private FinancialDocumentService documents;
    @Resource
    private FinancialInterpretationFacade interpretations;
    @Resource
    private BrokerResearchService brokerResearch;
    @Resource
    private BrokerResearchSyncService brokerResearchSync;

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
        FinancialReportView result = refresh.refresh(id, request.getPeriodEnd(), request.getReportType());
        return ResponseEntity.accepted().body(ApiResponses.success(result));
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FinancialDocument> upload(
            @RequestParam Long instrumentId,
            @RequestParam(required = false) Long reportId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponses.success(documents.store(instrumentId, reportId, file.getOriginalFilename(), file.getInputStream(), file.getSize()));
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

    @GetMapping("/instruments/{id}/research-reports")
    public ApiResponse<List<BrokerResearchReport>> researchReports(@PathVariable Long id) {
        return ApiResponses.success(brokerResearch.list(id));
    }

    @GetMapping("/instruments/{id}/research-reports/candidates")
    public ApiResponse<BrokerResearchSyncResult> researchReportCandidates(
            @PathVariable Long id) {
        return ApiResponses.success(brokerResearchSync.candidates(id));
    }

    @PostMapping("/instruments/{id}/research-reports/import")
    public ApiResponse<BrokerResearchReportView> importResearchReport(
            @PathVariable Long id,
            @Valid @RequestBody BrokerResearchImportRequest request) {
        return ApiResponses.success(brokerResearchSync.importCandidate(
                id, request.getFinancialReportId(),
                request.getSourceCode(), request.getExternalId()));
    }

    @GetMapping("/research-reports/{id}")
    public ApiResponse<BrokerResearchReportView> researchReport(
            @PathVariable Long id,
            @RequestParam(required = false) Long financialReportId) {
        return ApiResponses.success(brokerResearch.get(id, financialReportId));
    }

    @PostMapping(value = "/research-reports/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BrokerResearchReportView> uploadResearchReport(
            @RequestParam Long instrumentId,
            @RequestParam(required = false) Long financialReportId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String analyst,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishedDate,
            @RequestParam(required = false) String rating,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) BigDecimal targetPrice,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponses.success(brokerResearch.upload(
                instrumentId, financialReportId, title, institution, analyst, publishedDate,
                rating, reportType, targetPrice, file.getOriginalFilename(),
                file.getInputStream(), file.getSize()));
    }

    @PostMapping("/research-reports/{id}/reanalyze")
    public ApiResponse<BrokerResearchReportView> reanalyzeResearchReport(
            @PathVariable Long id,
            @RequestParam(required = false) Long financialReportId) {
        return ApiResponses.success(brokerResearch.reanalyze(id, financialReportId));
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
