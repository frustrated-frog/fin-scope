package com.finscope.service.learningcard;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.dao.learningcard.StockLearningCardRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.StockLearningCard;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.service.strategy.StrategyInstrumentResolver;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

@Service
public class StockLearningCardService {
    @Resource private StrategyInstrumentResolver instrumentResolver;
    @Resource private StockLearningCardRepository cards;
    @Resource private StockLearningCardAgentExecutor agentExecutor;

    public StockLearningCardRun start(String code) {
        Instrument instrument = instrumentResolver.resolve(code, "STOCK");
        StockLearningCard card = cards.findOrCreate(instrument.getId(), StockLearningFramework.CODE);
        StockLearningCardRun latest = cards.latest(card.getId()).orElse(null);
        if (latest != null && "RUNNING".equals(latest.getStatus())) throw new BusinessConflictException("该股票学习卡仍在生成中");
        StockLearningCardRun running = run(card, "RUNNING", null, "学习卡已进入生成队列", "PENDING", "CONTROLLED");
        running.setStage("QUEUED");
        running = cards.appendRun(running, Collections.emptyList(), Collections.emptyList());
        agentExecutor.schedule(instrument, running);
        return running;
    }

    public StockLearningCardView get(String code) {
        Instrument instrument = instrumentResolver.resolve(code, "STOCK");
        StockLearningCard card = cards.findOrCreate(instrument.getId(), StockLearningFramework.CODE);
        StockLearningCardRun latest = cards.latest(card.getId()).orElse(null);
        return new StockLearningCardView(card, latest);
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
