package com.finscope.service.research.mission;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ResearchPlanValidator {
    private static final int MAX_TASKS = 8;
    private static final int MAX_SEARCH_TASKS = 4;
    private static final Pattern TASK_KEY = Pattern.compile("[a-z][a-z0-9_]{2,47}");
    private static final Pattern PROTOCOL_PREFIX = Pattern.compile("(?i)^\\s*[a-z][a-z0-9+.-]*://.*");
    private static final Set<String> INTENTS = new HashSet<String>(Arrays.asList(
            "BASELINE", "SUPPORT", "COUNTER", "PRIMARY", "BREADTH", "ASSESS", "SYNTHESIS"));
    private static final Set<String> TASK_TYPES = new HashSet<String>(Arrays.asList(
            "COLLECT", "SEARCH", "ASSESS", "SYNTHESIS"));

    private final ResearchToolRegistry toolRegistry;

    public ResearchPlanValidator(ResearchToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public ResearchMissionDraft validate(ResearchMissionDraft draft) {
        if (draft == null) {
            throw invalid("计划为空");
        }
        requireText(draft.getScopeSummary(), "研究范围", 240);
        if (draft.getSuccessCriteria() == null || draft.getSuccessCriteria().isEmpty()
                || draft.getSuccessCriteria().size() > 5) {
            throw invalid("成功条件必须为1到5条");
        }
        for (String criterion : draft.getSuccessCriteria()) {
            requireText(criterion, "成功条件", 100);
        }
        if (draft.getTasks() == null || draft.getTasks().size() < 4 || draft.getTasks().size() > MAX_TASKS) {
            throw invalid("任务数量必须为4到" + MAX_TASKS + "个");
        }

        Map<String, ResearchMissionTaskDraft> byKey = new LinkedHashMap<String, ResearchMissionTaskDraft>();
        int searchTasks = 0;
        for (ResearchMissionTaskDraft task : draft.getTasks()) {
            validateTask(task);
            if (byKey.put(task.getTaskKey(), task) != null) {
                throw invalid("任务键重复：" + task.getTaskKey());
            }
            if ("public_news_search".equals(task.getToolCode())) {
                searchTasks++;
            }
        }
        if (searchTasks > MAX_SEARCH_TASKS) {
            throw invalid("公开搜索任务不能超过" + MAX_SEARCH_TASKS + "个");
        }

        List<ResearchMissionTaskDraft> sorted = topologicalSort(byKey);
        requireContractTasks(sorted);
        draft.setTasks(sorted);
        return draft;
    }

    private void validateTask(ResearchMissionTaskDraft task) {
        if (task == null || task.getTaskKey() == null || !TASK_KEY.matcher(task.getTaskKey()).matches()) {
            throw invalid("任务键格式不合法");
        }
        requireText(task.getTitle(), "任务标题", 60);
        requireText(task.getQuestion(), "研究问题", 180);
        if (!TASK_TYPES.contains(task.getTaskType())) {
            throw invalid("任务类型不合法：" + task.getTaskType());
        }
        toolRegistry.required(task.getToolCode());
        if (!INTENTS.contains(task.getIntent())) {
            throw invalid("证据意图不合法：" + task.getIntent());
        }
        optionalText(task.getParallelGroup(), "并行组", 48);
        optionalText(task.getRationale(), "选择理由", 240);
        optionalText(task.getExpectedEvidence(), "预期证据", 180);
        if ("public_news_search".equals(task.getToolCode())) {
            requireText(task.getQueryText(), "搜索词", 180);
            if (PROTOCOL_PREFIX.matcher(task.getQueryText()).matches() || hasControlCharacter(task.getQueryText())) {
                throw invalid("搜索词包含协议头或控制字符");
            }
        } else if (task.getQueryText() != null && !task.getQueryText().trim().isEmpty()) {
            optionalText(task.getQueryText(), "查询文本", 180);
        }
        if (task.getDependencies() == null || task.getDependencies().size() > MAX_TASKS - 1) {
            throw invalid("任务依赖数量不合法：" + task.getTaskKey());
        }
    }

    private List<ResearchMissionTaskDraft> topologicalSort(Map<String, ResearchMissionTaskDraft> byKey) {
        Map<String, Integer> indegree = new LinkedHashMap<String, Integer>();
        Map<String, List<String>> dependents = new HashMap<String, List<String>>();
        for (String key : byKey.keySet()) {
            indegree.put(key, 0);
            dependents.put(key, new ArrayList<String>());
        }
        for (ResearchMissionTaskDraft task : byKey.values()) {
            Set<String> uniqueDependencies = new HashSet<String>();
            for (String dependency : task.getDependencies()) {
                if (!byKey.containsKey(dependency)) {
                    throw invalid("任务依赖不存在：" + dependency);
                }
                if (task.getTaskKey().equals(dependency) || !uniqueDependencies.add(dependency)) {
                    throw invalid("任务依赖重复或自引用：" + task.getTaskKey());
                }
                indegree.put(task.getTaskKey(), indegree.get(task.getTaskKey()) + 1);
                dependents.get(dependency).add(task.getTaskKey());
            }
        }

        Deque<String> ready = new ArrayDeque<String>();
        for (String key : byKey.keySet()) {
            if (indegree.get(key) == 0) {
                ready.addLast(key);
            }
        }
        List<ResearchMissionTaskDraft> sorted = new ArrayList<ResearchMissionTaskDraft>();
        while (!ready.isEmpty()) {
            String key = ready.removeFirst();
            sorted.add(byKey.get(key));
            for (String dependent : dependents.get(key)) {
                int next = indegree.get(dependent) - 1;
                indegree.put(dependent, next);
                if (next == 0) {
                    ready.addLast(dependent);
                }
            }
        }
        if (sorted.size() != byKey.size()) {
            throw invalid("任务依赖图存在循环");
        }
        return sorted;
    }

    private void requireContractTasks(List<ResearchMissionTaskDraft> tasks) {
        boolean baseline = false;
        boolean support = false;
        boolean counter = false;
        boolean assess = false;
        boolean synthesis = false;
        for (ResearchMissionTaskDraft task : tasks) {
            baseline |= "source_scan".equals(task.getToolCode()) && "BASELINE".equals(task.getIntent());
            support |= "public_news_search".equals(task.getToolCode()) && "SUPPORT".equals(task.getIntent());
            counter |= "public_news_search".equals(task.getToolCode()) && "COUNTER".equals(task.getIntent());
            assess |= "evidence_assess".equals(task.getToolCode()) && "ASSESS".equals(task.getIntent());
            synthesis |= "report_synthesis".equals(task.getToolCode()) && "SYNTHESIS".equals(task.getIntent());
        }
        if (!baseline || !support || !counter || !assess || !synthesis) {
            throw invalid("计划必须包含基线、支持、反方、证据判断和报告合成任务");
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(field + "不能为空");
        }
        if (value.trim().length() > maxLength || hasControlCharacter(value)) {
            throw invalid(field + "长度或字符不合法");
        }
    }

    private void optionalText(String value, String field, int maxLength) {
        if (value != null && !value.trim().isEmpty()) {
            requireText(value, field, maxLength);
        }
    }

    private boolean hasControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isISOControl(current) && !Character.isWhitespace(current)) {
                return true;
            }
        }
        return false;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("研究计划校验失败：" + message);
    }
}
