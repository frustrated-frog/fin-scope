package com.finscope.service.factorresearch;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.factorresearch.ResearchDraftRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.ResearchDraft;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.InstrumentCodeCanonicalizer;
import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.finscope.common.exception.BizErrorCode;

@Service
public class ResearchDraftService {
    private static final FactorIdentity CAPITAL_FACTOR =
            new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0");
    private final ResearchDraftRepository repository;
    private final ResearchFactorCatalog catalog;
    private final CapitalBehaviorSnapshotRepository snapshots;
    private final InstrumentRepository instruments;
    private final Clock clock;

    @Autowired
    public ResearchDraftService(ResearchDraftRepository repository, ResearchFactorCatalog catalog,
                                CapitalBehaviorSnapshotRepository snapshots, InstrumentRepository instruments) {
        this(repository, catalog, snapshots, instruments, Clock.systemDefaultZone());
    }

    ResearchDraftService(ResearchDraftRepository repository, ResearchFactorCatalog catalog,
                         CapitalBehaviorSnapshotRepository snapshots, InstrumentRepository instruments, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.snapshots = snapshots;
        this.instruments = instruments;
        this.clock = clock;
    }

    @Transactional
    public ResearchDraft createFromCapitalSignal(CapitalResearchDraftCommand command) {
        if (command == null) throw new BusinessException(BizErrorCode.RESEARCH_DRAFT_REQUEST_REQUIRED);
        catalog.get(CAPITAL_FACTOR.getNamespace(), CAPITAL_FACTOR.getCode(), CAPITAL_FACTOR.getVersion());
        Long snapshotId = required(command.getSnapshotId(), "snapshotId");
        CapitalBehaviorSnapshot snapshot = snapshots.findById(snapshotId).orElseThrow(() ->
                new BusinessException(BizErrorCode.CAPITAL_SNAPSHOT_NOT_FOUND, snapshotId));
        Instrument instrument = instruments.findById(snapshot.getInstrumentId()).orElseThrow(() ->
                new BusinessException(BizErrorCode.CAPITAL_SNAPSHOT_INSTRUMENT_NOT_FOUND, snapshot.getInstrumentId()));
        String authoritativeCode = InstrumentCodeCanonicalizer.canonical(instrument.getCode(), instrument.getMarket());
        requireMatch(authoritativeCode, required(command.getInstrumentCode(), "instrumentCode").toUpperCase(Locale.ROOT),
                "INSTRUMENT_DOES_NOT_MATCH_SNAPSHOT");
        requireMatch(instrument.getName(), required(command.getInstrumentName(), "instrumentName"),
                "INSTRUMENT_DOES_NOT_MATCH_SNAPSHOT");
        requireMatch(snapshot.getAsOf(), required(command.getObservedAt(), "observedAt"),
                "OBSERVED_AT_DOES_NOT_MATCH_SNAPSHOT");
        requireMatch(snapshot.getFingerprint(), required(command.getSnapshotFingerprint(), "snapshotFingerprint"),
                "SNAPSHOT_FINGERPRINT_CHANGED");
        String signalCode = authoritativeSignalCode(snapshot, command.getSignalCode());
        List<String> evidenceRefs = authoritativeEvidenceRefs(snapshot);
        List<String> objectiveTags = authoritativeObjectiveTags(snapshot);

        ResearchDraft value = new ResearchDraft();
        value.setSourceType("CAPITAL_BEHAVIOR");
        value.setInstrumentCode(authoritativeCode);
        value.setInstrumentName(instrument.getName());
        value.setObservedAt(snapshot.getAsOf());
        value.setSignalCode(signalCode);
        value.setFactor(CAPITAL_FACTOR);
        value.setSnapshotId(snapshot.getId());
        value.setSnapshotFingerprint(snapshot.getFingerprint());
        value.setEvidenceRefs(evidenceRefs);
        value.setObjectiveTags(objectiveTags);
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
                new BusinessException(BizErrorCode.RESEARCH_DRAFT_NOT_FOUND, id));
    }

    private static <T> T required(T value, String field) {
        if (value == null || value instanceof String && ((String) value).trim().isEmpty()) {
            throw new BusinessException(BizErrorCode.REQUIRED_FIELD_EMPTY, field);
        }
        return value;
    }

    private static void requireMatch(Object authoritative, Object requested, String reason) {
        if (!java.util.Objects.equals(authoritative, requested)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, reason);
        }
    }

    private static String authoritativeSignalCode(CapitalBehaviorSnapshot snapshot, String requested) {
        String requiredSignal = required(requested, "signalCode");
        if (snapshot.getSignals().isEmpty()) return "CAPITAL_SNAPSHOT_OBSERVATION";
        for (CapitalBehaviorSignal signal : snapshot.getSignals()) {
            if (requiredSignal.equals(signal.getType())) return signal.getType();
        }
        throw new BusinessException(BizErrorCode.CAPITAL_SIGNAL_NOT_IN_SNAPSHOT);
    }

    private static List<String> authoritativeEvidenceRefs(CapitalBehaviorSnapshot snapshot) {
        Set<String> refs = new LinkedHashSet<String>();
        refs.add("snapshot:" + snapshot.getId());
        for (CapitalBehaviorSignal signal : snapshot.getSignals()) {
            refs.addAll(signal.getMetricRefs());
            refs.addAll(signal.getFactorRefs());
        }
        refs.remove(null); refs.remove("");
        return new ArrayList<String>(refs);
    }

    private static List<String> authoritativeObjectiveTags(CapitalBehaviorSnapshot snapshot) {
        Set<String> tags = new LinkedHashSet<String>();
        for (CapitalBehaviorSignal signal : snapshot.getSignals()) {
            if (signal.getType() != null && !signal.getType().trim().isEmpty()) tags.add(signal.getType());
        }
        return new ArrayList<String>(tags);
    }
}
