package com.finscope.service.marketintel;

import com.finscope.dao.marketintel.DragonTigerRepository;
import com.finscope.dao.marketintel.MarketIntelRefreshRunRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.DragonTigerRecord;
import com.finscope.domain.marketintel.MarketIntelRefreshStep;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class MarketIntelDragonTigerService {
    private static final Set<Integer> ALLOWED_DAYS =
            new LinkedHashSet<Integer>(Arrays.asList(30, 60, 120));

    private final MarketIntelCapitalService capital;
    private final DragonTigerRepository records;
    private final MarketIntelRefreshRunRepository runs;

    public MarketIntelDragonTigerService(
            MarketIntelCapitalService capital,
            DragonTigerRepository records,
            MarketIntelRefreshRunRepository runs) {
        this.capital = capital;
        this.records = records;
        this.runs = runs;
    }

    public DragonTigerView view(Long instrumentId, int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new IllegalArgumentException("龙虎榜查询天数仅支持 30、60、120");
        }
        Instrument instrument = capital.stock(instrumentId);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        List<DragonTigerRecord> values =
                records.findLatestBusinessVersions(instrumentId, from, to);

        DragonTigerView view = new DragonTigerView();
        view.setInstrument(instrument);
        DragonTigerView.Range range = new DragonTigerView.Range();
        range.setDays(days);
        range.setFrom(from);
        range.setTo(to);
        view.setRange(range);
        view.setRecords(values);
        view.setHealth(health(instrumentId, values));
        return view;
    }

    private DragonTigerView.Health health(
            Long instrumentId, List<DragonTigerRecord> records) {
        DragonTigerView.Health health = new DragonTigerView.Health();
        Optional<MarketIntelRefreshStep> latest =
                runs.findLatestStep(instrumentId, "DRAGON_TIGER");
        List<String> warnings = new ArrayList<String>();
        String providerCode = records.isEmpty() ? "" : records.get(0).getProviderCode();
        LocalDateTime asOf = records.stream()
                .map(DragonTigerRecord::getRetrievedAt)
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo).orElse(null);

        if (!latest.isPresent()) {
            health.setStatus(records.isEmpty() ? "NOT_REFRESHED" : "FRESH_PRIMARY");
            health.setProviderCode(providerCode);
            health.setAsOf(asOf);
            health.setWarnings(records.isEmpty()
                    ? Collections.singletonList("尚未刷新龙虎榜数据")
                    : Collections.<String>emptyList());
            return health;
        }

        MarketIntelRefreshStep step = latest.get();
        providerCode = step.getProviderCode() == null ? providerCode : step.getProviderCode();
        if (step.getFinishedAt() != null && (asOf == null || step.getFinishedAt().isAfter(asOf))) {
            asOf = step.getFinishedAt();
        }
        if (step.getErrorMessage() != null && !step.getErrorMessage().trim().isEmpty()) {
            warnings.add(step.getErrorMessage());
        }
        boolean partialRecord = records.stream()
                .anyMatch(value -> !"COMPLETE".equals(value.getQualityStatus()));
        if (partialRecord && warnings.isEmpty()) {
            warnings.add("部分龙虎榜席位数据缺失");
        }
        switch (step.getStatus()) {
            case FAILED:
                health.setStatus(records.isEmpty() ? "UNAVAILABLE" : "STALE_FALLBACK");
                break;
            case SKIPPED:
                health.setStatus("STALE_FALLBACK");
                break;
            default:
                health.setStatus(warnings.isEmpty() ? "FRESH_PRIMARY" : "PARTIAL_FRESH");
        }
        health.setProviderCode(providerCode);
        health.setAsOf(asOf);
        health.setWarnings(warnings);
        return health;
    }
}
