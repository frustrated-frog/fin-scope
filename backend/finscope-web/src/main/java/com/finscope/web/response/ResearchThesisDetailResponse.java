package com.finscope.web.response;

import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunOutput;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.ThesisFinding;
import java.util.List;

public class ResearchThesisDetailResponse {
    private ResearchThesis thesis;
    private List<ThesisFinding> findings;
    private List<ResearchRun> runs;
    private List<ResearchRunOutput> outputs;
    public ResearchThesisDetailResponse(ResearchThesis thesis, List<ThesisFinding> findings, List<ResearchRun> runs, List<ResearchRunOutput> outputs) { this.thesis=thesis; this.findings=findings; this.runs=runs; this.outputs=outputs; }
    public ResearchThesis getThesis(){return thesis;} public List<ThesisFinding> getFindings(){return findings;} public List<ResearchRun> getRuns(){return runs;} public List<ResearchRunOutput> getOutputs(){return outputs;}
}
