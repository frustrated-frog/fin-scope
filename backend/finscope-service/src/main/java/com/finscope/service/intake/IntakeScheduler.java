package com.finscope.service.intake;

import com.finscope.dao.intake.FetchBatchRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.intake.IntakeEnums;
import com.finscope.domain.source.Source;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class IntakeScheduler {
    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final SourceRepository sourceRepository;
    private final FetchBatchRepository fetchBatchRepository;
    private final IntakeService intakeService;

    public IntakeScheduler(SourceRepository sourceRepository,
                           FetchBatchRepository fetchBatchRepository,
                           IntakeService intakeService) {
        this.sourceRepository = sourceRepository;
        this.fetchBatchRepository = fetchBatchRepository;
        this.intakeService = intakeService;
    }

    @Scheduled(fixedDelay = 60000L)
    public void runDueSources() {
        String currentSlot = LocalTime.now().format(SLOT_FORMAT);
        LocalDate today = LocalDate.now();
        List<Source> sources = sourceRepository.findAll();
        for (Source source : sources) {
            if (!source.isScheduledEnabled() || !source.isEnabled()) {
                continue;
            }
            if (!containsSlot(source.getScheduleTimes(), currentSlot)) {
                continue;
            }
            if (fetchBatchRepository.hasScheduledRunForSlot(source.getId(), today, currentSlot)) {
                continue;
            }
            try {
                intakeService.intakeFetch(source.getId(), IntakeEnums.TRIGGER_SCHEDULED, currentSlot);
            } catch (Exception ex) {
                log.warn("定时摄入失败 sourceId={} slot={} message={}", source.getId(), currentSlot, ex.getMessage());
            }
        }
    }

    private boolean containsSlot(String scheduleTimes, String currentSlot) {
        if (scheduleTimes == null || scheduleTimes.trim().isEmpty()) {
            return false;
        }
        for (String raw : scheduleTimes.split(",")) {
            if (currentSlot.equals(raw.trim())) {
                return true;
            }
        }
        return false;
    }
}
