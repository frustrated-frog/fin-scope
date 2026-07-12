package com.finscope.service.research;

import com.finscope.dao.research.ResearchThesisRepository;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.ThesisFinding;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.ArrayList;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunOutput;

@Service
public class ResearchThesisService {
    @Resource
    private ResearchThesisRepository researchThesisRepository;
    @Resource private ResearchRunRepository researchRunRepository;
    @Resource private ResearchRunOutputService researchRunOutputService;

    public ResearchThesis create(ResearchThesis thesis) {
        validate(thesis);
        if (isBlank(thesis.getStatus())) {
            thesis.setStatus("OPEN");
        }
        return researchThesisRepository.save(thesis);
    }

    public ResearchThesis update(Long id, ResearchThesis thesis) {
        ResearchThesis existing = detail(id);
        thesis.setId(existing.getId());
        validate(thesis);
        if (isBlank(thesis.getStatus())) {
            thesis.setStatus(existing.getStatus());
        }
        return researchThesisRepository.update(thesis);
    }

    public List<ResearchThesis> list() {
        return researchThesisRepository.findAll();
    }

    public ResearchThesis detail(Long id) {
        return researchThesisRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Research thesis not found: " + id));
    }

    public ThesisFinding addFinding(Long thesisId, ThesisFinding finding) {
        detail(thesisId);
        if (finding == null || isBlank(finding.getStance()) || isBlank(finding.getSummary())) {
            throw new IllegalArgumentException("Finding stance and summary are required");
        }
        finding.setThesisId(thesisId);
        return researchThesisRepository.saveFinding(finding);
    }

    public List<ThesisFinding> findings(Long thesisId) {
        detail(thesisId);
        return researchThesisRepository.findFindingsByThesisId(thesisId);
    }

    public ThesisDetail detailWithResearch(Long thesisId) {
        ResearchThesis thesis = detail(thesisId);
        List<ResearchRun> runs = researchRunRepository.findByThesisId(thesisId);
        List<ResearchRunOutput> outputs = new ArrayList<ResearchRunOutput>();
        for (ResearchRun run : runs) outputs.addAll(researchRunOutputService.list(run.getId()));
        return new ThesisDetail(thesis, findings(thesisId), runs, outputs);
    }
    public static class ThesisDetail { public final ResearchThesis thesis; public final List<ThesisFinding> findings; public final List<ResearchRun> runs; public final List<ResearchRunOutput> outputs; ThesisDetail(ResearchThesis t,List<ThesisFinding> f,List<ResearchRun> r,List<ResearchRunOutput> o){thesis=t;findings=f;runs=r;outputs=o;} }

    private void validate(ResearchThesis thesis) {
        if (thesis == null || isBlank(thesis.getQuestion()) || isBlank(thesis.getSubjectType())
                || isBlank(thesis.getSubjectName())) {
            throw new IllegalArgumentException("Thesis question, subject type and subject name are required");
        }
        if (!"COMPANY".equals(thesis.getSubjectType()) && !"INDUSTRY".equals(thesis.getSubjectType())
                && !"WATCHLIST".equals(thesis.getSubjectType())) {
            throw new IllegalArgumentException("Unsupported thesis subject type: " + thesis.getSubjectType());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
