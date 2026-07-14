package com.finscope.domain.agent;

public class AgentActionFingerprint {
    /**
     * 节点名称。
     */
    private final String nodeName;
    /**
     * 目标对象类型。
     */
    private final String targetType;
    /**
     * 目标对象 ID。
     */
    private final String targetId;
    /**
     * 内容指纹。
     */
    private final String fingerprint;
    /**
     * 输入内容哈希。
     */
    private final String inputHash;

    private AgentActionFingerprint(String nodeName,
                                   String targetType,
                                   String targetId,
                                   String fingerprint,
                                   String inputHash) {
        this.nodeName = emptyIfNull(nodeName);
        this.targetType = emptyIfNull(targetType);
        this.targetId = emptyIfNull(targetId);
        this.fingerprint = emptyIfNull(fingerprint);
        this.inputHash = emptyIfNull(inputHash);
    }

    public static AgentActionFingerprint of(String nodeName,
                                            String targetType,
                                            String targetId,
                                            String fingerprint,
                                            String inputHash) {
        return new AgentActionFingerprint(nodeName, targetType, targetId, fingerprint, inputHash);
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getInputHash() {
        return inputHash;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
