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

    /**
     * 查询已录入的标的列表。
     *
     * @return 财务分析可用的标的列表。
     */
    @GetMapping("/instruments")
    public ApiResponse<List<Instrument>> instruments() {
        return ApiResponses.success(query.listInstruments());
    }

    /**
     * 查询指定标的的财报列表。
     *
     * @param id 标的 ID。
     * @return 该标的下的财报列表。
     */
    @GetMapping("/instruments/{id}/reports")
    public ApiResponse<List<FinancialReport>> reports(@PathVariable Long id) {
        return ApiResponses.success(query.listReports(id));
    }

    /**
     * 查询财报详情视图。
     *
     * @param id 财报 ID。
     * @return 财报详情视图，包含报表结构和展示字段。
     */
    @GetMapping("/reports/{id}")
    public ApiResponse<FinancialReportView> report(@PathVariable Long id) {
        return ApiResponses.success(query.view(id));
    }

    /**
     * 刷新指定标的的财报数据。
     *
     * @param id 标的 ID。
     * @param request 财报刷新请求，包含报告期和报告类型。
     * @return 202 Accepted 响应，响应体为刷新后的财报详情视图。
     */
    @PostMapping("/instruments/{id}/refresh")
    public ResponseEntity<ApiResponse<FinancialReportView>> refresh(
            @PathVariable Long id,
            @Valid @RequestBody FinancialRefreshRequest request) {
        FinancialReportView result = refresh.refresh(id, request.getPeriodEnd(), request.getReportType());
        return ResponseEntity.accepted().body(ApiResponses.success(result));
    }

    /**
     * 上传财务文档。
     *
     * @param instrumentId 标的 ID。
     * @param reportId 关联财报 ID，可为空。
     * @param file 上传的财务文档文件。
     * @return 已保存的财务文档记录。
     * @throws IOException 读取上传文件流失败时抛出。
     */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FinancialDocument> upload(
            @RequestParam Long instrumentId,
            @RequestParam(required = false) Long reportId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponses.success(documents.store(instrumentId, reportId, file.getOriginalFilename(), file.getInputStream(), file.getSize()));
    }

    /**
     * 查询财报关联的财务文档列表。
     *
     * @param id 财报 ID。
     * @return 该财报关联的财务文档列表。
     */
    @GetMapping("/reports/{id}/documents")
    public ApiResponse<List<FinancialDocument>> documents(@PathVariable Long id) {
        return ApiResponses.success(documents.listByReport(id));
    }

    /**
     * 请求生成财报解读。
     *
     * @param id 财报 ID。
     * @param request 解读请求，包含是否强制重新解读的标记，可为空。
     * @return 202 Accepted 响应，响应体为生成中的财报解读。
     */
    @PostMapping("/reports/{id}/interpretations")
    public ResponseEntity<ApiResponse<FinancialInterpretation>> interpret(
            @PathVariable Long id,
            @RequestBody(required = false) FinancialInterpretationRequest request) {
        FinancialInterpretation result = interpretations.request(
                id, request != null && request.isForce());
        return ResponseEntity.accepted().body(ApiResponses.success(result));
    }

    /**
     * 查询财报的最新解读。
     *
     * @param id 财报 ID。
     * @return 该财报的最新解读。
     */
    @GetMapping("/reports/{id}/interpretations/latest")
    public ApiResponse<FinancialInterpretation> latestInterpretation(@PathVariable Long id) {
        return ApiResponses.success(interpretations.latest(id));
    }

    /**
     * 查询财报解读历史。
     *
     * @param id 财报 ID。
     * @param limit 返回条数上限。
     * @return 财报解读历史列表。
     */
    @GetMapping("/reports/{id}/interpretations")
    public ApiResponse<List<FinancialInterpretation>> interpretationHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.success(interpretations.history(id, limit));
    }

    /**
     * 查询标的关联的券商研报列表。
     *
     * @param id 标的 ID。
     * @return 该标的关联的券商研报列表。
     */
    @GetMapping("/instruments/{id}/research-reports")
    public ApiResponse<List<BrokerResearchReport>> researchReports(@PathVariable Long id) {
        return ApiResponses.success(brokerResearch.list(id));
    }

    /**
     * 查询标的可导入的券商研报候选。
     *
     * @param id 标的 ID。
     * @return 券商研报同步结果，包含候选研报列表。
     */
    @GetMapping("/instruments/{id}/research-reports/candidates")
    public ApiResponse<BrokerResearchSyncResult> researchReportCandidates(
            @PathVariable Long id) {
        return ApiResponses.success(brokerResearchSync.candidates(id));
    }

    /**
     * 导入券商研报候选。
     *
     * @param id 标的 ID。
     * @param request 研报导入请求，包含关联财报 ID、来源编码和外部 ID。
     * @return 导入后的券商研报视图。
     */
    @PostMapping("/instruments/{id}/research-reports/import")
    public ApiResponse<BrokerResearchReportView> importResearchReport(
            @PathVariable Long id,
            @Valid @RequestBody BrokerResearchImportRequest request) {
        return ApiResponses.success(brokerResearchSync.importCandidate(
                id, request.getFinancialReportId(),
                request.getSourceCode(), request.getExternalId()));
    }

    /**
     * 查询券商研报视图详情。
     *
     * @param id 券商研报 ID。
     * @param financialReportId 关联财报 ID，可为空。
     * @return 券商研报视图详情。
     */
    @GetMapping("/research-reports/{id}")
    public ApiResponse<BrokerResearchReportView> researchReport(
            @PathVariable Long id,
            @RequestParam(required = false) Long financialReportId) {
        return ApiResponses.success(brokerResearch.get(id, financialReportId));
    }

    /**
     * 上传券商研报 PDF。
     *
     * @param instrumentId 标的 ID。
     * @param financialReportId 关联财报 ID，可为空。
     * @param title 研报标题，可为空。
     * @param institution 研报机构，可为空。
     * @param analyst 研报分析师，可为空。
     * @param publishedDate 研报发布日期，可为空。
     * @param rating 研报评级，可为空。
     * @param reportType 研报类型，可为空。
     * @param targetPrice 目标价，可为空。
     * @param file 上传的研报 PDF 文件。
     * @return 上传并解析后的券商研报视图。
     * @throws IOException 读取上传文件流失败时抛出。
     */
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

    /**
     * 重新解析券商研报。
     *
     * @param id 券商研报 ID。
     * @param financialReportId 关联财报 ID，可为空。
     * @return 重新解析后的券商研报视图。
     */
    @PostMapping("/research-reports/{id}/reanalyze")
    public ApiResponse<BrokerResearchReportView> reanalyzeResearchReport(
            @PathVariable Long id,
            @RequestParam(required = false) Long financialReportId) {
        return ApiResponses.success(brokerResearch.reanalyze(id, financialReportId));
    }

    /**
     * 查询财报解读详情。
     *
     * @param id 财报解读 ID。
     * @return 指定财报解读详情。
     */
    @GetMapping("/interpretations/{id}")
    public ApiResponse<FinancialInterpretation> interpretation(@PathVariable Long id) {
        return ApiResponses.success(interpretations.get(id));
    }

    /**
     * 查询财报解读的证据列表。
     *
     * @param id 财报解读 ID。
     * @return 该解读引用的财务证据列表。
     */
    @GetMapping("/interpretations/{id}/evidence")
    public ApiResponse<List<FinancialEvidence>> interpretationEvidence(@PathVariable Long id) {
        return ApiResponses.success(interpretations.evidence(id));
    }

}
