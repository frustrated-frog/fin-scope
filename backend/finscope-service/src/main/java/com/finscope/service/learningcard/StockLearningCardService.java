package com.finscope.service.learningcard;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.dao.learningcard.StockLearningCardRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.StockLearningCard;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.service.strategy.StrategyInstrumentResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

@Service
public class StockLearningCardService {
    private static final long RUN_LEASE_MINUTES = 30L;
    @Resource private StrategyInstrumentResolver instrumentResolver;
    @Resource private StockLearningCardRepository cards;
    @Resource private StockLearningCardAgentExecutor agentExecutor;

    public StockLearningCardRun start(String code) {
        Instrument instrument = instrumentResolver.resolve(code, "STOCK");
        StockLearningCard card = cards.findOrCreate(instrument.getId(), StockLearningFramework.CODE);
        StockLearningCardRun active = cards.active(card.getId()).orElse(null);
        if (active != null) {
            if (!isStale(active)) throw new BusinessConflictException("该股票学习卡仍在生成中");
            expire(active);
        }
        StockLearningCardRun running = run(card, "RUNNING", null, "学习卡已进入生成队列", "PENDING", "CONTROLLED");
        running.setStage("QUEUED");
        try {
            running = cards.appendRun(running, Collections.emptyList(), Collections.emptyList());
        } catch (DataAccessException error) {
            StockLearningCardRun concurrent = cards.active(card.getId()).orElse(null);
            if (concurrent != null) {
                throw new BusinessConflictException("该股票学习卡仍在生成中");
            }
            throw error;
        }
        try {
            agentExecutor.schedule(instrument, running);
        } catch (RuntimeException error) {
            running.setStatus("FAILED"); running.setStage("COMPLETED"); running.setFailedStage("QUEUED");
            running.setErrorCode("QUEUE_REJECTED");
            running.setUserMessage("学习卡生成队列暂时繁忙，请稍后重新生成");
            running.setRetryable(true); running.setSummary("学习卡未能进入生成队列");
            running = cards.updateRun(running, Collections.emptyList(), Collections.emptyList());
        }
        return running;
    }

    public StockLearningCardView get(String code) {
        Instrument instrument = instrumentResolver.resolve(code, "STOCK");
        Optional<StockLearningCard> stored = cards.findByInstrumentId(instrument.getId());
        StockLearningCard card = stored.orElseGet(() -> emptyCard(instrument));
        StockLearningCardRun latest = stored.isPresent() ? cards.latest(card.getId()).orElse(null) : null;
        return new StockLearningCardView(card, latest);
    }

    private boolean isStale(StockLearningCardRun run) {
        return run.getCreatedAt() != null && run.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(RUN_LEASE_MINUTES));
    }

    private void expire(StockLearningCardRun run) {
        String failedStage = run.getStage() == null ? "QUEUED" : run.getStage();
        run.setStatus("FAILED"); run.setStage("COMPLETED"); run.setFailedStage(failedStage);
        run.setErrorCode("STALE_RUN_EXPIRED");
        run.setUserMessage("上一次生成因服务中断未完成，已允许重新生成");
        run.setRetryable(true); run.setSummary("上一次学习卡运行已过期");
        cards.updateRun(run, run.getClaims(), run.getWatchItems());
    }

    private StockLearningCard emptyCard(Instrument instrument) {
        StockLearningCard card = new StockLearningCard();
        card.setInstrumentId(instrument.getId()); card.setCode(instrument.getCode()); card.setName(instrument.getName());
        card.setFrameworkCode(StockLearningFramework.CODE); card.setStatus("IDLE");
        return card;
    }
    private StockLearningCardRun run(StockLearningCard card, String status, String conclusion, String summary, String completeness, String mode) {
        StockLearningCardRun value = new StockLearningCardRun(); value.setCardId(card.getId());
        value.setFrameworkCode(StockLearningFramework.CODE); value.setStatus(status); value.setConclusionStatus(conclusion);
        value.setSummary(summary); value.setEvidenceCompleteness(completeness); value.setGenerationMode(mode); return value;
    }
    public static class StockLearningCardView {
        private final StockLearningCard card; private final StockLearningCardRun latestRun;
        public StockLearningCardView(StockLearningCard card, StockLearningCardRun latestRun) { this.card=card; this.latestRun=latestRun; }
        public StockLearningCard getCard() { return card; }
        public StockLearningCardRun getLatestRun() { return latestRun; }
    }
}
