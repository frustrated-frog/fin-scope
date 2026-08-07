package com.finscope.service.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.service.cache.ViewRevisionService;
import org.springframework.stereotype.Service;
import com.finscope.common.exception.BizErrorCode;

@Service
public class RadarResearchLinkService {
    private final RadarEventWorkspaceRepository links; private final RadarRepository radar; private final ResearchRunRepository runs; private final ViewRevisionService viewRevisions;
    @org.springframework.beans.factory.annotation.Autowired
    public RadarResearchLinkService(RadarEventWorkspaceRepository links,RadarRepository radar,ResearchRunRepository runs,ViewRevisionService viewRevisions){
        this.links=links;this.radar=radar;this.runs=runs;this.viewRevisions=viewRevisions;
    }
    RadarResearchLinkService(RadarEventWorkspaceRepository links,RadarRepository radar,ResearchRunRepository runs){
        this(links,radar,runs,null);
    }
    public RadarEventWorkspace.ResearchLink link(Long eventId,Long runId,String question){
        radar.findEvent(eventId).orElseThrow(()->new BusinessException(BizErrorCode.RADAR_EVENT_NOT_FOUND));
        runs.findById(runId).orElseThrow(()->new BusinessException(BizErrorCode.RESEARCH_RUN_NOT_FOUND));
        String snapshot=question==null||question.trim().isEmpty()?null:question.trim();
        if(snapshot!=null&&snapshot.length()>500)throw new BusinessException(BizErrorCode.RESEARCH_QUESTION_TOO_LONG);
        RadarEventWorkspace.ResearchLink linked = links.linkResearchRun(eventId,runId,snapshot);
        return linked;
    }
}
