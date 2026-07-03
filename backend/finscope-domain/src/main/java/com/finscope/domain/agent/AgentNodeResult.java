package com.finscope.domain.agent;

public class AgentNodeResult<T> {
    private final String status;
    private final T value;
    private final String inputSummary;
    private final String outputSummary;
    private final String errorType;
    private final String errorMessage;
    private final boolean fallbackUsed;
    private final String fallbackReason;
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
