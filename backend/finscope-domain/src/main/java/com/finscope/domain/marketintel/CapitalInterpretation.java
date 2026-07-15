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
    /** 结构化市场状态。 */
    private String marketState;
    /** 面向用户的首屏结论。 */
    private String executiveSummary;
    /** 按量能、资金、订单、协同、日内等维度组织的观察。 */
    private List<CapitalInterpretationObservation> observations = Collections.emptyList();
    /** 与主结论相反的证据。 */
    private List<String> counterEvidence = Collections.emptyList();
    /** 经服务端验证后的观察条件引用。 */
    private List<String> watchConditionRefs = Collections.emptyList();
    /** 本次结论总体置信度。 */
    private String confidence;
    private String factorVersion;
    private String signalVersion;
    /** 页面可解析、可展示的原始证据。 */
    private List<CapitalEvidenceRef> evidenceRefs = Collections.emptyList();
    /** 被服务端证据门禁拒绝的模型输出数量。 */
    private int rejectedOutputCount;
    private List<String> rejectionReasons = Collections.emptyList();
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
    public void setObservations(List<CapitalInterpretationObservation> values) { observations = immutable(values); }
    public void setCounterEvidence(List<String> values) { counterEvidence = immutable(values); }
    public void setWatchConditionRefs(List<String> values) { watchConditionRefs = immutable(values); }
    public void setEvidenceRefs(List<CapitalEvidenceRef> values) { evidenceRefs = immutable(values); }
    public void setRejectionReasons(List<String> values) { rejectionReasons = immutable(values); }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }
}
