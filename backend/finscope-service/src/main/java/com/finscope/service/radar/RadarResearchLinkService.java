package com.finscope.service.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.service.cache.ViewRevisionService;
import org.springframework.stereotype.Service;

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
        radar.findEvent(eventId).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"雷达事件不存在"));
        runs.findById(runId).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"研究运行不存在"));
        String snapshot=question==null||question.trim().isEmpty()?null:question.trim();
        if(snapshot!=null&&snapshot.length()>500)throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,"研究问题不能超过500字");
        RadarEventWorkspace.ResearchLink linked = links.linkResearchRun(eventId,runId,snapshot);
        if (viewRevisions != null) viewRevisions.invalidate("radar");
        return linked;
    }
}
