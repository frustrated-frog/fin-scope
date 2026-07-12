package com.finscope.service.quant.experiment;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.strategy.QuantStrategyService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.Executor;

@Service
public class QuantExperimentService {
    @Resource private QuantExperimentRepository repository;
    @Resource private QuantStrategyService strategies;
    @Resource private QuantExperimentRunner runner;
    @Resource private QuantExperimentAgent agent;
    @Resource(name = "quantExperimentExecutor") private Executor executor;

    public QuantExperiment create(Long strategyVersionId) {
        QuantStrategyVersion version = strategies.getVersion(strategyVersionId);
        String requestFingerprint = sha256(version.getStrategyFingerprint() + "|" + version.getDatasetFingerprint() + "|" + version.getEngineVersion());
        java.util.Optional<QuantExperiment> active = repository.findActiveByRequestFingerprint(requestFingerprint);
        if (active.isPresent()) return active.get();
        QuantExperiment value = new QuantExperiment(); value.setStrategyVersionId(strategyVersionId);
        value.setRequestFingerprint(requestFingerprint); value.setDatasetFingerprint(version.getDatasetFingerprint());
        value.setEngineVersion(version.getEngineVersion()); value.setStatus("QUEUED"); QuantExperiment saved = repository.save(value);
        executor.execute(() -> runner.run(saved.getId())); return saved;
    }
    public QuantExperiment get(Long id) { return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "量化实验不存在")); }
    public List<QuantExperiment> list() { return repository.findAll(); }
    public QuantExperiment interpret(Long id) {
        QuantExperiment value = get(id);
        agent.interpret(value);
        return get(id);
    }
    private String sha256(String input) {
        try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(); for (byte item : digest) result.append(String.format("%02x", item & 0xff)); return result.toString();
        } catch (Exception ex) { throw new IllegalStateException("无法计算实验指纹", ex); }
    }
}
