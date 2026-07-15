package com.finscope.domain.marketintel;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public final class CapitalFactorDefinition {
    public enum ExpressionKind { DECLARATIVE, CALCULATOR }
    public enum SourceType { INTERNAL, QLIB, FORMULAIC_ALPHA_101, PAPER, OSS_PROJECT }
    public enum AdaptationType { ORIGINAL, ADAPTED, PROXY }
    public enum EvaluationStatus { UNTESTED, EXPLORATORY, VALIDATED, INVALIDATED }
    public enum AdmissionStatus { CANDIDATE, FEASIBILITY_PASSED, IMPLEMENTED, TESTED, PUBLISHED, REJECTED }

    private final String code;
    private final String name;
    private final String category;
    private final String description;
    private final ExpressionKind expressionKind;
    private final String canonicalFormula;
    private final String calculationKey;
    private final List<String> requiredFields;
    private final String window;
    private final int minimumSamples;
    private final SourceType sourceType;
    private final String sourceRef;
    private final AdaptationType adaptationType;
    private final String calculationVersion;
    private final EvaluationStatus evaluationStatus;
    private final AdmissionStatus admissionStatus;
    private final String interpretationBoundary;

    private CapitalFactorDefinition(Builder builder) {
        this.code = required(builder.code, "code");
        this.name = required(builder.name, "name");
        this.category = required(builder.category, "category");
        this.description = required(builder.description, "description");
        this.expressionKind = required(builder.expressionKind, "expressionKind");
        this.canonicalFormula = required(builder.canonicalFormula, "canonicalFormula");
        this.calculationKey = required(builder.calculationKey, "calculationKey");
        this.requiredFields = Collections.unmodifiableList(new ArrayList<String>(builder.requiredFields));
        this.window = required(builder.window, "window");
        if (builder.minimumSamples < 1) throw new IllegalArgumentException("minimumSamples must be positive");
        this.minimumSamples = builder.minimumSamples;
        this.sourceType = required(builder.sourceType, "sourceType");
        this.sourceRef = required(builder.sourceRef, "sourceRef");
        this.adaptationType = required(builder.adaptationType, "adaptationType");
        this.calculationVersion = required(builder.calculationVersion, "calculationVersion");
        this.evaluationStatus = required(builder.evaluationStatus, "evaluationStatus");
        this.admissionStatus = required(builder.admissionStatus, "admissionStatus");
        this.interpretationBoundary = required(builder.interpretationBoundary, "interpretationBoundary");
    }

    public static Builder builder(String code, String name) { return new Builder(code, name); }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    public static final class Builder {
        private final String code;
        private final String name;
        private String category;
        private String description;
        private ExpressionKind expressionKind;
        private String canonicalFormula;
        private String calculationKey;
        private List<String> requiredFields = Collections.emptyList();
        private String window;
        private int minimumSamples;
        private SourceType sourceType;
        private String sourceRef;
        private AdaptationType adaptationType;
        private String calculationVersion;
        private EvaluationStatus evaluationStatus;
        private AdmissionStatus admissionStatus;
        private String interpretationBoundary;

        private Builder(String code, String name) { this.code = code; this.name = name; }
        public Builder category(String value) { category = value; return this; }
        public Builder description(String value) { description = value; return this; }
        public Builder expressionKind(ExpressionKind value) { expressionKind = value; return this; }
        public Builder canonicalFormula(String value) { canonicalFormula = value; return this; }
        public Builder calculationKey(String value) { calculationKey = value; return this; }
        public Builder requiredFields(List<String> value) { requiredFields = value == null ? Collections.emptyList() : value; return this; }
        public Builder window(String value) { window = value; return this; }
        public Builder minimumSamples(int value) { minimumSamples = value; return this; }
        public Builder sourceType(SourceType value) { sourceType = value; return this; }
        public Builder sourceRef(String value) { sourceRef = value; return this; }
        public Builder adaptationType(AdaptationType value) { adaptationType = value; return this; }
        public Builder calculationVersion(String value) { calculationVersion = value; return this; }
        public Builder evaluationStatus(EvaluationStatus value) { evaluationStatus = value; return this; }
        public Builder admissionStatus(AdmissionStatus value) { admissionStatus = value; return this; }
        public Builder interpretationBoundary(String value) { interpretationBoundary = value; return this; }
        public CapitalFactorDefinition build() { return new CapitalFactorDefinition(this); }
    }
}
