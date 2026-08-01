package com.finscope.service.research.method;

import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ResearchMethodCompletionPolicy {
    private final ResearchMethodRegistry registry;

    public ResearchMethodCompletionPolicy() {
        this(ResearchMethodRegistry.defaults());
    }

    @Autowired
    public ResearchMethodCompletionPolicy(ResearchMethodRegistry registry) {
        this.registry = registry;
    }

    public List<String> missingConditions(ResearchMission mission, List<ResearchMissionTask> tasks) {
        if (mission == null || mission.getMethodCodes() == null || mission.getMethodCodes().isEmpty()) {
            return Collections.emptyList();
        }
        List<ResearchMissionTask> values = tasks == null ? Collections.<ResearchMissionTask>emptyList() : tasks;
        List<String> missing = new ArrayList<String>();
        for (String code : mission.getMethodCodes()) {
            ResearchMethodDefinition definition;
            try {
                definition = registry.required(code);
            } catch (IllegalArgumentException error) {
                missing.add("投研方法未注册：" + code);
                continue;
            }
            for (String intent : definition.getRequiredIntents()) {
                if (!hasTerminalIntent(values, intent)) {
                    missing.add("投研方法 " + code + " 尚未完成 " + intent + " 意图");
                }
            }
        }
        return missing;
    }

    private boolean hasTerminalIntent(List<ResearchMissionTask> tasks, String intent) {
        for (ResearchMissionTask task : tasks) {
            if (intent.equals(task.getIntent())
                    && ("COMPLETED".equals(task.getStatus())
                    || ("SKIPPED".equals(task.getStatus())
                    && "SUFFICIENT_EVIDENCE".equals(task.getSkipReason())))) {
                return true;
            }
        }
        return false;
    }
}
