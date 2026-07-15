package com.finscope.service.factorresearch;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.factorresearch.ResearchDraftRepository;
import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.ResearchDraft;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;

@Service
public class ResearchDraftService {
    private static final FactorIdentity CAPITAL_FACTOR =
            new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0");
    private final ResearchDraftRepository repository;
    private final ResearchFactorCatalog catalog;
    private final Clock clock;

    @Autowired
    public ResearchDraftService(ResearchDraftRepository repository, ResearchFactorCatalog catalog) {
        this(repository, catalog, Clock.systemDefaultZone());
    }

    ResearchDraftService(ResearchDraftRepository repository, ResearchFactorCatalog catalog, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional
    public ResearchDraft createFromCapitalSignal(CapitalResearchDraftCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        catalog.get(CAPITAL_FACTOR.getNamespace(), CAPITAL_FACTOR.getCode(), CAPITAL_FACTOR.getVersion());

        ResearchDraft value = new ResearchDraft();
        value.setSourceType("CAPITAL_BEHAVIOR");
        value.setInstrumentCode(required(command.getInstrumentCode(), "instrumentCode").toUpperCase(Locale.ROOT));
        value.setInstrumentName(required(command.getInstrumentName(), "instrumentName"));
        value.setObservedAt(required(command.getObservedAt(), "observedAt"));
        value.setSignalCode(required(command.getSignalCode(), "signalCode"));
        value.setFactor(CAPITAL_FACTOR);
        value.setSnapshotId(required(command.getSnapshotId(), "snapshotId"));
        value.setSnapshotFingerprint(required(command.getSnapshotFingerprint(), "snapshotFingerprint"));
        value.setEvidenceRefs(command.getEvidenceRefs());
        value.setObjectiveTags(command.getObjectiveTags());
        value.setEvaluationMode("CROSS_SECTIONAL_FACTOR_STUDY");
        value.setStatus("DRAFT");
        value.setRequiredNextSteps(Arrays.asList(
                "冻结同日股票池资金数据并通过质量门禁",
                "预注册股票池、持有期与失败条件",
                "运行横截面评价后再决定是否进入策略实验"));
        value.setCreatedAt(LocalDateTime.now(clock));
        value.validate();
        return repository.save(value);
    }

    public ResearchDraft get(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "研究草稿不存在：" + id));
    }

    private static <T> T required(T value, String field) {
        if (value == null || value instanceof String && ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
