package com.finscope.domain.factorresearch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResearchFactorContractTest {

    @Test
    void factorIdentityIsVersionedImmutableValueObject() {
        FactorIdentity identity = new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0");

        assertEquals("capital", identity.getNamespace());
        assertEquals("MAIN_FLOW_SHARE", identity.getCode());
        assertEquals("1.0.0", identity.getVersion());
        assertEquals(identity, new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"));
        assertEquals(identity.hashCode(), new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0").hashCode());
        assertNotEquals(identity, new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.1"));

        assertThrows(IllegalArgumentException.class, () -> new FactorIdentity(null, "CODE", "1"));
        assertThrows(IllegalArgumentException.class, () -> new FactorIdentity(" ", "CODE", "1"));
        assertThrows(IllegalArgumentException.class, () -> new FactorIdentity("capital", "", "1"));
        assertThrows(IllegalArgumentException.class, () -> new FactorIdentity("capital", "CODE", "  "));
    }

    @Test
    void exposesTheA1Lifecycle() {
        assertArrayEquals(new FactorLifecycleStatus[]{
                        FactorLifecycleStatus.CANDIDATE,
                        FactorLifecycleStatus.DEFINITION_REVIEWED,
                        FactorLifecycleStatus.IMPLEMENTED,
                        FactorLifecycleStatus.CALCULATION_VERIFIED,
                        FactorLifecycleStatus.EXPLORATORY,
                        FactorLifecycleStatus.VALIDATED,
                        FactorLifecycleStatus.PRODUCTION_ELIGIBLE,
                        FactorLifecycleStatus.INVALIDATED,
                        FactorLifecycleStatus.RETIRED
                },
                FactorLifecycleStatus.values());
    }

    @Test
    void requiresCompleteProfessionalFactorMetadata() {
        assertThrows(IllegalArgumentException.class, () -> ResearchFactorDefinition.builder()
                .identity(new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"))
                .name("主力流入强度")
                .build());

        assertThrows(IllegalArgumentException.class, () -> validBuilder().identity(null).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().name(" ").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().category(null).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().frequency("").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().expectedDirection(" ").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().plainMeaning(null).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().hypothesis("").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().economicRationale(" ").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().interpretationBoundary(null).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().requiredFields(null).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().requiredFields(new ArrayList<String>()).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().requiredFields(Arrays.asList("amount", " ")).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().availableAtRule(" ").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().missingPolicy(null).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().calculationKey("").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().calculationVersion(" ").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().sourceType(null).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().sourceRef("").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().evaluationPolicyCode(" ").build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().evaluationPolicyVersion(null).build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder().status(null).build());
    }

    @Test
    void defensivelyCopiesAndProtectsListInputs() {
        List<String> requiredFields = new ArrayList<String>();
        requiredFields.add("mainNetInflow");
        ResearchFactorDefinition definition = validBuilder().requiredFields(requiredFields).build();

        requiredFields.clear();

        assertEquals(Arrays.asList("mainNetInflow"), definition.getRequiredFields());
        assertThrows(UnsupportedOperationException.class,
                () -> definition.getRequiredFields().add("tradeAmount"));
    }

    @Test
    void validatedLifecycleRequiresEvidenceReference() {
        assertThrows(IllegalArgumentException.class, () -> validBuilder()
                .status(FactorLifecycleStatus.VALIDATED)
                .build());
        assertThrows(IllegalArgumentException.class, () -> validBuilder()
                .status(FactorLifecycleStatus.PRODUCTION_ELIGIBLE)
                .validationEvidenceRef(" ")
                .build());

        for (FactorLifecycleStatus status : Arrays.asList(
                FactorLifecycleStatus.CANDIDATE,
                FactorLifecycleStatus.DEFINITION_REVIEWED,
                FactorLifecycleStatus.IMPLEMENTED,
                FactorLifecycleStatus.CALCULATION_VERIFIED,
                FactorLifecycleStatus.EXPLORATORY)) {
            assertNull(validBuilder().status(status).build().getValidationEvidenceRef());
        }

        assertEquals("validation:capital-main-flow-share-v1",
                validBuilder()
                        .status(FactorLifecycleStatus.VALIDATED)
                        .validationEvidenceRef("validation:capital-main-flow-share-v1")
                        .build()
                        .getValidationEvidenceRef());
    }

    @Test
    void factorObservationRequiresAuditAndPointInTimeFields() {
        FactorIdentity identity = identity();
        LocalDate tradeDate = LocalDate.of(2026, 7, 14);
        LocalDateTime availableAt = LocalDateTime.of(2026, 7, 14, 15, 5);
        BigDecimal raw = new BigDecimal("0.1500000000");
        BigDecimal processed = new BigDecimal("0.1500000000");

        FactorObservation observation = new FactorObservation(
                "capital-flow-daily-v1", "600519.SH", tradeDate, availableAt, identity,
                raw, processed, "COMPLETE", "source-fp", "calculation-fp");

        assertEquals("capital-flow-daily-v1", observation.getDatasetId());
        assertEquals("600519.SH", observation.getInstrumentCode());
        assertEquals(tradeDate, observation.getTradeDate());
        assertEquals(availableAt, observation.getAvailableAt());
        assertEquals(identity, observation.getIdentity());
        assertEquals(raw, observation.getRawValue());
        assertEquals(processed, observation.getProcessedValue());
        assertEquals("COMPLETE", observation.getQualityStatus());
        assertEquals("source-fp", observation.getSourceFingerprint());
        assertEquals("calculation-fp", observation.getCalculationFingerprint());

        assertThrows(IllegalArgumentException.class, () -> observation(null, "600519.SH", tradeDate,
                availableAt, identity, raw, processed, "COMPLETE", "source-fp", "calculation-fp"));
        assertThrows(IllegalArgumentException.class, () -> observation("dataset", " ", tradeDate,
                availableAt, identity, raw, processed, "COMPLETE", "source-fp", "calculation-fp"));
        assertThrows(IllegalArgumentException.class, () -> observation("dataset", "600519.SH", null,
                availableAt, identity, raw, processed, "COMPLETE", "source-fp", "calculation-fp"));
        assertThrows(IllegalArgumentException.class, () -> observation("dataset", "600519.SH", tradeDate,
                null, identity, raw, processed, "COMPLETE", "source-fp", "calculation-fp"));
        assertThrows(IllegalArgumentException.class, () -> observation("dataset", "600519.SH", tradeDate,
                availableAt, null, raw, processed, "COMPLETE", "source-fp", "calculation-fp"));
        assertThrows(IllegalArgumentException.class, () -> observation("dataset", "600519.SH", tradeDate,
                availableAt, identity, raw, processed, " ", "source-fp", "calculation-fp"));
        assertThrows(IllegalArgumentException.class, () -> observation("dataset", "600519.SH", tradeDate,
                availableAt, identity, raw, processed, "COMPLETE", " ", "calculation-fp"));
        assertThrows(IllegalArgumentException.class, () -> observation("dataset", "600519.SH", tradeDate,
                availableAt, identity, raw, processed, "COMPLETE", "source-fp", null));
    }

    @Test
    void completeObservationRequiresBothValuesButIncompleteObservationMayOmitThem() {
        assertThrows(IllegalArgumentException.class, () -> observation("dataset", "600519.SH",
                LocalDate.of(2026, 7, 14), LocalDateTime.of(2026, 7, 14, 15, 5), identity(),
                null, BigDecimal.ONE, "COMPLETE", "source-fp", "calculation-fp"));
        assertThrows(IllegalArgumentException.class, () -> observation("dataset", "600519.SH",
                LocalDate.of(2026, 7, 14), LocalDateTime.of(2026, 7, 14, 15, 5), identity(),
                BigDecimal.ONE, null, "COMPLETE", "source-fp", "calculation-fp"));

        FactorObservation missing = observation("dataset", "600519.SH",
                LocalDate.of(2026, 7, 14), LocalDateTime.of(2026, 7, 14, 15, 5), identity(),
                null, null, "MISSING_INPUT", "source-fp", "calculation-fp");

        assertNull(missing.getRawValue());
        assertNull(missing.getProcessedValue());
    }

    private ResearchFactorDefinition.Builder validBuilder() {
        return ResearchFactorDefinition.builder()
                .identity(identity())
                .name("主力流入强度")
                .category("CAPITAL_FLOW")
                .frequency("DAILY")
                .expectedDirection("POSITIVE")
                .plainMeaning("主力净流入占当日成交额的比例")
                .hypothesis("更强的主力净流入可能对应更强的后续相对收益")
                .economicRationale("持续的大额主动买入可能反映新增信息定价")
                .interpretationBoundary("仅描述资金行为，不单独构成收益预测")
                .requiredFields(Arrays.asList("mainNetInflow", "tradeAmount"))
                .availableAtRule("retrievedAt")
                .missingPolicy("MARK_MISSING_INPUT")
                .calculationKey("MAIN_FLOW_SHARE")
                .calculationVersion("capital-main-flow-share-v1")
                .sourceType("INTERNAL")
                .sourceRef("capital-flow-daily")
                .evaluationPolicyCode("CROSS_SECTIONAL_FACTOR_V1")
                .evaluationPolicyVersion("1.0.0")
                .status(FactorLifecycleStatus.CANDIDATE);
    }

    private FactorIdentity identity() {
        return new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0");
    }

    private FactorObservation observation(String datasetId, String instrumentCode, LocalDate tradeDate,
                                          LocalDateTime availableAt, FactorIdentity identity,
                                          BigDecimal rawValue, BigDecimal processedValue, String qualityStatus,
                                          String sourceFingerprint, String calculationFingerprint) {
        return new FactorObservation(datasetId, instrumentCode, tradeDate, availableAt, identity,
                rawValue, processedValue, qualityStatus, sourceFingerprint, calculationFingerprint);
    }
}
