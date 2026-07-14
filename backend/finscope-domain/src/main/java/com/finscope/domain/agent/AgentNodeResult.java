package com.finscope.domain.agent;

public class AgentNodeResult<T> {
    /**
     * 当前状态。
     */
    private final String status;
    /**
     * 数值。
     */
    private final T value;
    /**
     * 输入摘要。
     */
    private final String inputSummary;
    /**
     * 输出摘要。
     */
    private final String outputSummary;
    /**
     * 错误类型。
     */
    private final String errorType;
    /**
     * 错误信息。
     */
    private final String errorMessage;
    /**
     * 是否使用兜底结果。
     */
    private final boolean fallbackUsed;
    /**
     * 兜底原因。
     */
    private final String fallbackReason;
    /**
     * 进度增量。
     */
    private final int progressDelta;

    private AgentNodeResult(String status,
                            T value,
                            String inputSummary,
                            String outputSummary,
                            String errorType,
                            String errorMessage,
                            boolean fallbackUsed,
                            String fallbackReason,
                            int progressDelta) {
        this.status = emptyIfNull(status);
        this.value = value;
        this.inputSummary = emptyIfNull(inputSummary);
        this.outputSummary = emptyIfNull(outputSummary);
        this.errorType = emptyIfNull(errorType);
        this.errorMessage = emptyIfNull(errorMessage);
        this.fallbackUsed = fallbackUsed;
        this.fallbackReason = emptyIfNull(fallbackReason);
        this.progressDelta = progressDelta;
    }

    public static <T> AgentNodeResult<T> success(T value,
                                                 String inputSummary,
                                                 String outputSummary,
                                                 int progressDelta) {
        return new AgentNodeResult<T>("SUCCESS", value, inputSummary, outputSummary,
                "", "", false, "", progressDelta);
    }

    public static <T> AgentNodeResult<T> skipped(String errorType, String errorMessage) {
        return new AgentNodeResult<T>("SKIPPED", null, "", "",
                errorType, errorMessage, false, "", 0);
    }

    public static <T> AgentNodeResult<T> failed(String errorType, String errorMessage) {
        return new AgentNodeResult<T>("FAILED", null, "", "",
                errorType, errorMessage, false, "", 0);
    }

    public String getStatus() {
        return status;
    }

    public T getValue() {
        return value;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public int getProgressDelta() {
        return progressDelta;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
