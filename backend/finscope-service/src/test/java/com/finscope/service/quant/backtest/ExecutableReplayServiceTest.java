package com.finscope.service.quant.backtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.enums.quant.OpenExecutionState;
import com.finscope.common.enums.quant.ReplayOrderReason;
import com.finscope.common.enums.quant.ReplayOrderSide;
import com.finscope.common.exception.BusinessException;
import com.finscope.domain.quant.execution.*;
import com.finscope.service.factorresearch.FactorProviderRegistry;
import com.finscope.service.factorresearch.LegacyQuantFactorProvider;
import com.finscope.service.quant.factor.FactorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ExecutableReplayService.class, ExecutableReplayValidator.class,
        QuantBacktestEngine.class, FactorRegistry.class, FactorProviderRegistry.class,
        LegacyQuantFactorProvider.class, ExecutableReplayServiceTest.JsonConfig.class})
class ExecutableReplayServiceTest {
    @Configuration
    static class JsonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    @Autowired
    private ExecutableReplayService service;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void fixedSlotsRetainCashAndReplayIsIdentical() throws Exception {
        ExecutableReplayInput input = input();
        ExecutableReplayReport first = service.replay(input);
        ExecutableReplayReport second = service.replay(input);
        assertEquals(mapper.writeValueAsString(first), mapper.writeValueAsString(second));
        assertEquals(60000, first.getAccount().getEquityCurve().get(1).getCash(), 1e-8);
        assertEquals(0.2, first.getTargetWeights().get(input.getTradingDates().get(0)).get("600001.SH"));
        assertEquals(input.getTradingDates().get(1), first.getAccount().getTrades().get(0).getTradeDate());
        assertEquals(input.getTradingDates().size(), first.getAccount().getEquityCurve().size());
        assertEquals(100000, first.getAccount().getEquityCurve().get(11).getCash(), 1e-8);
        assertEquals(64, first.getInputFingerprint().length());
    }

    @Test
    void unchangedTargetsDoNotSellAndRebuy() throws Exception {
        ExecutableReplayInput input = input();
        input.getSignals().get(1).setCandidates(input.getSignals().get(0).getCandidates());
        ExecutableReplayReport report = service.replay(input);
        assertTrue(report.getOrders().stream().noneMatch(order -> order.getTradeDate().equals(input.getTradingDates().get(6))));
    }

    @Test
    void blockedSalePreservesCashAndIndustryBudget() throws Exception {
        ExecutableReplayInput input = input();
        input.getProtocol().setMaxExposure(0.4);
        input.getProtocol().setMaxSingleWeight(0.08);
        input.getProtocol().setMaxIndustryWeight(0.4);
        // One industry slot fills first; a blocked sale must still occupy its budget.
        for (FrozenCandidate c : input.getSignals().get(0).getCandidates()) {
            c.setEligible(true);
        }
        input.getProtocol().setMaxIndustryWeight(0.08);
        for (FrozenCandidate c : input.getSignals().get(0).getCandidates()) {
            c.setIndustry("same-sector");
        }
        for (FrozenSignalBatch batch : input.getSignals()) {
            for (FrozenCandidate c : batch.getCandidates()) {
                c.setIndustry("same-sector");
            }
        }
        for (ExecutionBar bar : input.getBars()) {
            if (bar.getTradeDate().equals(input.getTradingDates().get(6)) && bar.getInstrumentCode().equals("600001.SH")) {
                bar.setOpenState(OpenExecutionState.SELL_BLOCKED);
            }
        }
        ExecutableReplayReport report = service.replay(input);
        assertTrue(report.getOrders().stream().anyMatch(order -> order.getSide() == ReplayOrderSide.SELL
                && order.getReason() == ReplayOrderReason.OPEN_BLOCKED));
        assertTrue(report.getOrders().stream().anyMatch(order -> order.getSide() == ReplayOrderSide.BUY
                && order.getTradeDate().equals(input.getTradingDates().get(6))
                && order.getReason() == ReplayOrderReason.NO_LOT_BUDGET));
        assertEquals(92000, report.getAccount().getEquityCurve().get(6).getCash(), 1e-8);
    }

    @Test
    void blockedOverweightHoldingStopsAllNewRisk() throws Exception {
        ExecutableReplayInput input = input();
        input.getProtocol().setMaxExposure(0.4);
        input.getProtocol().setMaxSingleWeight(0.08);
        for (ExecutionBar bar : input.getBars()) {
            if (bar.getTradeDate().equals(input.getTradingDates().get(6)) && bar.getInstrumentCode().equals("600001.SH")) {
                bar.setOpen(BigDecimal.valueOf(300));
                bar.setClose(BigDecimal.valueOf(300));
                bar.setOpenState(OpenExecutionState.SELL_BLOCKED);
            }
        }
        ExecutableReplayReport report = service.replay(input);
        assertEquals(84000, report.getAccount().getEquityCurve().get(6).getCash(), 1e-8);
        assertTrue(report.getOrders().stream().filter(order -> order.getSide() == ReplayOrderSide.BUY
                && order.getTradeDate().equals(input.getTradingDates().get(6)))
                .allMatch(order -> order.getFilledQuantity() == 0));
    }

    @Test
    void highFeesReduceOrderSizeWithoutMakingCashNegative() throws Exception {
        ExecutableReplayInput input = input();
        input.getProtocol().setInitialCapital(6000d);
        input.getProtocol().setMinimumCommission(1000d);
        input.getProtocol().setMaxIndustryWeight(1d);
        for (FrozenCandidate candidate : input.getSignals().get(0).getCandidates()) {
            candidate.setEligible(true);
        }
        ExecutableReplayReport report = service.replay(input);
        assertTrue(report.getAccount().getEquityCurve().stream().allMatch(point -> point.getCash() >= 0));
        assertEquals(0, report.getAccount().getEquityCurve().get(1).getCash(), 1e-8);
    }

    @Test
    void collapseCannotCreateNegativeCashFromMinimumSellCommission() throws Exception {
        ExecutableReplayInput input = input();
        input.getProtocol().setMinimumCommission(1000d);
        // Use larger capital to buy lots, then consume cash with fees before a price collapse.
        input.getProtocol().setInitialCapital(6000d);
        for (FrozenCandidate candidate : input.getSignals().get(0).getCandidates()) {
            candidate.setEligible(true);
        }
        for (ExecutionBar bar : input.getBars()) {
            if (!bar.getTradeDate().isBefore(input.getTradingDates().get(6))) {
                bar.setOpen(BigDecimal.valueOf(0.01));
                bar.setClose(BigDecimal.valueOf(0.01));
            }
        }
        ExecutableReplayReport report = service.replay(input);
        assertTrue(report.getOrders().stream().anyMatch(order -> order.getReason() == ReplayOrderReason.COST_EXCEEDS_CASH));
        assertTrue(report.getAccount().getEquityCurve().stream().allMatch(point -> point.getCash() >= 0));
    }

    @Test
    void feesAreChargedPerOrderAndUnrealizedLossIsInEquity() throws Exception {
        ExecutableReplayInput input = input();
        input.getProtocol().setMinimumCommission(5d);
        input.getProtocol().setStampDuty(0.001);
        for (ExecutionBar bar : input.getBars()) {
            if (bar.getTradeDate().equals(input.getTradingDates().get(2))) {
                bar.setClose(BigDecimal.valueOf(9));
            }
        }
        ExecutableReplayReport report = service.replay(input);
        assertEquals(59990, report.getAccount().getEquityCurve().get(1).getCash(), 1e-8);
        assertEquals(95990, report.getAccount().getEquityCurve().get(2).getTotalAsset(), 1e-8);
        assertTrue(report.getAccount().getTrades().stream().filter(t -> "SELL".equals(t.getSide()))
                .allMatch(t -> Math.abs(t.getFee() - (5 + t.getNotional() * 0.001)) < 1e-8));
    }

    @Test
    void insufficientLotAndBlockedOpenAreAudited() throws Exception {
        ExecutableReplayInput input = input();
        input.getProtocol().setInitialCapital(100d);
        ExecutableReplayReport report = service.replay(input);
        assertTrue(report.getAccount().getTrades().isEmpty());
        assertTrue(report.getOrders().stream().allMatch(o -> o.getReason() == ReplayOrderReason.NO_LOT_BUDGET));
        input = input();
        for (ExecutionBar bar : input.getBars()) {
            bar.setOpenState(OpenExecutionState.SUSPENDED);
        }
        report = service.replay(input);
        assertTrue(report.getAccount().getTrades().isEmpty());
        assertTrue(report.getOrders().stream().allMatch(o -> o.getReason() == ReplayOrderReason.OPEN_BLOCKED));
    }

    @Test
    void emptyUniverseProducesCashCurveButMissingBatchFails() throws Exception {
        ExecutableReplayInput input = input();
        for (FrozenSignalBatch batch : input.getSignals()) {
            batch.setCandidates(Collections.emptyList());
        }
        input.setBars(Collections.emptyList());
        assertEquals(12, service.replay(input).getAccount().getEquityCurve().size());
        input.getSignals().remove(1);
        assertThrows(BusinessException.class, () -> service.replay(input));
    }

    @Test
    void rejectsUnknownDataFutureTrainingAndTargetMismatch() throws Exception {
        ExecutableReplayInput input = input();
        input.setPriceBasis("QFQ");
        assertThrows(BusinessException.class, () -> service.replay(input));
        input.setPriceBasis("RAW");
        input.setHasCorporateActions(null);
        assertThrows(BusinessException.class, () -> service.replay(input));
        input.setHasCorporateActions(true);
        assertThrows(BusinessException.class, () -> service.replay(input));
        input.setHasCorporateActions(false);
        input.getSignals().get(0).setProtocolVersion("NEXT_CLOSE_RETURN");
        assertThrows(BusinessException.class, () -> service.replay(input));
        input.getSignals().get(0).setProtocolVersion("OPEN_5D_V1");
        input.getSignals().get(0).setTrainingLabelsMaturedBefore(input.getSignals().get(0).getInformationCutoff());
        assertThrows(BusinessException.class, () -> service.replay(input));
    }

    @Test
    void rejectsMissingStateDuplicateRowsAndMissingTradingDay() throws Exception {
        ExecutableReplayInput input = input();
        input.getBars().get(0).setOpenState(null);
        assertThrows(BusinessException.class, () -> service.replay(input));
        input.getBars().get(0).setOpenState(OpenExecutionState.TRADABLE);
        input.getBars().add(input.getBars().get(0));
        assertThrows(BusinessException.class, () -> service.replay(input));
        input.getBars().remove(input.getBars().size() - 1);
        input.getBars().remove(0);
        assertThrows(BusinessException.class, () -> service.replay(input));
    }

    private ExecutableReplayInput input() throws Exception {
        ExecutableReplayInput input = mapper.readValue(Path.of("../../docs/quant/examples/executable-replay-synthetic.json").toFile(),
                ExecutableReplayInput.class);
        input.getProtocol().setBuyCommission(0d);
        input.getProtocol().setSellCommission(0d);
        input.getProtocol().setMinimumCommission(0d);
        input.getProtocol().setStampDuty(0d);
        input.getProtocol().setSlippageBps(0d);
        return input;
    }
}
