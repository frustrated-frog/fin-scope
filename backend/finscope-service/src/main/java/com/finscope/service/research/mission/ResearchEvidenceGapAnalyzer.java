package com.finscope.service.research.mission;

import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.service.research.report.EvidenceSufficiency;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ResearchEvidenceGapAnalyzer {
    public ResearchMissionGap assess(Long runId, String afterTaskKey, EvidenceSufficiency sufficiency) {
        if (runId == null || sufficiency == null) {
            throw new IllegalArgumentException("研究运行和证据评估不能为空");
        }
        ResearchMissionGap gap = new ResearchMissionGap();
        gap.setResearchRunId(runId);
        gap.setAfterTaskKey(afterTaskKey);
        gap.setSufficient(sufficiency.isSufficient());
        gap.setEvidenceCount(sufficiency.getEvidenceCount());
        gap.setSourceCount(sufficiency.getSourceCount());
        gap.setSupportCount(sufficiency.getSupportCount());
        gap.setCounterCount(sufficiency.getCounterCount());
        gap.setWarnings(sufficiency.getWarnings());
        gap.setRecommendedIntent(recommendedIntent(sufficiency));
        gap.setStateHash(stateHash(gap));
        return gap;
    }

    private String recommendedIntent(EvidenceSufficiency value) {
        if (value.isSufficient()) {
            return "NONE";
        }
        if (value.getCounterCount() == 0) {
            return "COUNTER";
        }
        if (value.getSupportCount() == 0) {
            return "SUPPORT";
        }
        if (value.getSourceCount() < 2) {
            return "PRIMARY";
        }
        return "BREADTH";
    }

    private String stateHash(ResearchMissionGap gap) {
        List<String> warnings = new ArrayList<String>(gap.getWarnings());
        Collections.sort(warnings);
        String canonical = gap.getEvidenceCount() + "|" + gap.getSourceCount() + "|"
                + gap.getSupportCount() + "|" + gap.getCounterCount() + "|"
                + gap.isSufficient() + "|" + gap.getRecommendedIntent() + "|"
                + String.join("\u001F", warnings);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                value.append(String.format("%02x", item & 0xff));
            }
            return value.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("JDK does not provide SHA-256", impossible);
        }
    }
}
