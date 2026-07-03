package com.finscope.service.agent;

import com.finscope.domain.agent.AgentActionFingerprint;
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.agent.AgentRunContext;
import org.springframework.stereotype.Service;

@Service
public class AgentHarness {

    public <T> AgentNodeResult<T> runNode(AgentRunContext context,
                                          AgentActionFingerprint fingerprint,
                                          NodeExecutor<T> executor) {
        AgentRunContext actualContext = context == null
                ? AgentRunContext.start(null, null)
                : context;
        String nodeName = fingerprint == null ? "" : fingerprint.getNodeName();
        actualContext.enterNode(nodeName);

        AgentRunContext.ActionRecord actionRecord = actualContext.recordAction(fingerprint);
        if (actionRecord.isHardThresholdReached()) {
            return AgentNodeResult.skipped("REPEATED_ACTION",
                    "Repeated action reached hard threshold: " + actionRecord.getFingerprint());
        }

        try {
            return executor.execute(actualContext);
        } catch (Exception ex) {
            return AgentNodeResult.failed("UNKNOWN", ex.getMessage());
        }
    }

    public interface NodeExecutor<T> {
        AgentNodeResult<T> execute(AgentRunContext context) throws Exception;
    }
}
