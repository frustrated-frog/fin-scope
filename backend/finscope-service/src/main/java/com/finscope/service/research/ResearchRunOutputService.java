package com.finscope.service.research;

import com.finscope.dao.research.ResearchRunOutputRepository;
import com.finscope.domain.research.ResearchRunOutput;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class ResearchRunOutputService {
    public static final String ARTICLE = "ARTICLE";
    public static final String EVENT = "EVENT";
    public static final String EVIDENCE = "EVIDENCE";
    public static final String BRIEF = "BRIEF";
    public static final String REPORT = "REPORT";
    @Resource private ResearchRunOutputRepository repository;
    public void recordCurrentRun(String type, Long outputId) {
        Long runId = ResearchRunContext.currentRunId();
        if (runId != null && outputId != null) repository.record(runId, type, outputId);
    }
    public void record(Long runId, String type, Long outputId) {
        if (runId != null && outputId != null) repository.record(runId, type, outputId);
    }
    public int count(Long runId, String type) { return repository.countByRunIdAndType(runId, type); }
    public List<ResearchRunOutput> list(Long runId) { return repository.findByRunId(runId); }
    public int deleteByType(Long runId, String type) {
        return repository.deleteByRunIdAndType(runId, type);
    }
}
