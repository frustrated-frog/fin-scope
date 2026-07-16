package com.finscope.service.factorresearch;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.factorresearch.QuantCapitalFlowRepository;
import com.finscope.dao.factorresearch.QuantDatasetPartitionRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantDatasetPartition;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.service.quant.data.QuantDatasetFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapitalFlowFreezeServiceTest {
    private static final Long DATASET_ID = 7L;
    private static final LocalDate DATE = LocalDate.of(2026, 7, 14);
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 7, 15, 9, 0);

    private QuantDatasetRepository datasets;
    private CapitalFlowRepository sourceFlows;
    private InstrumentRepository instruments;
    private QuantMarketDataRepository marketData;
    private QuantCapitalFlowRepository capitalFlows;
    private QuantDatasetPartitionRepository partitions;
    private CapitalFlowFreezeService service;

    @BeforeEach
    void setUp() {
        datasets = mock(QuantDatasetRepository.class);
        sourceFlows = mock(CapitalFlowRepository.class);
        instruments = mock(InstrumentRepository.class);
        marketData = mock(QuantMarketDataRepository.class);
        capitalFlows = mock(QuantCapitalFlowRepository.class);
        partitions = mock(QuantDatasetPartitionRepository.class);
        service = new CapitalFlowFreezeService();
        ReflectionTestUtils.setField(service, "datasets", datasets);
        ReflectionTestUtils.setField(service, "sourceFlows", sourceFlows);
        ReflectionTestUtils.setField(service, "instruments", instruments);
        ReflectionTestUtils.setField(service, "marketData", marketData);
        ReflectionTestUtils.setField(service, "capitalFlows", capitalFlows);
        ReflectionTestUtils.setField(service, "partitions", partitions);
        ReflectionTestUtils.setField(service, "fingerprint", new QuantDatasetFingerprint());
        stubCompleteFixture();
    }

    @Test
    void usesRetrievedAtAsAvailableAtAndNeverObservedAt() {
        CapitalFlowPoint point = completePoint(11L, DATE, DATE.atTime(18, 3));
        point.setObservedAt(DATE.atTime(15, 0));
        when(sourceFlows.findDailyPointInTime(eq(DATE), eq(DATE), eq(AS_OF), any())).thenReturn(Collections.singletonList(point));

        service.freeze(DATASET_ID, DATE, DATE, AS_OF);

        QuantCapitalFlowDaily frozen = capturedRows().get(0);
        assertEquals(point.getRetrievedAt(), frozen.getAvailableAt());
        assertNotEquals(point.getObservedAt(), frozen.getAvailableAt());
    }

    @Test
    void blocksBackfilledHistoryThatWasNotAvailableOnSignalDates() {
        CapitalFlowPoint point = completePoint(11L, DATE, DATE.plusDays(1).atTime(8, 0));
        when(sourceFlows.findDailyPointInTime(eq(DATE), eq(DATE), eq(AS_OF), any())).thenReturn(Collections.singletonList(point));

        service.freeze(DATASET_ID, DATE, DATE, AS_OF);

        assertEquals("POINT_IN_TIME_BLOCKED", capturedRows().get(0).getQualityStatus());
        verify(datasets).updateResearchState(eq(DATASET_ID), eq(DATE), eq(DATE), eq("BLOCKED"),
                eq(AS_OF), eq("quant-dataset-v2"), anyString(), anyString(),
                org.mockito.ArgumentMatchers.contains("backfilled"), eq(3L));
    }

    @Test
    void mapsMarketIntelInstrumentToCanonicalQuantCode() {
        Instrument instrument = instrument(11L, "600519", "SH");
        when(instruments.findAll()).thenReturn(Collections.singletonList(instrument));

        service.freeze(DATASET_ID, DATE, DATE, AS_OF);

        assertEquals("600519.SH", capturedRows().get(0).getInstrumentCode());
    }

    @Test
    void excludesRowsOutsideDateAwareUniverseAndBlocksWithGap() {
        QuantUniverseMember removed = member("600519.SH", DATE, false);
        when(marketData.findUniverseMembers(DATASET_ID)).thenReturn(Collections.singletonList(removed));

        service.freeze(DATASET_ID, DATE, DATE, AS_OF);

        verify(capitalFlows, never()).saveAll(any());
        ArgumentCaptor<QuantDatasetPartition> partition = ArgumentCaptor.forClass(QuantDatasetPartition.class);
        verify(partitions).save(partition.capture());
        assertEquals(0, partition.getValue().getRowCount());
        assertEquals("BLOCKED", partition.getValue().getQualityStatus());
        verify(datasets).updateResearchState(eq(DATASET_ID), any(), any(), eq("BLOCKED"),
                eq(AS_OF), eq("quant-dataset-v2"), anyString(), anyString(),
                org.mockito.ArgumentMatchers.contains("noActiveUniverse"), eq(3L));
    }

    @Test
    void ignoresGlobalSourceRowsOutsideThisDatasetUniverse() {
        Instrument unrelated = instrument(22L, "000001", "SZ");
        when(instruments.findAll()).thenReturn(Arrays.asList(
                instrument(11L, "600519", "SH"), unrelated));
        when(sourceFlows.findDailyPointInTime(eq(DATE), eq(DATE), eq(AS_OF), any())).thenReturn(Arrays.asList(
                completePoint(11L, DATE, DATE.atTime(18, 0)),
                completePoint(22L, DATE, DATE.atTime(18, 0))));

        service.freeze(DATASET_ID, DATE, DATE, AS_OF);

        assertEquals(1, capturedRows().size());
        verify(datasets).updateResearchState(eq(DATASET_ID), any(), any(), eq("READY"),
                eq(AS_OF), eq("quant-dataset-v2"), anyString(), anyString(),
                org.mockito.ArgumentMatchers.contains("\"blockingIssues\":0"), eq(3L));
    }

    @Test
    void blocksWhenActiveUniverseMemberHasNoMarketBarOrCapitalFlow() {
        when(marketData.findUniverseMembers(DATASET_ID)).thenReturn(Arrays.asList(
                member("600519.SH", DATE, true), member("000001.SZ", DATE, true)));

        service.freeze(DATASET_ID, DATE, DATE, AS_OF);

        verify(datasets).updateResearchState(eq(DATASET_ID), any(), any(), eq("BLOCKED"),
                eq(AS_OF), eq("quant-dataset-v2"), anyString(), anyString(),
                org.mockito.ArgumentMatchers.contains("missingMarketBar"), eq(3L));
    }

    @Test
    void rejectsPartialOrMissingAmountWithoutCallingItComplete() {
        CapitalFlowPoint partial = completePoint(11L, DATE, DATE.atTime(18, 0));
        partial.setQualityStatus("PARTIAL");
        partial.setIntervalTradeAmount(null);
        when(sourceFlows.findDailyPointInTime(eq(DATE), eq(DATE), eq(AS_OF), any())).thenReturn(Collections.singletonList(partial));

        service.freeze(DATASET_ID, DATE, DATE, AS_OF);

        QuantCapitalFlowDaily frozen = capturedRows().get(0);
        assertNotEquals("COMPLETE", frozen.getQualityStatus());
        verify(datasets).updateResearchState(eq(DATASET_ID), any(), any(), eq("BLOCKED"),
                eq(AS_OF), eq("quant-dataset-v2"), anyString(), anyString(), anyString(), eq(3L));
    }

    @Test
    void includesCapitalPartitionAndAsOfInDatasetFingerprint() {
        QuantDatasetFingerprint fingerprint = new QuantDatasetFingerprint();
        QuantDataset first = researchDataset();
        first.setAsOfTime(AS_OF);
        QuantDataset second = researchDataset();
        second.setAsOfTime(AS_OF.plusSeconds(1));
        QuantDatasetPartition partition = partition("capital-a");
        String original = fingerprint.datasetV2(first, Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(member("600519.SH", DATE, true)), Collections.singletonList(partition));
        partition.setPartitionFingerprint("capital-b");
        String changedPartition = fingerprint.datasetV2(first, Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(member("600519.SH", DATE, true)), Collections.singletonList(partition));
        String changedAsOf = fingerprint.datasetV2(second, Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(member("600519.SH", DATE, true)), Collections.singletonList(partition));

        assertNotEquals(original, changedPartition);
        assertNotEquals(changedPartition, changedAsOf);
    }

    @Test
    void repeatedInputOrderProducesSamePartitionAndDatasetFingerprints() {
        QuantDatasetFingerprint fingerprint = new QuantDatasetFingerprint();
        QuantCapitalFlowDaily first = frozen("600519.SH", DATE, "source-a");
        QuantCapitalFlowDaily second = frozen("000001.SZ", DATE.plusDays(1), "source-b");
        assertEquals(fingerprint.capitalPartition(Arrays.asList(first, second)),
                fingerprint.capitalPartition(Arrays.asList(second, first)));

        QuantDataset dataset = researchDataset();
        dataset.setAsOfTime(AS_OF);
        QuantDatasetPartition capital = partition("capital");
        QuantDatasetPartition bars = partition("bars");
        bars.setPartitionType("BARS");
        assertEquals(
                fingerprint.datasetV2(dataset, Collections.emptyList(), Collections.emptyList(),
                        Collections.singletonList(member("600519.SH", DATE, true)), Arrays.asList(capital, bars)),
                fingerprint.datasetV2(dataset, Collections.emptyList(), Collections.emptyList(),
                        Collections.singletonList(member("600519.SH", DATE, true)), Arrays.asList(bars, capital)));
    }

    @Test
    void changingCapitalProvenanceChangesFingerprint() {
        QuantDatasetFingerprint fingerprint = new QuantDatasetFingerprint();
        QuantCapitalFlowDaily first = frozen("600519.SH", DATE, "payload-a");
        QuantCapitalFlowDaily changed = frozen("600519.SH", DATE, "payload-b");

        assertNotEquals(fingerprint.capitalPartition(Collections.singletonList(first)),
                fingerprint.capitalPartition(Collections.singletonList(changed)));
    }

    @Test
    void reachesReadyForTruthfulSameDayCompleteRows() {
        service.freeze(DATASET_ID, DATE, DATE, AS_OF);

        QuantCapitalFlowDaily frozen = capturedRows().get(0);
        assertEquals("COMPLETE", frozen.getQualityStatus());
        assertEquals(new BigDecimal("0.1234567890"), frozen.getMainFlowShare());
        verify(datasets).updateResearchState(eq(DATASET_ID), eq(DATE), eq(DATE), eq("READY"),
                eq(AS_OF), eq("quant-dataset-v2"), anyString(), anyString(),
                org.mockito.ArgumentMatchers.contains("\"blockingIssues\":0"), eq(3L));
    }

    @Test
    void rejectsFreezeWhenDatasetIsReadyOrNotResearchV2() {
        QuantDataset ready = researchDataset();
        ready.setStatus("READY");
        when(datasets.findById(DATASET_ID)).thenReturn(Optional.of(ready));
        BusinessException immutable = assertThrows(BusinessException.class,
                () -> service.freeze(DATASET_ID, DATE, DATE, AS_OF));
        assertEquals(ErrorCode.CONFLICT, immutable.getErrorCode());

        QuantDataset legacy = researchDataset();
        legacy.setFingerprintVersion("quant-dataset-v1");
        when(datasets.findById(DATASET_ID)).thenReturn(Optional.of(legacy));
        BusinessException unsupported = assertThrows(BusinessException.class,
                () -> service.freeze(DATASET_ID, DATE, DATE, AS_OF));
        assertEquals(ErrorCode.CONFLICT, unsupported.getErrorCode());
        verify(sourceFlows, never()).findDailyPointInTime(any(), any(), any(), any());
    }

    @Test
    void validatesFreezeRequestBeforeReadingSources() {
        assertThrows(BusinessException.class, () -> service.freeze(null, DATE, DATE, AS_OF));
        assertThrows(BusinessException.class, () -> service.freeze(DATASET_ID, DATE.plusDays(1), DATE, AS_OF));
        assertThrows(BusinessException.class, () -> service.freeze(DATASET_ID, DATE, DATE, DATE.minusDays(1).atTime(23, 59)));
        verify(sourceFlows, never()).findDailyPointInTime(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private List<QuantCapitalFlowDaily> capturedRows() {
        ArgumentCaptor<List> rows = ArgumentCaptor.forClass(List.class);
        verify(capitalFlows).saveAll(rows.capture());
        return (List<QuantCapitalFlowDaily>) rows.getValue();
    }

    private void stubCompleteFixture() {
        QuantDataset dataset = researchDataset();
        when(datasets.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(datasets.updateResearchState(anyLong(), any(), any(), anyString(), any(), anyString(),
                anyString(), anyString(), anyString(), anyLong())).thenReturn(true);
        when(instruments.findAll()).thenReturn(Collections.singletonList(instrument(11L, "600519", "SH")));
        when(marketData.findUniverseMembers(DATASET_ID)).thenReturn(
                Collections.singletonList(member("600519.SH", DATE, true)));
        when(marketData.findBars(DATASET_ID)).thenReturn(Collections.singletonList(bar("600519.SH", DATE)));
        when(marketData.findFundamentals(DATASET_ID)).thenReturn(Collections.emptyList());
        when(sourceFlows.findDailyPointInTime(eq(DATE), eq(DATE), eq(AS_OF), any())).thenReturn(
                Collections.singletonList(completePoint(11L, DATE, DATE.atTime(18, 0))));
        when(datasets.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
    }

    private QuantDataset researchDataset() {
        QuantDataset value = new QuantDataset();
        value.setId(DATASET_ID);
        value.setName("capital research");
        value.setMarket("A_SHARE");
        value.setUniverseType("CUSTOM");
        value.setSourceType("MANUAL_IMPORT");
        value.setDataKind("REAL");
        value.setDatasetLevel("RESEARCH");
        value.setFingerprintVersion("quant-dataset-v2");
        value.setPartitionManifest("[]");
        value.setStatus("BUILDING");
        value.setRevision(3L);
        return value;
    }

    private Instrument instrument(Long id, String code, String market) {
        Instrument value = new Instrument();
        value.setId(id);
        value.setCode(code);
        value.setMarket(market);
        value.setType("STOCK");
        return value;
    }

    private CapitalFlowPoint completePoint(Long instrumentId, LocalDate date, LocalDateTime retrievedAt) {
        CapitalFlowPoint value = new CapitalFlowPoint();
        value.setId(101L);
        value.setInstrumentId(instrumentId);
        value.setProviderCode("EASTMONEY");
        value.setGranularity("DAY_1");
        value.setDataDate(date);
        value.setObservedAt(date.atTime(15, 0));
        value.setRetrievedAt(retrievedAt);
        value.setPayloadHash("payload-sha256");
        value.setCalculationVersion("capital-flow-v3");
        value.setQualityStatus("COMPLETE");
        value.setIntervalTradeAmount(new BigDecimal("100000000"));
        value.setMainNetInflow(new BigDecimal("12345678.9"));
        value.setSuperLargeNetInflow(new BigDecimal("5000000.1"));
        value.setLargeNetInflow(new BigDecimal("7345678.8"));
        value.setMediumNetInflow(new BigDecimal("-2000000"));
        value.setSmallNetInflow(new BigDecimal("-10345678.9"));
        value.setTurnoverRate(new BigDecimal("0.0312"));
        return value;
    }

    private QuantUniverseMember member(String code, LocalDate date, boolean active) {
        QuantUniverseMember value = new QuantUniverseMember();
        value.setDatasetId(DATASET_ID);
        value.setInstrumentCode(code);
        value.setTradeDate(date);
        value.setMember(active);
        value.setSourceKind("POINT_IN_TIME");
        return value;
    }

    private QuantDailyBar bar(String code, LocalDate date) {
        QuantDailyBar value = new QuantDailyBar();
        value.setDatasetId(DATASET_ID);
        value.setInstrumentCode(code);
        value.setTradeDate(date);
        return value;
    }

    private QuantCapitalFlowDaily frozen(String code, LocalDate date, String sourceFingerprint) {
        QuantCapitalFlowDaily value = new QuantCapitalFlowDaily();
        value.setDatasetId(DATASET_ID);
        value.setTradeDate(date);
        value.setInstrumentCode(code);
        value.setAvailableAt(date.atTime(18, 0));
        value.setSourceFlowId(101L);
        value.setProviderCode("EASTMONEY");
        value.setMainNetInflow(new BigDecimal("123.4500"));
        value.setMainFlowShare(new BigDecimal("0.0123450000"));
        value.setSuperLargeNetInflow(new BigDecimal("100"));
        value.setLargeNetInflow(new BigDecimal("23.45"));
        value.setMediumNetInflow(new BigDecimal("-20"));
        value.setSmallNetInflow(new BigDecimal("-103.45"));
        value.setTurnoverRate(new BigDecimal("0.03"));
        value.setAmount(new BigDecimal("10000"));
        value.setQualityStatus("COMPLETE");
        value.setSourceFingerprint(sourceFingerprint);
        value.setCalculationVersion("capital-flow-v3");
        return value;
    }

    private QuantDatasetPartition partition(String fingerprint) {
        QuantDatasetPartition value = new QuantDatasetPartition();
        value.setDatasetId(DATASET_ID);
        value.setPartitionType("CAPITAL_FLOW_DAILY");
        value.setRowCount(1);
        value.setMinDate(DATE);
        value.setMaxDate(DATE);
        value.setPartitionFingerprint(fingerprint);
        value.setQualityStatus("COMPLETE");
        value.setCreatedAt(AS_OF);
        return value;
    }
}
