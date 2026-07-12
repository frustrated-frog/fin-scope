package com.finscope.service.research;

import com.finscope.dao.research.EventEvidenceThesisRepository;
import com.finscope.domain.research.EventEvidenceThesis;
import com.finscope.domain.research.EvidenceItem;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

@Service
public class EventEvidenceThesisService {
    @Resource private EventEvidenceThesisRepository repository;
    public void syncForEvidence(EvidenceItem evidence) {
        if (evidence == null || evidence.getId() == null || evidence.getEventId() == null || blank(evidence.getClaim())) return;
        EventEvidenceThesis thesis = repository.findByEventAndStatement(evidence.getEventId(), evidence.getClaim()).orElseGet(() -> create(evidence));
        repository.linkEvidence(thesis.getId(), evidence.getId());
    }
    private EventEvidenceThesis create(EvidenceItem evidence) { EventEvidenceThesis thesis = new EventEvidenceThesis(); thesis.setEventId(evidence.getEventId()); thesis.setStatement(evidence.getClaim()); boolean direct = "FACT".equals(evidence.getEvidenceType()) || "TIMELINE".equals(evidence.getEvidenceType()); thesis.setKind(direct ? "FACT" : "IMPACT"); thesis.setStatus(direct ? "CONFIRMED" : "PARTIALLY_SUPPORTED"); thesis.setRationale(direct ? "由可直接引用的事实或时间线材料支持。" : "这是一条数据或影响线索，尚不能单独构成确定结论。"); thesis.setEvidenceGap(direct ? null : "需要独立的一手来源或可比数据验证影响范围和持续期。"); return repository.save(thesis); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
