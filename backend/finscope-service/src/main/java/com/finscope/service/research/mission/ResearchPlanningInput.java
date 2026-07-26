package com.finscope.service.research.mission;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchPlanningInput {
    private String question;
    private String subjectType;
    private String subjectName;
    private List<String> themeCodes = Collections.emptyList();
    private int maxActions;
    private LocalDate currentDate;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public List<String> getThemeCodes() {
        return themeCodes;
    }

    public void setThemeCodes(List<String> themeCodes) {
        this.themeCodes = themeCodes == null || themeCodes.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(themeCodes));
    }

    public int getMaxActions() {
        return maxActions;
    }

    public void setMaxActions(int maxActions) {
        this.maxActions = maxActions;
    }

    public LocalDate getCurrentDate() {
        return currentDate;
    }

    public void setCurrentDate(LocalDate currentDate) {
        this.currentDate = currentDate;
    }
}
