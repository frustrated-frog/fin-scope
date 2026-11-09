package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.investmentobservation.ReactionEventQueryRepository;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.domain.investmentobservation.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Service
public class ReactionWorkspaceService {
    @Resource
    private ReactionSampleRepository repository;
    @Resource
    private ReactionRegistrationService registration;

    @Resource
    private ReactionEventQueryRepository eventQueries;

    @Resource
    private InstrumentRepository instruments;

    public ReactionEventPage query(ReactionEventQuery query) {
        if (query.getView() == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        validatePage(query.getQuery(), query.getPage(), query.getSize(), query.getAnchor());
        if (query.getStock() != null) {
            String code = query.getStock().strip().toUpperCase(Locale.ROOT);
            if (!code.matches("[0-9]{6}\\.(SH|SZ|BJ)")) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
            }
            query.setStock(code);
        }
        query.setQuery(query.getQuery().trim());
        return eventQueries.query(query);
    }

    public ReactionStockPage queryStocks(ReactionStockQuery query) {
        validatePage(query.getQuery(), query.getPage(), query.getSize(), query.getAnchor());
        query.setQuery(query.getQuery().strip());
        ReactionStockPage page = eventQueries.queryStocks(query);
        if (page.getTotal() == 0 && !query.getQuery().isEmpty() && !query.isTrackingOnly()) {
            page.setMatchedStock(findKnownStock(query.getQuery()));
        }
        return page;
    }

    private ReactionStockMatch findKnownStock(String search) {
        var matches = instruments.findAll().stream()
                .filter(instrument -> "STOCK".equals(instrument.getType())
                        && instrument.getMarket() != null && instrument.getMarket().matches("SH|SZ|BJ"))
                .filter(instrument -> search.equals(instrument.getName()) || search.equalsIgnoreCase(instrument.getCode())
                        || search.equalsIgnoreCase(instrument.getCode().replaceFirst("\\.(SH|SZ|BJ)$", "") + "." + instrument.getMarket()))
                .toList();
        if (matches.size() != 1) {
            return null;
        }
        var instrument = matches.get(0);
        var result = new ReactionStockMatch();
        result.setCode(instrument.getCode().replaceFirst("\\.(SH|SZ|BJ)$", "") + "." + instrument.getMarket());
        result.setName(instrument.getName());
        return result;
    }

    private void validatePage(String query, int page, int size, long anchor) {
        if (page < 1 || page > 100000 || size < 1 || size > 50 || anchor < 0
                || query == null || query.length() > 200) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
    }

    public ReactionSample event(String key, String stock) {
        List<ReactionSample> samples = repository.findByIdentity(key);
        return samples.stream().filter(value -> stock == null || stock.equals(value.getInstrumentCode()))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public ReactionSample exclude(long id, int revision, boolean excluded) {
        registration.require(id);
        if (!repository.exclude(id, revision, excluded)) {
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT);
        }
        return registration.require(id);
    }

    public List<ReactionSource> versions(long id) {
        ReactionSample sample = registration.require(id);
        return repository.sourceVersions(sample.getSourceIdentity(), historicalKey(sample));
    }

    public List<ReactionSample> peers(long id) {
        return repository.findByIdentity(registration.require(id).getSourceIdentity());
    }

    public List<ReactionSource> sources(long id) {
        ReactionSample sample = registration.require(id);
        return repository.sources(sample.getSourceIdentity(), historicalKey(sample));
    }

    public List<ReactionSample> follow(long id, boolean followed) {
        ReactionSample sample = registration.require(id);
        if (!repository.followEvent(sample.getSourceIdentity(), followed)) {
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT);
        }
        return peers(id);
    }

    public List<ReactionSample> followed(long beforeId) {
        validateCursor(beforeId);
        return repository.followed(beforeId, 100);
    }

    public List<ReactionChange> changes(LocalDate date, long beforeId) {
        validateCursor(beforeId);
        return repository.changes(date == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : date, beforeId, 100);
    }

    public ReactionHistoryComparison compare(long id, int sessions) {
        if (sessions != 1 && sessions != 3 && sessions != 5) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        ReactionSample target = registration.require(id);
        var distinct = new HashMap<String, ReactionSample>();
        ReactionEventSubtype subtype = subtype(target);
        if (!target.isExcluded() && target.getPublishedAt() != null && target.getCalculation() != null && subtype.isSpecific()) {
            long cursor = 0;
            while (true) {
                List<ReactionSample> page = repository.comparableCandidates(subtype, cursor);
                for (ReactionSample candidate : page) {
                    if (eligible(target, candidate)) {
                        String key = caseIdentity(candidate) + "|" + candidate.getInstrumentCode();
                        distinct.merge(key, candidate, (first, next) -> next.getPublishedAt().isBefore(first.getPublishedAt()) ? next : first);
                    }
                }
                if (page.size() < 100) {
                    break;
                }
                cursor = page.get(page.size() - 1).getId();
            }
        }
        var eligible = distinct.values();
        ReactionHistoryComparison result = new ReactionHistoryComparison();
        result.setSessions(sessions);
        if (!target.isExcluded() && target.getHistoricalCase() != null && target.getHistoricalCase().topicKey() != null) {
            result.setRelatedStages(repository.relatedStages(target.getHistoricalCase()));
        }
        result.setSameCompany(group(target, eligible.stream().filter(value -> value.getInstrumentCode().equals(target.getInstrumentCode())).toList(), sessions));
        result.setOtherCompanies(group(target, eligible.stream().filter(value -> !value.getInstrumentCode().equals(target.getInstrumentCode())).toList(), sessions));
        return result;
    }

    private boolean eligible(ReactionSample target, ReactionSample candidate) {
        return !candidate.isExcluded() && candidate.getState() != ReactionSampleState.DRAFT && !target.getSourceIdentity().equals(candidate.getSourceIdentity())
                && !caseIdentity(target).equals(caseIdentity(candidate))
                && candidate.getPublishedAt() != null && candidate.getPublishedAt().isBefore(target.getPublishedAt())
                && subtype(candidate) == subtype(target)
                && (candidate.getCalculation() == null || target.getCalculation().getBenchmarkCode().equals(candidate.getCalculation().getBenchmarkCode())
                && target.getCalculation().getMethodVersion().equals(candidate.getCalculation().getMethodVersion()));
    }

    private ReactionEventSubtype subtype(ReactionSample sample) {
        return sample.getHistoricalCase() == null ? sample.getEventSubtype() : sample.getHistoricalCase().subtype();
    }

    private String historicalKey(ReactionSample sample) {
        return sample.isExcluded() || sample.getHistoricalCase() == null ? null : sample.getHistoricalCase().key();
    }

    private String caseIdentity(ReactionSample sample) {
        String key = historicalKey(sample);
        return key == null ? "SOURCE:" + sample.getSourceIdentity() : "CASE:" + key;
    }

    private ReactionComparisonGroup group(ReactionSample target, List<ReactionSample> eligible, int sessions) {
        ReactionComparisonGroup result = new ReactionComparisonGroup();
        List<ReactionSample> strict = eligible.stream().filter(value -> timeBand(value) == timeBand(target)
                && beforeBand(value) != null && beforeBand(value).equals(beforeBand(target))).toList();
        boolean relaxed = strict.size() < 3 && strict.size() < eligible.size();
        List<ReactionSample> selected = relaxed ? eligible : strict;
        String criteria = relaxed ? "样本不足：放宽公开时段与事前相对表现；仍限定同事件子类、同基准、同计算口径"
                : "同事件子类、同公开时段、同事前相对表现分组、同基准及计算口径";
        result.setRelaxed(relaxed);
        result.setCriteria(criteria + "；缺失统计仅覆盖已选可比样本，无法判断可比性另列；按事件时间倒序选取有行情的展示案例，不按事后涨幅挑选");
        result.setBaseCandidateCount(eligible.size());
        result.setUnknownComparabilityCount((int) eligible.stream().filter(value -> beforeBand(value) == null).count());
        result.setSampleCount(selected.size());
        HashSet<String> events = new HashSet<>();
        List<BigDecimal> returns = new ArrayList<>();
        for (ReactionSample value : selected) {
            events.add(caseIdentity(value));
            ReactionWindow window = value.getCalculation() == null ? null : value.getCalculation().getWindows().stream().filter(item -> item.getSessions() == sessions).findFirst().orElse(null);
            if (window != null && window.getStatus() == ReactionWindowStatus.READY && window.getRelativeReturnPp() != null) {
                result.setCompleteCount(result.getCompleteCount() + 1);
                returns.add(window.getRelativeReturnPp());
            } else if (window != null && window.getStatus() == ReactionWindowStatus.NOT_DUE) {
                result.setNotDueCount(result.getNotDueCount() + 1);
            } else {
                result.setMissingCount(result.getMissingCount() + 1);
            }
        }
        result.setEventCount(events.size());
        returns.sort(Comparator.naturalOrder());
        if (!returns.isEmpty()) {
            result.setMedian(quantile(returns, 0.5));
            result.setLowerQuartile(quantile(returns, 0.25));
            result.setUpperQuartile(quantile(returns, 0.75));
        }
        for (ReactionSample value : selected.stream().filter(value -> value.getCalculation() != null).sorted(Comparator.comparing(ReactionSample::getPublishedAt).reversed()
                .thenComparing(ReactionSample::getId)).limit(4).toList()) {
            ReactionComparableCase item = new ReactionComparableCase();
            item.setSampleId(value.getId());
            item.setEventKey(value.getSourceIdentity());
            item.setTitle(value.getTitle());
            item.setInstrumentCode(value.getInstrumentCode());
            item.setInstrumentName(value.getInstrumentName());
            item.setPublishedAt(value.getPublishedAt());
            item.setMatchReason(criteria);
            item.setCalculation(value.getCalculation());
            result.getCases().add(item);
        }
        return result;
    }

    private BigDecimal quantile(List<BigDecimal> values, double fraction) {
        double index = (values.size() - 1) * fraction;
        int low = (int) index;
        int high = Math.min(low + 1, values.size() - 1);
        return values.get(low).add(values.get(high).subtract(values.get(low)).multiply(BigDecimal.valueOf(index - low)));
    }

    private Integer beforeBand(ReactionSample sample) {
        ReactionProfile profile = sample.getCalculation() == null ? null : sample.getCalculation().getProfile();
        if (profile == null || profile.getBeforeRelativePp() == null) {
            return null;
        }
        double value = profile.getBeforeRelativePp().doubleValue();
        return value >= 2 ? 1 : value <= -2 ? -1 : 0;
    }

    private void validateCursor(long beforeId) {
        if (beforeId <= 0) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
    }

    private int timeBand(ReactionSample sample) {
        if (sample.getCalculation() != null && sample.getCalculation().getFirstSession() != null
                && sample.getCalculation().getBaselineDate() != null
                && !sample.getPublishedAt().toLocalDate().equals(sample.getCalculation().getFirstSession())
                && !sample.getPublishedAt().toLocalDate().equals(sample.getCalculation().getBaselineDate())) {
            return 3;
        }
        LocalTime time = sample.getPublishedAt().toLocalTime();
        return time.isBefore(LocalTime.of(9, 30)) ? 0 : time.isBefore(LocalTime.of(15, 0)) ? 1 : 2;
    }
}
