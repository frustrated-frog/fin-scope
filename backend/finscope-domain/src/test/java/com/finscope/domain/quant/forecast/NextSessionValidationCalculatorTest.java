package com.finscope.domain.quant.forecast;

import com.finscope.common.enums.quant.NextSessionOutcomeStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NextSessionValidationCalculatorTest {
    @Test
    void evaluatesMoreThanOneHundredRowsWithEqualDateWeight() {
        List<NextSessionPredictionRecord> rows = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            rows.add(record(index + 1, "stock" + index, index < 100 ? "2026-09-21" : "2026-09-22", index < 100 ? .02 : -.02));
        }
        var summary = NextSessionValidationCalculator.summarize(rows);
        var group = summary.getGroups().get(0);
        assertEquals(101, summary.getRecordCount());
        assertEquals(2L, group.get("days"));
        assertEquals(.5, (Double) group.get("accuracy"), 1e-9);
        assertEquals(.34, (Double) group.get("brier"), 1e-9);
    }

    @Test
    void keepsEarliestUnavailableRecordBeforeFilteringAndSeparatesVersions() {
        var first = record(1, "605058.SH", "2026-09-21", .02);
        first.setStatus(NextSessionOutcomeStatus.UNAVAILABLE);
        var later = record(2, "605058.SH", "2026-09-21", .02);
        var newVersion = record(3, "605058.SH", "2026-09-21", .02);
        newVersion.getPrediction().setModelVersion("v2");
        var summary = NextSessionValidationCalculator.summarize(List.of(later, first, newVersion));
        assertEquals(2, summary.getGroups().size());
        var group = summary.getGroups().get(0);
        assertEquals(1, group.get("duplicates"));
        assertEquals(1L, group.get("unavailable"));
        assertEquals(0, group.get("count"));
        assertNull(group.get("brier"));
        assertTrue(NextSessionValidationCalculator.summarize(List.of()).getGroups().isEmpty());
    }

    private NextSessionPredictionRecord record(long id, String code, String day, double actual) {
        var prediction = new NextSessionPrediction();
        prediction.setModelVersion("v1");
        prediction.setTargetDate(LocalDate.parse(day));
        prediction.setGeneratedAt(LocalDateTime.of(2026, 9, 20, 16, 0).plusMinutes(id));
        prediction.setUpProbability(.8);
        var record = new NextSessionPredictionRecord();
        record.setId(id);
        record.setInstrumentCode(code);
        record.setPrediction(prediction);
        record.setStatus(NextSessionOutcomeStatus.MATURED);
        record.setActualReturn(actual);
        return record;
    }
}
