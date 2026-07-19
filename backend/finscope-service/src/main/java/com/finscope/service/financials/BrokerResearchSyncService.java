package com.finscope.service.financials;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.financials.BrokerResearchReportRepository;
import com.finscope.domain.financials.BrokerResearchCandidate;
import com.finscope.domain.financials.BrokerResearchReport;
import com.finscope.domain.financials.BrokerResearchReportView;
import com.finscope.domain.financials.BrokerResearchSyncResult;
import com.finscope.domain.instrument.Instrument;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class BrokerResearchSyncService {
    private static final int CATALOG_LIMIT = 20;
    private static final int LOOKBACK_DAYS = 365;
    private final Map<String, BrokerResearchSource> sources;
    private final BrokerResearchReportRepository repository;
    private final BrokerResearchService reports;
    private final FinancialQueryService financials;

    public BrokerResearchSyncService(List<BrokerResearchSource> sources,
                                     BrokerResearchReportRepository repository,
                                     BrokerResearchService reports,
                                     FinancialQueryService financials) {
        Map<String, BrokerResearchSource> mapped = new HashMap<String, BrokerResearchSource>();
        for (BrokerResearchSource source : sources) {
            mapped.put(source.sourceCode().toUpperCase(Locale.ROOT), source);
        }
        this.sources = Collections.unmodifiableMap(mapped);
        this.repository = repository;
        this.reports = reports;
        this.financials = financials;
    }

    public BrokerResearchSyncResult candidates(Long instrumentId) {
        return discover(instrumentId);
    }

    public BrokerResearchReportView importCandidate(Long instrumentId, Long financialReportId,
                                                    String sourceCode, String externalId) {
        BrokerResearchSource source = requireSource(sourceCode);
        Instrument instrument = financials.instrument(instrumentId);
        BrokerResearchCandidate candidate = source.list(instrument.getCode(),
                        LocalDate.now().minusDays(LOOKBACK_DAYS), LocalDate.now(), CATALOG_LIMIT)
                .stream()
                .filter(value -> externalId != null && externalId.equals(value.getExternalId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("公开研报候选不存在：" + externalId));
        Optional<BrokerResearchReport> existing = repository.findBySourceUrl(
                source.sourceCode(), candidate.getSourceUrl());
        if (existing.isPresent()) {
            if (!instrumentId.equals(existing.get().getInstrumentId())) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                        "公开研报与所选公司不匹配");
            }
            return reports.get(existing.get().getId(), financialReportId);
        }
        return reports.importRemote(instrumentId, financialReportId,
                candidate, source.download(candidate));
    }

    private BrokerResearchSyncResult discover(Long instrumentId) {
        Instrument instrument = financials.instrument(instrumentId);
        BrokerResearchSyncResult result = new BrokerResearchSyncResult();
        result.setStatus("SUCCESS");
        if (sources.isEmpty()) {
            result.setStatus("FAILED");
            result.getErrors().add("没有可用的公开研报来源");
            result.setCompletedAt(LocalDateTime.now());
            return result;
        }
        List<BrokerResearchCandidate> all = new ArrayList<BrokerResearchCandidate>();
        for (BrokerResearchSource source : sources.values()) {
            try {
                List<BrokerResearchCandidate> found = source.list(instrument.getCode(),
                        LocalDate.now().minusDays(LOOKBACK_DAYS), LocalDate.now(), CATALOG_LIMIT);
                markImported(instrumentId, found, result);
                all.addAll(found);
            } catch (RuntimeException error) {
                result.getErrors().add(source.sourceCode() + "：" + message(error));
            }
        }
        all.sort(Comparator.comparing(BrokerResearchCandidate::getPublishedDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (all.size() > CATALOG_LIMIT) {
            all = new ArrayList<BrokerResearchCandidate>(all.subList(0, CATALOG_LIMIT));
        }
        result.setCandidates(all);
        result.setSourceCode(sources.size() == 1
                ? sources.values().iterator().next().sourceCode() : "MULTI");
        if (!result.getErrors().isEmpty()) {
            result.setStatus(all.isEmpty() && result.getImportedCount() == 0 ? "FAILED" : "PARTIAL");
        }
        result.setCompletedAt(LocalDateTime.now());
        return result;
    }

    private void markImported(Long instrumentId, List<BrokerResearchCandidate> values,
                              BrokerResearchSyncResult result) {
        for (BrokerResearchCandidate candidate : values) {
            Optional<BrokerResearchReport> existing = repository.findBySourceUrl(
                    candidate.getSourceCode(), candidate.getSourceUrl());
            if (existing.isPresent() && instrumentId.equals(existing.get().getInstrumentId())) {
                candidate.setImportedReportId(existing.get().getId());
                candidate.setAvailability("IMPORTED");
                result.setSkippedCount(result.getSkippedCount() + 1);
            } else if (existing.isPresent()) {
                candidate.setAvailability("UNAVAILABLE");
            } else {
                candidate.setAvailability("AVAILABLE");
            }
        }
    }

    private BrokerResearchSource requireSource(String sourceCode) {
        BrokerResearchSource source = sourceCode == null ? null
                : sources.get(sourceCode.toUpperCase(Locale.ROOT));
        if (source == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    "不支持的公开研报来源：" + sourceCode);
        }
        return source;
    }

    private String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty()
                ? error.getClass().getSimpleName() : value;
    }
}
