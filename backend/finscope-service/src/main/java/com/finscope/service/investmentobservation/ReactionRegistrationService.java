package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.domain.investmentobservation.ReactionCandidate;
import com.finscope.domain.investmentobservation.ReactionRegistration;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.domain.majorevent.MajorEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReactionRegistrationService {
    @Resource
    private ReactionSampleRepository repository;
    @Resource
    private MajorEventRepository majorEvents;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public List<ReactionSample> recent(long beforeId, int limit) {
        if (beforeId <= 0 || limit < 1 || limit > 100) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        return repository.recent(beforeId, limit);
    }

    public List<ReactionSample> list(ReactionSampleState state, long afterId, int limit) {
        if (afterId < 0 || limit < 1 || limit > 100) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        return repository.list(state, afterId, limit);
    }

    public List<ReactionCandidate> candidates() {
        List<ReactionCandidate> result = new ArrayList<>();
        for (MajorEvent event : majorEvents.findRecent(100)) {
            ReactionEventType type = suggestType(event.getTitle());
            if (type == null) {
                continue;
            }
            ReactionCandidate candidate = new ReactionCandidate();
            candidate.setMajorEventId(event.getId());
            candidate.setTitle(event.getTitle());
            candidate.setSummary(event.getSummary());
            candidate.setSourceUrl(event.getSourceUrl());
            candidate.setOccurredDate(event.getOccurredDate());
            candidate.setSuggestedType(type);
            result.add(candidate);
        }
        return result;
    }

    public ReactionSample createDraft(Long majorEventId) {
        if (majorEventId == null || majorEventId <= 0) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        MajorEvent event = majorEvents.findById(majorEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "来源大事记已不存在"));
        ReactionSample sample = new ReactionSample();
        sample.setMajorEventId(event.getId());
        sample.setSourceOriginType(event.getOriginType());
        sample.setSourceOriginKey(event.getOriginKey());
        sample.setTitle(event.getTitle());
        sample.setSummary(event.getSummary());
        sample.setSourceUrl(event.getSourceUrl());
        sample.setOccurredDate(event.getOccurredDate());
        sample.setFirstCapturedAt(event.getCreatedAt());
        sample.setRegisteredAt(LocalDateTime.now(clock));
        sample.setEventType(suggestType(event.getTitle()));
        return repository.create(sample);
    }

    public ReactionSample confirm(long id, ReactionRegistration command) {
        validate(command);
        ReactionSample sample = require(id);
        if (sample.getState() != ReactionSampleState.DRAFT) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "已确认的事件快照不能改写，请归档错误样本");
        }
        sample.setInstrumentCode(command.getInstrumentCode().trim());
        sample.setInstrumentName(command.getInstrumentName().trim());
        sample.setEventType(command.getEventType());
        sample.setPublishedAt(command.getPublishedAt());
        sample.setRelationNote(command.getRelationNote().trim());
        sample.setHistoricalBackfill(command.getPublishedAt().toLocalDate().isBefore(sample.getRegisteredAt().toLocalDate()));
        sample.setState(ReactionSampleState.OBSERVING);
        if (!repository.confirm(sample, command.getRevision())) {
            boolean duplicate = repository.findByIdentity(sample.getSourceIdentity()).stream()
                    .anyMatch(existing -> existing.getInstrumentCode().equals(sample.getInstrumentCode())
                            && !existing.getId().equals(sample.getId()));
            if (duplicate) {
                throw new BusinessException(ErrorCode.DUPLICATE_OPERATION, "这个事件与股票已登记，请打开已有样本");
            }
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT);
        }
        return require(id);
    }

    public ReactionSample archive(long id, int revision, boolean archived) {
        ReactionSample sample = require(id);
        ReactionSampleState target = archived ? ReactionSampleState.ARCHIVED
                : sample.getPublishedAt() == null || blank(sample.getInstrumentCode()) ? ReactionSampleState.DRAFT : ReactionSampleState.OBSERVING;
        if (!archived && sample.getState() != ReactionSampleState.ARCHIVED) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "仅归档样本可以恢复");
        }
        requireUpdated(repository.changeState(id, revision, target));
        return require(id);
    }

    public ReactionSample require(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validate(ReactionRegistration command) {
        if (command == null || command.getInstrumentCode() == null
                || !command.getInstrumentCode().matches("(?:6[0-9]{5}\\.SH|[03][0-9]{5}\\.SZ|[489][0-9]{5}\\.BJ)")
                || blank(command.getInstrumentName()) || command.getInstrumentName().length() > 80
                || command.getEventType() == null || command.getPublishedAt() == null
                || command.getPublishedAt().isAfter(LocalDateTime.now(clock))
                || command.getPublishedAt().getYear() < 1990 || blank(command.getRelationNote())
                || command.getRelationNote().length() > 1000 || command.getRevision() < 0) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "请填写有效 A 股代码、名称、事件类型、已公开的北京时间与关联依据");
        }
    }

    private ReactionEventType suggestType(String title) {
        String value = title == null ? "" : title;
        if (value.matches(".*(业绩|季报|年报|半年报|财报).*")) {
            return ReactionEventType.EARNINGS;
        }
        if (value.matches(".*(合同|订单|中标).*")) {
            return ReactionEventType.CONTRACT;
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void requireUpdated(boolean updated) {
        if (!updated) {
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT);
        }
    }
}
