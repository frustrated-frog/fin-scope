package com.finscope.service.marketintel;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.DragonTigerRecord;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class DragonTigerView {
    private Instrument instrument;
    private Range range;
    private List<DragonTigerRecord> records = Collections.emptyList();
    private Health health;

    @Data
    public static class Range {
        private int days;
        private LocalDate from;
        private LocalDate to;
    }

    @Data
    public static class Health {
        private String status;
        private String providerCode;
        private LocalDateTime asOf;
        private List<String> warnings = Collections.emptyList();
    }
}
