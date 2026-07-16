package com.finscope.service.financials;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.instrument.Instrument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FinancialQueryService {
    private final InstrumentRepository instruments;
    private final FinancialReportRepository reports;

    public FinancialQueryService(InstrumentRepository instruments,
                                 FinancialReportRepository reports) {
        this.instruments = instruments;
        this.reports = reports;
    }

    public List<Instrument> listInstruments() {
        return instruments.findAll().stream()
                .filter(value -> "STOCK".equals(value.getType()))
                .filter(value -> "SH".equals(value.getMarket())
                        || "SZ".equals(value.getMarket())
                        || "BJ".equals(value.getMarket()))
                .collect(Collectors.toList());
    }

    public List<FinancialReport> listReports(Long instrumentId) {
        requireInstrument(instrumentId);
        return reports.findReports(instrumentId);
    }

    public FinancialReportView view(Long reportId) {
        FinancialReport report = reports.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("财报不存在：" + reportId));
        Instrument instrument = requireInstrument(report.getInstrumentId());
        List<FinancialLineItem> lines = reports.findAllLineItems(reportId);
        FinancialReportView view = new FinancialReportView();
        view.setInstrument(instrument);
        view.setReport(report);
        view.setStatements(group(lines));
        view.setMetrics(reports.findMetrics(reportId));
        view.setFindings(reports.findFindings(reportId));
        if (lines.isEmpty()) {
            view.getDataGaps().add("该报告尚无结构化三张表数据");
        }
        return view;
    }

    private Instrument requireInstrument(Long id) {
        return instruments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("标的不存在：" + id));
    }

    private Map<FinancialStatementType, List<FinancialLineItem>> group(
            List<FinancialLineItem> items) {
        Map<FinancialStatementType, List<FinancialLineItem>> result =
                new EnumMap<FinancialStatementType, List<FinancialLineItem>>(
                        FinancialStatementType.class);
        for (FinancialStatementType type : FinancialStatementType.values()) {
            result.put(type, new ArrayList<FinancialLineItem>());
        }
        for (FinancialLineItem item : items) {
            result.get(item.getStatementType()).add(item);
        }
        return result;
    }
}
