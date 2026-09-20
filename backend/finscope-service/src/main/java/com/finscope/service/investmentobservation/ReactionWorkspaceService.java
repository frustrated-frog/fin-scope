package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
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
import java.util.HashSet;
import java.util.List;

@Service
public class ReactionWorkspaceService {
    @Resource
    private ReactionSampleRepository repository;
    @Resource
    private ReactionRegistrationService registration;

    public List<ReactionSample> peers(long id) {
        return repository.findByIdentity(registration.require(id).getSourceIdentity());
    }

    public List<ReactionSource> sources(long id) {
        return repository.sources(registration.require(id).getSourceIdentity());
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
        List<ReactionSample> eligible = new ArrayList<>();
        if (target.getPublishedAt() != null && target.getCalculation() != null && target.getEventSubtype() != ReactionEventSubtype.UNCLASSIFIED) {
            long cursor = 0;
            while (true) {
                List<ReactionSample> page = repository.list(null, cursor, 100);
                for (ReactionSample candidate : page) {
                    if (eligible(target, candidate)) {
                        eligible.add(candidate);
                    }
                }
                if (page.size() < 100) {
                    break;
                }
                cursor = page.get(page.size() - 1).getId();
            }
        }
        ReactionHistoryComparison result = new ReactionHistoryComparison();
        result.setSessions(sessions);
        result.setSameCompany(group(target, eligible.stream().filter(value -> value.getInstrumentCode().equals(target.getInstrumentCode())).toList(), sessions));
        result.setOtherCompanies(group(target, eligible.stream().filter(value -> !value.getInstrumentCode().equals(target.getInstrumentCode())).toList(), sessions));
        return result;
    }

    private boolean eligible(ReactionSample target, ReactionSample candidate) {
        return candidate.getState() != ReactionSampleState.DRAFT && !target.getSourceIdentity().equals(candidate.getSourceIdentity())
                && candidate.getPublishedAt() != null && candidate.getPublishedAt().isBefore(target.getPublishedAt())
                && candidate.getEventType() == target.getEventType() && candidate.getEventSubtype() == target.getEventSubtype()
                && (candidate.getCalculation() == null || target.getCalculation().getBenchmarkCode().equals(candidate.getCalculation().getBenchmarkCode())
                && target.getCalculation().getMethodVersion().equals(candidate.getCalculation().getMethodVersion()));
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
        result.setCriteria(criteria + "；无行情样本计入缺失；按事件时间倒序选取有行情的展示案例，不按事后涨幅挑选");
        result.setSampleCount(selected.size());
        HashSet<String> events = new HashSet<>();
        List<BigDecimal> returns = new ArrayList<>();
        for (ReactionSample value : selected) {
            events.add(value.getSourceIdentity());
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
