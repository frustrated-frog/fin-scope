package com.finscope.domain.research.mission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchMissionView {
    private ResearchMission mission;
    private List<ResearchMissionTask> tasks = Collections.emptyList();
    private List<ResearchMissionGap> gaps = Collections.emptyList();
    private List<ResearchToolDescriptor> tools = Collections.emptyList();

    public ResearchMission getMission() {
        return mission;
    }

    public void setMission(ResearchMission mission) {
        this.mission = mission;
    }

    public List<ResearchMissionTask> getTasks() {
        return tasks;
    }

    public void setTasks(List<ResearchMissionTask> tasks) {
        this.tasks = immutable(tasks);
    }

    public List<ResearchMissionGap> getGaps() {
        return gaps;
    }

    public void setGaps(List<ResearchMissionGap> gaps) {
        this.gaps = immutable(gaps);
    }

    public List<ResearchToolDescriptor> getTools() {
        return tools;
    }

    public void setTools(List<ResearchToolDescriptor> tools) {
        this.tools = immutable(tools);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null || values.isEmpty()
                ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
