package com.finscope.service.quant.catalog;

import com.finscope.dao.quant.QuantStrategyCatalogRepository;
import com.finscope.domain.quant.catalog.QuantStrategyCandidate;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogEntry;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSnapshot;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSource;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSyncResult;
import com.finscope.domain.quant.catalog.QuantStrategyCompatibility;
import com.finscope.rpc.quant.catalog.QuantStrategyCatalogProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuantStrategyCatalogService {
    private final QuantStrategyCatalogProvider provider;
    private final QuantStrategyCatalogRepository repository;
    private final QuantStrategyCompatibilityService compatibility;

    public QuantStrategyCatalogService(QuantStrategyCatalogProvider provider,
                                       QuantStrategyCatalogRepository repository,
                                       QuantStrategyCompatibilityService compatibility) {
        this.provider = provider;
        this.repository = repository;
        this.compatibility = compatibility;
    }

    @Transactional
    public QuantStrategyCatalogSyncResult sync() {
        QuantStrategyCatalogSnapshot snapshot = provider.fetch();
        List<QuantStrategyCandidate> candidates = new ArrayList<QuantStrategyCandidate>();
        for (QuantStrategyCatalogEntry entry : snapshot.getEntries()) candidates.add(candidate(entry));
        QuantStrategyCatalogSource source = new QuantStrategyCatalogSource();
        source.setCode(snapshot.getSourceCode()); source.setRepositoryUrl(snapshot.getRepositoryUrl());
        source.setBranch(snapshot.getBranch()); source.setCommitSha(snapshot.getCommitSha());
        source.setStatus("READY"); source.setLastSyncedAt(snapshot.getFetchedAt());
        repository.saveSource(source);
        repository.upsertCandidates(snapshot.getSourceCode(), snapshot.getCommitSha(), candidates, snapshot.getFetchedAt());
        QuantStrategyCatalogSyncResult result = new QuantStrategyCatalogSyncResult();
        result.setSourceCode(snapshot.getSourceCode()); result.setCommitSha(snapshot.getCommitSha());
        result.setImportedCount(candidates.size()); result.setActiveCount(repository.countActive());
        result.setSyncedAt(snapshot.getFetchedAt()); return result;
    }

    public List<QuantStrategyCandidate> list(String compatibilityStatus, String query) {
        return repository.findCandidates(compatibilityStatus, query);
    }
    public java.util.Optional<QuantStrategyCatalogSource> source() { return repository.findSource(); }
    public java.util.Optional<QuantStrategyCandidate> find(Long id) { return repository.findById(id); }

    private QuantStrategyCandidate candidate(QuantStrategyCatalogEntry entry) {
        QuantStrategyCompatibility assessment = compatibility.evaluate(entry);
        QuantStrategyCandidate value = new QuantStrategyCandidate();
        value.setExternalKey(entry.getExternalKey()); value.setTitle(entry.getTitle());
        value.setReportedSharpe(entry.getReportedSharpe()); value.setReportedVolatility(entry.getReportedVolatility());
        value.setRebalanceCadence(entry.getRebalanceCadence()); value.setImplementationUrl(entry.getImplementationUrl());
        value.setPaperUrl(entry.getPaperUrl()); value.setAssetClass("EQUITY");
        value.setCompatibilityStatus(assessment.getStatus()); value.setAdaptationNote(assessment.getAdaptationNote());
        value.setMappedFactors(assessment.getMappedFactors()); value.setMissingFactors(assessment.getMissingFactors());
        return value;
    }
}
