package com.finscope.service.quant.experiment;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.quant.QuantExperimentRepository;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.strategy.QuantStrategyService;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.domain.quant.data.QuantDataset;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.task.TaskRejectedException;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class QuantExperimentService {
    @Resource private QuantExperimentRepository repository;
    @Resource private QuantStrategyService strategies;
    @Resource private QuantExperimentRunner runner;
    @Resource private QuantExperimentAgent agent;
    @Resource private QuantDatasetService datasets;
    @Resource(name = "quantExperimentExecutor") private Executor executor;

    public QuantExperiment create(Long strategyVersionId) {
        if (strategyVersionId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "策略版本不能为空");
        QuantStrategyVersion version = strategies.getVersion(strategyVersionId);
        String requestFingerprint = sha256(version.getStrategyFingerprint() + "|" + version.getDatasetFingerprint() + "|" + version.getEngineVersion());
        java.util.Optional<QuantExperiment> active = repository.findActiveByRequestFingerprint(requestFingerprint);
        if (active.isPresent()) return decorate(active.get());
        QuantExperiment value = new QuantExperiment(); value.setStrategyVersionId(strategyVersionId);
        value.setRequestFingerprint(requestFingerprint); value.setDatasetFingerprint(version.getDatasetFingerprint());
        value.setEngineVersion(version.getEngineVersion()); value.setStatus("QUEUED");
        final QuantExperiment saved;
        try {
            saved = repository.save(value);
        } catch (DataIntegrityViolationException ex) {
            return decorate(repository.findActiveByRequestFingerprint(requestFingerprint).orElseThrow(() -> ex));
        }
        try {
            executor.execute(() -> runner.run(saved.getId()));
        } catch (TaskRejectedException ex) {
            throw rejected(saved, ex);
        } catch (RejectedExecutionException ex) {
            throw rejected(saved, ex);
        }
        return decorate(saved);
    }
    public QuantExperiment get(Long id) { return decorate(repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "量化实验不存在"))); }
    public List<QuantExperiment> list() { List<QuantExperiment> values = repository.findAll(); values.forEach(this::decorate); return values; }
    public QuantExperiment interpret(Long id) {
        QuantExperiment value = get(id);
        agent.interpret(value);
        return get(id);
    }
    private BusinessException rejected(QuantExperiment experiment, RuntimeException cause) {
        repository.markFailed(experiment.getId(), "实验队列已满，请稍后重试");
        return new BusinessException(ErrorCode.CONFLICT, "实验队列已满，请稍后重试", cause);
    }
    private QuantExperiment decorate(QuantExperiment experiment) {
        QuantStrategyVersion version = strategies.getVersion(experiment.getStrategyVersionId());
        QuantDataset dataset = datasets.get(version.getDatasetId());
        experiment.setDatasetId(dataset.getId()); experiment.setDatasetName(dataset.getName()); experiment.setDataKind(dataset.getDataKind());
        return experiment;
    }
    private String sha256(String input) {
        try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(); for (byte item : digest) result.append(String.format("%02x", item & 0xff)); return result.toString();
        } catch (Exception ex) { throw new IllegalStateException("无法计算实验指纹", ex); }
    }
}
