package com.finscope.domain.factorresearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可审计、可版本化的专业因子定义。
 */
public final class ResearchFactorDefinition {
    private final FactorIdentity identity;
    private final String name;
    private final String category;
    private final String frequency;
    private final String expectedDirection;
    private final String plainMeaning;
    private final String hypothesis;
    private final String economicRationale;
    private final String interpretationBoundary;
    private final List<String> requiredFields;
    private final String availableAtRule;
    private final String missingPolicy;
    private final String calculationKey;
    private final String calculationVersion;
    private final String sourceType;
    private final String sourceRef;
    private final String evaluationPolicyCode;
    private final String evaluationPolicyVersion;
    private final FactorLifecycleStatus status;
    private final String validationEvidenceRef;

    private ResearchFactorDefinition(Builder builder) {
        this.identity = required(builder.identity, "identity");
        this.name = required(builder.name, "name");
        this.category = required(builder.category, "category");
        this.frequency = required(builder.frequency, "frequency");
        this.expectedDirection = required(builder.expectedDirection, "expectedDirection");
        this.plainMeaning = required(builder.plainMeaning, "plainMeaning");
        this.hypothesis = required(builder.hypothesis, "hypothesis");
        this.economicRationale = required(builder.economicRationale, "economicRationale");
        this.interpretationBoundary = required(builder.interpretationBoundary, "interpretationBoundary");
        this.requiredFields = immutableRequiredStrings(builder.requiredFields, "requiredFields");
        this.availableAtRule = required(builder.availableAtRule, "availableAtRule");
        this.missingPolicy = required(builder.missingPolicy, "missingPolicy");
        this.calculationKey = required(builder.calculationKey, "calculationKey");
        this.calculationVersion = required(builder.calculationVersion, "calculationVersion");
        this.sourceType = required(builder.sourceType, "sourceType");
        this.sourceRef = required(builder.sourceRef, "sourceRef");
        this.evaluationPolicyCode = required(builder.evaluationPolicyCode, "evaluationPolicyCode");
        this.evaluationPolicyVersion = required(builder.evaluationPolicyVersion, "evaluationPolicyVersion");
        this.status = required(builder.status, "status");
        this.validationEvidenceRef = optional(builder.validationEvidenceRef);
        if (requiresValidationEvidence(status) && validationEvidenceRef == null) {
            throw new IllegalArgumentException("validationEvidenceRef is required for " + status);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public FactorIdentity getIdentity() {
        return identity;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getExpectedDirection() {
        return expectedDirection;
    }

    public String getPlainMeaning() {
        return plainMeaning;
    }

    public String getHypothesis() {
        return hypothesis;
    }

    public String getEconomicRationale() {
        return economicRationale;
    }

    public String getInterpretationBoundary() {
        return interpretationBoundary;
    }

    public List<String> getRequiredFields() {
        return requiredFields;
    }

    public String getAvailableAtRule() {
        return availableAtRule;
    }

    public String getMissingPolicy() {
        return missingPolicy;
    }

    public String getCalculationKey() {
        return calculationKey;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getEvaluationPolicyCode() {
        return evaluationPolicyCode;
    }

    public String getEvaluationPolicyVersion() {
        return evaluationPolicyVersion;
    }

    public FactorLifecycleStatus getStatus() {
        return status;
    }

    public String getValidationEvidenceRef() {
        return validationEvidenceRef;
    }

    private static boolean requiresValidationEvidence(FactorLifecycleStatus value) {
        return value == FactorLifecycleStatus.VALIDATED
                || value == FactorLifecycleStatus.PRODUCTION_ELIGIBLE;
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static List<String> immutableRequiredStrings(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        List<String> copy = new ArrayList<String>(values.size());
        for (String value : values) {
            copy.add(required(value, field));
        }
        return Collections.unmodifiableList(copy);
    }

    public static final class Builder {
        private FactorIdentity identity;
        private String name;
        private String category;
        private String frequency;
        private String expectedDirection;
        private String plainMeaning;
        private String hypothesis;
        private String economicRationale;
        private String interpretationBoundary;
        private List<String> requiredFields;
        private String availableAtRule;
        private String missingPolicy;
        private String calculationKey;
        private String calculationVersion;
        private String sourceType;
        private String sourceRef;
        private String evaluationPolicyCode;
        private String evaluationPolicyVersion;
        private FactorLifecycleStatus status;
        private String validationEvidenceRef;

        private Builder() {
        }

        public Builder identity(FactorIdentity value) { this.identity = value; return this; }
        public Builder name(String value) { this.name = value; return this; }
        public Builder category(String value) { this.category = value; return this; }
        public Builder frequency(String value) { this.frequency = value; return this; }
        public Builder expectedDirection(String value) { this.expectedDirection = value; return this; }
        public Builder plainMeaning(String value) { this.plainMeaning = value; return this; }
        public Builder hypothesis(String value) { this.hypothesis = value; return this; }
        public Builder economicRationale(String value) { this.economicRationale = value; return this; }
        public Builder interpretationBoundary(String value) { this.interpretationBoundary = value; return this; }
        public Builder requiredFields(List<String> value) { this.requiredFields = value; return this; }
        public Builder availableAtRule(String value) { this.availableAtRule = value; return this; }
        public Builder missingPolicy(String value) { this.missingPolicy = value; return this; }
        public Builder calculationKey(String value) { this.calculationKey = value; return this; }
        public Builder calculationVersion(String value) { this.calculationVersion = value; return this; }
        public Builder sourceType(String value) { this.sourceType = value; return this; }
        public Builder sourceRef(String value) { this.sourceRef = value; return this; }
        public Builder evaluationPolicyCode(String value) { this.evaluationPolicyCode = value; return this; }
        public Builder evaluationPolicyVersion(String value) { this.evaluationPolicyVersion = value; return this; }
        public Builder status(FactorLifecycleStatus value) { this.status = value; return this; }
        public Builder validationEvidenceRef(String value) { this.validationEvidenceRef = value; return this; }

        public ResearchFactorDefinition build() {
            return new ResearchFactorDefinition(this);
        }
    }
}
