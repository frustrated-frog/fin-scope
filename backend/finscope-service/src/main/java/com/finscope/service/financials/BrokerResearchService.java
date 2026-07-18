package com.finscope.service.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.financials.BrokerResearchReportRepository;
import com.finscope.domain.financials.BrokerResearchAnalysis;
import com.finscope.domain.financials.BrokerResearchAnalysisResult;
import com.finscope.domain.financials.BrokerResearchClaim;
import com.finscope.domain.financials.BrokerResearchCandidate;
import com.finscope.domain.financials.BrokerResearchForecast;
import com.finscope.domain.financials.BrokerResearchReport;
import com.finscope.domain.financials.BrokerResearchReportView;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.instrument.Instrument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class BrokerResearchService {
    private static final long MAX_FILE_SIZE = 30L * 1024L * 1024L;
    private final BrokerResearchReportRepository repository;
    private final BrokerResearchDocumentParser parser;
    private final BrokerResearchAnalyzer analyzer;
    private final BrokerResearchFinancialLinker linker;
    private final FinancialQueryService financials;
    private final ObjectMapper json;
    private final Path root;
    private final Object[] contentLocks = createLocks(64);

    @Autowired
    public BrokerResearchService(BrokerResearchReportRepository repository,
                                 BrokerResearchDocumentParser parser,
                                 BrokerResearchAnalyzer analyzer,
                                 BrokerResearchFinancialLinker linker,
                                 FinancialQueryService financials,
                                 ObjectMapper json,
                                 @Value("${finscope.data-root:../data}") String dataRoot) {
        this(repository, parser, analyzer, linker, financials, json,
                java.nio.file.Paths.get(dataRoot).resolve("financials/broker-research"));
    }

    BrokerResearchService(BrokerResearchReportRepository repository,
                          BrokerResearchDocumentParser parser,
                          BrokerResearchAnalyzer analyzer,
                          BrokerResearchFinancialLinker linker,
                          FinancialQueryService financials,
                          ObjectMapper json,
                          Path root) {
        this.repository = repository;
        this.parser = parser;
        this.analyzer = analyzer;
        this.linker = linker;
        this.financials = financials;
        this.json = json;
        this.root = root.toAbsolutePath().normalize();
    }

    public List<BrokerResearchReport> list(Long instrumentId) {
        financials.listReports(instrumentId);
        return repository.findByInstrument(instrumentId);
    }

    public BrokerResearchReportView get(Long id, Long financialReportId) {
        BrokerResearchReport report = require(id);
        List<BrokerResearchForecast> forecasts = repository.findForecasts(id);
        List<BrokerResearchClaim> claims = repository.findClaims(id);
        BrokerResearchAnalysis analysis = analysis(report.getAnalysisJson());
        return view(report, analysis, forecasts, claims,
                financialReportId == null ? report.getLinkedFinancialReportId() : financialReportId);
    }

    public BrokerResearchReportView upload(Long instrumentId, Long financialReportId,
                                           String title, String institution, String analyst,
                                           LocalDate publishedDate, String rating, String reportType,
                                           BigDecimal targetPrice, String originalFileName,
                                           InputStream input, long declaredSize) throws IOException {
        validateSize(declaredSize);
        financials.listReports(instrumentId);
        if (financialReportId != null) validateFinancialReport(instrumentId, financialReportId);
        byte[] content = readBounded(input);
        validatePdf(content);
        String hash = sha256(content);
        synchronized (contentLock(hash)) {
            return storeUnique(instrumentId, financialReportId, title, institution, analyst,
                    publishedDate, rating, reportType, targetPrice, originalFileName, content, hash,
                    "UPLOAD", null);
        }
    }

    private BrokerResearchReportView storeUnique(Long instrumentId, Long financialReportId,
                                                  String title, String institution, String analyst,
                                                  LocalDate publishedDate, String rating, String reportType,
                                                  BigDecimal targetPrice, String originalFileName,
                                                  byte[] content, String hash,
                                                  String sourceType, String sourceUrl) throws IOException {
        Optional<BrokerResearchReport> existing = repository.findByHash(hash);
        if (existing.isPresent()) {
            if (!instrumentId.equals(existing.get().getInstrumentId())) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                        "该 PDF 已归档到另一家公司，请核对所选股票");
            }
            if (sourceUrl != null && existing.get().getSourceUrl() == null) {
                repository.attachSourceIdentity(existing.get().getId(), sourceType, sourceUrl);
                existing.get().setSourceType(sourceType);
                existing.get().setSourceUrl(sourceUrl);
            }
            return get(existing.get().getId(), financialReportId);
        }
        BrokerResearchDocumentParser.ParsedDocument parsed;
        try {
            parsed = parser.parse(content);
        } catch (BrokerResearchDocumentParser.DocumentLimitException limit) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, limit.getMessage());
        } catch (IOException invalidPdf) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    "PDF 文件已损坏或无法解析，请重新下载后上传");
        }
        BrokerResearchAnalysisResult analyzed = analyzer.analyze(parsed.getText(), originalFileName);
        validateSourcePages(analyzed, parsed.getPageCount());
        Path directory = safeDirectory(instrumentId);
        Files.createDirectories(directory);
        Path target = directory.resolve(hash + ".pdf").normalize();
        Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        BrokerResearchReport report = new BrokerResearchReport();
        report.setInstrumentId(instrumentId);
        report.setLinkedFinancialReportId(financialReportId);
        report.setTitle(nonBlank(title, baseName(originalFileName)));
        report.setInstitution(blankToNull(institution));
        report.setAnalyst(blankToNull(analyst));
        report.setPublishedDate(publishedDate);
        report.setRating(blankToNull(rating));
        report.setReportType(nonBlank(reportType, "OTHER"));
        report.setTargetPrice(targetPrice);
        report.setTargetPriceCurrency(targetPrice == null ? null : "CNY");
        report.setSourceType(sourceType);
        report.setSourceUrl(sourceUrl);
        report.setOriginalFileName(safeName(originalFileName));
        report.setRelativePath(root.relativize(target).toString());
        report.setFileSize((long) content.length);
        report.setFileHash(hash);
        report.setPageCount(parsed.getPageCount());
        report.setParseStatus(parsed.getParseStatus());
        report.setAnalysisStatus(analyzed.getAnalysisMode());
        report.setQualityLevel("OCR_REQUIRED".equals(parsed.getParseStatus())
                ? "LOW" : analyzed.getQualityLevel());
        report.setExtractedText(parsed.getText());
        report.setAnalysisJson(json.writeValueAsString(analyzed.getAnalysis()));
        report.setErrorMessage(analyzed.getErrorMessage());
        try {
            repository.save(report, analyzed.getForecasts(), analyzed.getClaims());
        } catch (RuntimeException error) {
            Files.deleteIfExists(target);
            throw error;
        }
        return view(report, analyzed.getAnalysis(), analyzed.getForecasts(), analyzed.getClaims(), financialReportId);
    }

    public BrokerResearchReportView importRemote(Long instrumentId, Long financialReportId,
                                                 BrokerResearchCandidate candidate, byte[] content) {
        if (candidate == null || candidate.getSourceCode() == null
                || candidate.getExternalId() == null || candidate.getSourceUrl() == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "公开研报身份不完整");
        }
        Instrument instrument = financials.instrument(instrumentId);
        String instrumentCode = stockCode(instrument.getCode());
        String candidateCode = stockCode(candidate.getStockCode());
        if (instrumentCode.isEmpty() || !instrumentCode.equals(candidateCode)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    "公开研报与所选公司不匹配");
        }
        if (financialReportId != null) validateFinancialReport(instrumentId, financialReportId);
        Optional<BrokerResearchReport> sourced = repository.findBySourceUrl(
                candidate.getSourceCode(), candidate.getSourceUrl());
        if (sourced.isPresent()) {
            if (!instrumentId.equals(sourced.get().getInstrumentId())) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                        "该公开研报已归档到另一家公司");
            }
            return get(sourced.get().getId(), financialReportId);
        }
        validateSize(content == null ? 0 : content.length);
        validatePdf(content);
        String hash = sha256(content);
        try {
            synchronized (contentLock(hash)) {
                return storeUnique(instrumentId, financialReportId,
                        candidate.getTitle(), candidate.getInstitution(), candidate.getAnalyst(),
                        candidate.getPublishedDate(), candidate.getRating(),
                        nonBlank(candidate.getReportType(), "COMPANY_RESEARCH"), null,
                        candidate.getExternalId() + ".pdf", content, hash,
                        candidate.getSourceCode(), candidate.getSourceUrl());
            }
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.FILE_OPERATION_ERROR,
                    "公开研报原文保存失败", error);
        }
    }

    public BrokerResearchReportView reanalyze(Long id, Long financialReportId) {
        BrokerResearchReport report = require(id);
        if (financialReportId != null) validateFinancialReport(report.getInstrumentId(), financialReportId);
        BrokerResearchAnalysisResult analyzed = analyzer.analyze(
                report.getExtractedText(), report.getOriginalFileName());
        validateSourcePages(analyzed, report.getPageCount());
        try {
            report.setAnalysisStatus(analyzed.getAnalysisMode());
            report.setQualityLevel(analyzed.getQualityLevel());
            report.setAnalysisJson(json.writeValueAsString(analyzed.getAnalysis()));
            report.setErrorMessage(analyzed.getErrorMessage());
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        repository.replaceAnalysis(report, analyzed.getForecasts(), analyzed.getClaims());
        return view(report, analyzed.getAnalysis(), analyzed.getForecasts(), analyzed.getClaims(),
                financialReportId == null ? report.getLinkedFinancialReportId() : financialReportId);
    }

    public BrokerResearchReport require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("研报不存在：" + id));
    }

    public Path contentPath(Long id) {
        return contentPath(id, require(id));
    }

    Path contentPath(Long id, BrokerResearchReport report) {
        Path path = root.resolve(report.getRelativePath()).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "研报文件路径不合法：" + id);
        }
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("研报原件不存在：" + id);
        }
        return path;
    }

    private BrokerResearchReportView view(BrokerResearchReport report,
                                          BrokerResearchAnalysis analysis,
                                          List<BrokerResearchForecast> forecasts,
                                          List<BrokerResearchClaim> claims,
                                          Long financialReportId) {
        if (financialReportId != null) {
            FinancialReportView financial = financials.view(financialReportId);
            if (!report.getInstrumentId().equals(financial.getReport().getInstrumentId())) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "研报与财报不属于同一家公司");
            }
            linker.link(financial, forecasts, claims);
        } else {
            linker.link(null, forecasts, claims);
        }
        BrokerResearchReportView result = new BrokerResearchReportView();
        result.setReport(report);
        result.setAnalysis(analysis);
        result.setForecasts(forecasts);
        result.setClaims(claims);
        return result;
    }

    private BrokerResearchAnalysis analysis(String value) {
        if (value == null || value.trim().isEmpty()) return new BrokerResearchAnalysis();
        try {
            return json.readValue(value, BrokerResearchAnalysis.class);
        } catch (Exception error) {
            BrokerResearchAnalysis fallback = new BrokerResearchAnalysis();
            fallback.setLimitations(Collections.singletonList("历史解析结果无法读取，请重新解析"));
            return fallback;
        }
    }

    private void validateFinancialReport(Long instrumentId, Long reportId) {
        FinancialReportView financial = financials.view(reportId);
        if (!instrumentId.equals(financial.getReport().getInstrumentId())) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "研报与财报不属于同一家公司");
        }
    }

    private void validateSourcePages(BrokerResearchAnalysisResult analyzed, Integer pageCount) {
        if (pageCount == null || pageCount <= 0) return;
        int rejected = 0;
        for (BrokerResearchForecast value : analyzed.getForecasts()) {
            if (invalidPage(value.getSourcePage(), pageCount)) {
                value.setSourcePage(null);
                rejected++;
            }
        }
        for (BrokerResearchClaim value : analyzed.getClaims()) {
            if (invalidPage(value.getSourcePage(), pageCount)) {
                value.setSourcePage(null);
                rejected++;
            }
        }
        for (List<BrokerResearchAnalysis.EvidencePoint> section
                : analyzed.getAnalysis().getEvidenceSections().values()) {
            for (BrokerResearchAnalysis.EvidencePoint value : section) {
                if (invalidPage(value.getSourcePage(), pageCount)) {
                    value.setSourcePage(null);
                    rejected++;
                }
            }
        }
        if (rejected > 0) {
            analyzed.getAnalysis().getLimitations().add(
                    rejected + " 条引用的页码超出 PDF 范围，已移除页码但保留可定位原文");
        }
    }

    private boolean invalidPage(Integer page, int pageCount) {
        return page != null && (page < 1 || page > pageCount);
    }

    private Path safeDirectory(Long instrumentId) {
        Path directory = root.resolve(String.valueOf(instrumentId)).normalize();
        if (!directory.startsWith(root)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "研报存储路径不合法");
        }
        return directory;
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > MAX_FILE_SIZE) validateSize(total);
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void validateSize(long size) {
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "研报 PDF 大小必须在 30MB 以内");
        }
    }

    private void validatePdf(byte[] content) {
        if (content.length < 5 || content[0] != '%' || content[1] != 'P'
                || content[2] != 'D' || content[3] != 'F' || content[4] != '-') {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "上传文件不是有效 PDF");
        }
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private String baseName(String value) {
        String safe = safeName(value);
        return safe.toLowerCase().endsWith(".pdf") ? safe.substring(0, safe.length() - 4) : safe;
    }
    private String safeName(String value) {
        return nonBlank(value, "research-report.pdf").replace("\\", "_").replace("/", "_");
    }
    private String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String stockCode(String value) {
        if (value == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<!\\d)(\\d{6})(?!\\d)").matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private Object contentLock(String hash) {
        return contentLocks[(hash.hashCode() & Integer.MAX_VALUE) % contentLocks.length];
    }

    private static Object[] createLocks(int count) {
        Object[] locks = new Object[count];
        for (int index = 0; index < count; index++) locks[index] = new Object();
        return locks;
    }
}
