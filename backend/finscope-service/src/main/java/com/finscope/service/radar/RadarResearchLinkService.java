package com.finscope.service.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.domain.radar.RadarEventWorkspace;
import org.springframework.stereotype.Service;

@Service
public class RadarResearchLinkService {
    private final RadarEventWorkspaceRepository links; private final RadarRepository radar; private final ResearchRunRepository runs;
    public RadarResearchLinkService(RadarEventWorkspaceRepository links,RadarRepository radar,ResearchRunRepository runs){
        this.links=links;this.radar=radar;this.runs=runs;
    }
    public RadarEventWorkspace.ResearchLink link(Long eventId,Long runId,String question){
        radar.findEvent(eventId).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"雷达事件不存在"));
        runs.findById(runId).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"研究运行不存在"));
        String snapshot=question==null||question.trim().isEmpty()?null:question.trim();
        if(snapshot!=null&&snapshot.length()>500)throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID,"研究问题不能超过500字");
        return links.linkResearchRun(eventId,runId,snapshot);
    }
}
