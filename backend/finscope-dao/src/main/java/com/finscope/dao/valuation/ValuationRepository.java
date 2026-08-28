package com.finscope.dao.valuation;

import com.finscope.domain.valuation.StockCorporateAction;
import com.finscope.domain.valuation.StockValuationSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Repository
public class ValuationRepository {
    @Autowired
    private JdbcTemplate jdbc;

    public ValuationRepository() {
    }

    ValuationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<StockValuationSnapshot> snapshotMapper = (rs, rowNum) -> {
        StockValuationSnapshot value = new StockValuationSnapshot();
        value.setId(rs.getLong("id"));
        value.setInstrumentId(rs.getLong("instrument_id"));
        value.setObservedDate(LocalDate.parse(rs.getString("observed_date")));
        value.setObservedAt(Instant.parse(rs.getString("observed_at")));
        value.setName(rs.getString("name"));
        value.setPeTtm(decimal(rs.getString("pe_ttm")));
        value.setPeMrq(decimal(rs.getString("pe_mrq")));
        value.setPbMrq(decimal(rs.getString("pb_mrq")));
        value.setPsTtm(decimal(rs.getString("ps_ttm")));
        value.setPcfTtm(decimal(rs.getString("pcf_ttm")));
        value.setSourceCode(rs.getString("source_code"));
        value.setQualityStatus(rs.getString("quality_status"));
        value.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
        value.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at")));
        return value;
    };

    private final RowMapper<StockCorporateAction> actionMapper = (rs, rowNum) -> {
        StockCorporateAction value = new StockCorporateAction();
        value.setId(rs.getLong("id"));
        value.setInstrumentId(rs.getLong("instrument_id"));
        value.setExDate(LocalDate.parse(rs.getString("ex_date")));
        String eventTypes = rs.getString("event_types");
        value.setEventTypes(eventTypes == null || eventTypes.isEmpty()
                ? Collections.<String>emptyList() : Arrays.asList(eventTypes.split(",")));
        value.setDividendPerShare(decimal(rs.getString("dividend_per_share")));
        value.setPerShareBonus(decimal(rs.getString("per_share_bonus")));
        value.setAllotmentRatio(decimal(rs.getString("allotment_ratio")));
        value.setAllotmentPrice(decimal(rs.getString("allotment_price")));
        value.setCurrency(rs.getString("currency"));
        value.setSourceCode(rs.getString("source_code"));
        value.setRetrievedAt(LocalDateTime.parse(rs.getString("retrieved_at")));
        return value;
    };

    @Transactional
    public void saveSnapshot(StockValuationSnapshot value) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO stock_valuation_snapshot(instrument_id,observed_date,observed_at,"
                        + "name,pe_ttm,pe_mrq,pb_mrq,ps_ttm,pcf_ttm,source_code,quality_status,"
                        + "created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(instrument_id,observed_date,source_code) DO UPDATE SET "
                        + "observed_at=excluded.observed_at,name=excluded.name,pe_ttm=excluded.pe_ttm,"
                        + "pe_mrq=excluded.pe_mrq,pb_mrq=excluded.pb_mrq,ps_ttm=excluded.ps_ttm,"
                        + "pcf_ttm=excluded.pcf_ttm,quality_status=excluded.quality_status,"
                        + "updated_at=excluded.updated_at",
                value.getInstrumentId(), value.getObservedDate().toString(), value.getObservedAt().toString(),
                value.getName(), text(value.getPeTtm()), text(value.getPeMrq()), text(value.getPbMrq()),
                text(value.getPsTtm()), text(value.getPcfTtm()), value.getSourceCode(),
                value.getQualityStatus(), now.toString(), now.toString());
    }

    @Transactional
    public void saveCorporateActions(List<StockCorporateAction> values) {
        for (StockCorporateAction value : values) {
            LocalDateTime retrievedAt = value.getRetrievedAt() == null
                    ? LocalDateTime.now() : value.getRetrievedAt();
            jdbc.update("INSERT INTO stock_corporate_action(instrument_id,ex_date,event_types,"
                            + "dividend_per_share,per_share_bonus,allotment_ratio,allotment_price,currency,"
                            + "source_code,retrieved_at) VALUES(?,?,?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(instrument_id,ex_date,source_code) DO UPDATE SET "
                            + "event_types=excluded.event_types,dividend_per_share=excluded.dividend_per_share,"
                            + "per_share_bonus=excluded.per_share_bonus,allotment_ratio=excluded.allotment_ratio,"
                            + "allotment_price=excluded.allotment_price,currency=excluded.currency,"
                            + "retrieved_at=excluded.retrieved_at",
                    value.getInstrumentId(), value.getExDate().toString(),
                    String.join(",", value.getEventTypes()), text(value.getDividendPerShare()),
                    text(value.getPerShareBonus()), text(value.getAllotmentRatio()),
                    text(value.getAllotmentPrice()), value.getCurrency(), value.getSourceCode(),
                    retrievedAt.toString());
        }
    }

    public List<StockValuationSnapshot> findHistory(Long instrumentId, LocalDate fromDate) {
        return jdbc.query("SELECT * FROM stock_valuation_snapshot WHERE instrument_id=? "
                        + "AND observed_date>=? ORDER BY observed_date DESC,id DESC",
                snapshotMapper, instrumentId, fromDate.toString());
    }

    public List<StockCorporateAction> findCorporateActions(Long instrumentId, int limit) {
        return jdbc.query("SELECT * FROM stock_corporate_action WHERE instrument_id=? "
                        + "ORDER BY ex_date DESC,id DESC LIMIT ?", actionMapper, instrumentId, limit);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
