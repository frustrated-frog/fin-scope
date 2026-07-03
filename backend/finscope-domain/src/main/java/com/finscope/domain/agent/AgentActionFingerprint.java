package com.finscope.domain.agent;

public class AgentActionFingerprint {
    private final String nodeName;
    private final String targetType;
    private final String targetId;
    private final String fingerprint;
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
