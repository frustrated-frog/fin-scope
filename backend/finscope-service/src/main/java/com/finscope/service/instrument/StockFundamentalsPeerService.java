package com.finscope.service.instrument;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.StockFundamentals;
import com.finscope.domain.valuation.StockValuationPeer;
import com.finscope.domain.valuation.StockValuationSnapshot;
import com.finscope.domain.valuation.StockValuationView;
import com.finscope.service.valuation.StockValuationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 仅比较本地同口径估值，不为同行发起外部刷新。 */
@Service
@Slf4j
public class StockFundamentalsPeerService {
    private static final int PEER_LIMIT = 8;
    @Resource
    private InstrumentRepository instruments;
    @Resource
    private StockValuationService valuations;

    public void populate(StockFundamentals result) {
        List<StockValuationPeer> samples = new ArrayList<StockValuationPeer>();
        result.setPeers(samples);
        Instrument instrument = result.getInstrument();
        if (instrument.getSectorCode() == null || instrument.getSectorCode().isBlank()) {
            result.setPeerComparisonNote("缺少行业归属，暂无可比的本地同行样本");
            return;
        }
        StockValuationView valuation = result.getValuation();
        StockValuationSnapshot current = valuation == null ? null : valuation.getLatest();
        if (current == null || current.getObservedDate() == null || current.getSourceCode() == null
                || current.getSourceCode().isBlank()) {
            result.setPeerComparisonNote("当前估值缺少观测日或来源，暂无可比的本地同行样本");
            return;
        }
        List<Instrument> candidates;
        try {
            candidates = instruments.findSectorPeers(
                    instrument.getSectorCode(), instrument.getMarket(), instrument.getId(), PEER_LIMIT);
        } catch (RuntimeException error) {
            log.warn("本地同行标的读取失败 instrumentId={} errorType={}",
                    instrument.getId(), error.getClass().getSimpleName());
            result.setPeerComparisonNote("本地同行样本暂不可用，不代表全行业估值");
            result.getWarnings().add("本地同行样本读取失败，已保留当前股票数据");
            return;
        }
        for (Instrument candidate : candidates) {
            StockValuationPeer peer = localPeer(candidate, current, result);
            if (peer != null) {
                samples.add(peer);
            }
        }
        if (samples.isEmpty()) {
            result.setPeerComparisonNote("暂无同日同来源的本地同行正 PE/PB 样本，不代表全行业估值");
        } else {
            result.setPeerComparisonNote("本地同行样本（最多 8 只），仅比较同日同来源且为正的 PE/PB，不代表全行业估值");
        }
    }

    private StockValuationPeer localPeer(Instrument candidate, StockValuationSnapshot current,
                                         StockFundamentals result) {
        StockValuationView view;
        try {
            view = valuations.view(candidate.getId());
        } catch (RuntimeException error) {
            log.warn("本地同行估值读取失败 instrumentId={} errorType={}",
                    candidate.getId(), error.getClass().getSimpleName());
            result.getWarnings().add(candidate.getCode() + " 本地同行估值暂不可用");
            return null;
        }
        StockValuationSnapshot latest = view == null ? null : view.getLatest();
        if (latest == null || !Objects.equals(current.getObservedDate(), latest.getObservedDate())
                || !Objects.equals(current.getSourceCode(), latest.getSourceCode())) {
            return null;
        }
        BigDecimal pe = comparablePositive(current.getPeTtm(), latest.getPeTtm());
        BigDecimal pb = comparablePositive(current.getPbMrq(), latest.getPbMrq());
        if (pe == null && pb == null) {
            return null;
        }
        StockValuationPeer peer = new StockValuationPeer();
        peer.setCode(candidate.getCode());
        peer.setName(candidate.getName());
        peer.setObservedDate(latest.getObservedDate());
        peer.setSourceCode(latest.getSourceCode());
        peer.setQualityStatus(latest.getQualityStatus());
        peer.setPeTtm(pe);
        peer.setPbMrq(pb);
        return peer;
    }

    private BigDecimal comparablePositive(BigDecimal current, BigDecimal peer) {
        if (current == null || current.signum() <= 0 || peer == null || peer.signum() <= 0) {
            return null;
        }
        return peer;
    }
}
