package com.finscope.service.valuation;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.valuation.ValuationRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.valuation.StockCorporateAction;
import com.finscope.domain.valuation.StockValuationSnapshot;
import com.finscope.domain.valuation.StockValuationView;
import com.finscope.rpc.valuation.ExternalCorporateAction;
import com.finscope.rpc.valuation.ExternalValuationSnapshot;
import com.finscope.rpc.valuation.PythonValuationDataClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class StockValuationService {
    private static final int CORPORATE_ACTION_LIMIT = 50;
    @Autowired
    private InstrumentRepository instruments;
    @Autowired
    private ValuationRepository repository;
    @Autowired
    private PythonValuationDataClient client;
    @Autowired
    private ValuationPercentileCalculator percentileCalculator;

    public StockValuationView view(Long instrumentId) {
        Instrument instrument = requireInstrument(instrumentId);
        return assemble(instrument);
    }

    public StockValuationView refresh(Long instrumentId) {
        Instrument instrument = requireInstrument(instrumentId);
        ExternalValuationSnapshot external = client.fetchValuation(instrument);
        StockValuationSnapshot snapshot = toSnapshot(instrumentId, external);
        List<ExternalCorporateAction> externalActions = client.fetchCorporateActions(
                instrument, LocalDate.now().minusYears(5), LocalDate.now());
        repository.saveSnapshot(snapshot);
        repository.saveCorporateActions(toActions(instrumentId, externalActions));
        StockValuationView result = assemble(instrument);
        result.getWarnings().addAll(external.getWarnings());
        return result;
    }

    private StockValuationView assemble(Instrument instrument) {
        List<StockValuationSnapshot> history = repository.findHistory(
                instrument.getId(), LocalDate.now().minusYears(5));
        StockValuationView result = new StockValuationView();
        result.setInstrument(instrument);
        result.setHistory(history);
        result.setCorporateActions(repository.findCorporateActions(
                instrument.getId(), CORPORATE_ACTION_LIMIT));
        if (history.isEmpty()) {
            result.getWarnings().add("尚未积累估值快照，请先刷新数据");
            return result;
        }
        StockValuationSnapshot latest = history.get(0);
        result.setLatest(latest);
        result.getMetrics().add(percentileCalculator.summarize(
                "PE_TTM", latest.getPeTtm(), latest.getObservedDate(), history,
                StockValuationSnapshot::getPeTtm));
        result.getMetrics().add(percentileCalculator.summarize(
                "PE_MRQ", latest.getPeMrq(), latest.getObservedDate(), history,
                StockValuationSnapshot::getPeMrq));
        result.getMetrics().add(percentileCalculator.summarize(
                "PB_MRQ", latest.getPbMrq(), latest.getObservedDate(), history,
                StockValuationSnapshot::getPbMrq));
        result.getMetrics().add(percentileCalculator.summarize(
                "PS_TTM", latest.getPsTtm(), latest.getObservedDate(), history,
                StockValuationSnapshot::getPsTtm));
        result.getMetrics().add(percentileCalculator.summarize(
                "PCF_TTM", latest.getPcfTtm(), latest.getObservedDate(), history,
                StockValuationSnapshot::getPcfTtm));
        if (result.getMetrics().stream().anyMatch(value -> "ACCUMULATING".equals(value.getHistoryStatus()))) {
            result.getWarnings().add("历史分位需要至少 20 个有效日快照，当前仍在积累");
        }
        return result;
    }

    private StockValuationSnapshot toSnapshot(Long instrumentId, ExternalValuationSnapshot source) {
        StockValuationSnapshot result = new StockValuationSnapshot();
        result.setInstrumentId(instrumentId);
        result.setObservedAt(source.getObservedAt());
        result.setObservedDate(source.getObservedAt().atZone(ZoneId.systemDefault()).toLocalDate());
        result.setName(source.getName());
        result.setPeTtm(source.getPeTtm());
        result.setPeMrq(source.getPeMrq());
        result.setPbMrq(source.getPbMrq());
        result.setPsTtm(source.getPsTtm());
        result.setPcfTtm(source.getPcfTtm());
        result.setSourceCode(source.getSourceCode());
        result.setQualityStatus(source.getQualityStatus());
        return result;
    }

    private List<StockCorporateAction> toActions(
            Long instrumentId, List<ExternalCorporateAction> sources) {
        List<StockCorporateAction> result = new ArrayList<StockCorporateAction>();
        for (ExternalCorporateAction source : sources) {
            StockCorporateAction value = new StockCorporateAction();
            value.setInstrumentId(instrumentId);
            value.setExDate(source.getExDate());
            value.setEventTypes(source.getEventTypes());
            value.setDividendPerShare(source.getDividendPerShare());
            value.setPerShareBonus(source.getPerShareBonus());
            value.setAllotmentRatio(source.getAllotmentRatio());
            value.setAllotmentPrice(source.getAllotmentPrice());
            value.setCurrency(source.getCurrency());
            value.setSourceCode(source.getSourceCode());
            value.setRetrievedAt(LocalDateTime.now());
            result.add(value);
        }
        return result;
    }

    private Instrument requireInstrument(Long id) {
        return instruments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("标的不存在：" + id));
    }
}
