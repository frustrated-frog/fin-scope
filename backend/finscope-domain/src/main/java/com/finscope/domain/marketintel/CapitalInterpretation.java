package com.finscope.domain.marketintel;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class CapitalInterpretation {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 标的 ID。
     */
    private Long instrumentId;
    /**
     * 快照 ID。
     */
    private Long snapshotId;
    /**
     * 解读类型。
     */
    private String interpretationType;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 纯文本摘要。
     */
    private String plainSummary;
    /**
     * 事实记录。
     */
    private List<String> facts = Collections.emptyList();
    /**
     * 假设列表。
     */
    private List<CapitalHypothesis> hypotheses = Collections.emptyList();
    /**
     * 数据缺口列表。
     */
    private List<String> dataGaps = Collections.emptyList();
    /**
     * 观察要点列表。
     */
    private List<String> observationPoints = Collections.emptyList();
    /**
     * 免责声明或诚实说明。
     */
    private String disclaimer;
    /**
     * 兜底原因。
     */
    private String fallbackReason;
    /**
     * 规则版本。
     */
    private String ruleVersion;
    /**
     * 模型名称。
     */
    private String modelName;
    /**
     * 提示词版本。
     */
    private String promptVersion;
    /**
     * 输入内容哈希。
     */
    private String inputHash;
    /**
     * 输出内容哈希。
     */
    private String outputHash;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
    public void setFacts(List<String> values) { facts = immutable(values); }
    public void setHypotheses(List<CapitalHypothesis> values) { hypotheses = immutable(values); }
    public void setDataGaps(List<String> values) { dataGaps = immutable(values); }
    public void setObservationPoints(List<String> values) { observationPoints = immutable(values); }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }
}
