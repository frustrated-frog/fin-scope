package com.finscope.service.marketpulse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.marketpulse.SectorRotationStage;
import com.finscope.domain.marketpulse.MarketPulseCandidate;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.domain.quant.discovery.StockDiscoveryCandidate;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MarketPulseCandidateService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() { };
    private static final int LIMIT = 5;
    @Resource
    private ObjectMapper objectMapper;

    public List<MarketPulseCandidate> assemble(StockDiscoveryRun run, List<StockDiscoveryCandidate> candidates,
                                               List<SectorRotationItem> sectors) {
        Map<String, SectorRotationItem> sectorsByName = new HashMap<>();
        for (SectorRotationItem sector : sectors) {
            sectorsByName.put(sector.getSectorName(), sector);
        }
        List<StockDiscoveryCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparing(StockDiscoveryCandidate::getFinalRank,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StockDiscoveryCandidate::getDeepScore,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(StockDiscoveryCandidate::getInstrumentCode));
        List<MarketPulseCandidate> values = new ArrayList<>();
        for (StockDiscoveryCandidate candidate : ordered) {
            if (!eligible(candidate)) {
                continue;
            }
            SectorRotationItem sector = eligibleSector(candidate, sectorsByName);
            if (sector == null) {
                continue;
            }
            values.add(project(candidate, sector));
            if (values.size() >= LIMIT) {
                break;
            }
        }
        return values;
    }

    private boolean eligible(StockDiscoveryCandidate value) {
        return value.getFinalRank() != null && value.getFinalRank() > 0
                && "HEALTHY".equals(value.getHealthStatus())
                && ("ROBUST".equals(value.getConclusion())
                || "CONDITIONALLY_EFFECTIVE".equals(value.getConclusion()));
    }

    private SectorRotationItem eligibleSector(StockDiscoveryCandidate candidate,
                                               Map<String, SectorRotationItem> sectorsByName) {
        for (String name : sectorNames(candidate.getSectorNamesJson())) {
            SectorRotationItem sector = sectorsByName.get(name);
            if (sector != null && allowed(sector.getStage())) {
                return sector;
            }
        }
        return null;
    }

    private boolean allowed(SectorRotationStage stage) {
        return stage != null && stage != SectorRotationStage.WEAK && stage != SectorRotationStage.FADING
                && stage != SectorRotationStage.INSUFFICIENT_DATA;
    }

    private MarketPulseCandidate project(StockDiscoveryCandidate candidate, SectorRotationItem sector) {
        MarketPulseCandidate value = new MarketPulseCandidate();
        value.setInstrumentCode(candidate.getInstrumentCode());
        value.setName(candidate.getName());
        value.setResearchRank(candidate.getFinalRank());
        value.setCalibratedProbability(candidate.getCalibratedProbability());
        value.setHealthStatus(candidate.getHealthStatus());
        value.setSectorName(sector.getSectorName());
        value.setSectorStage(sector.getStage().name());
        value.setWhyNow(sector.getSectorName() + "处于" + stageLabel(sector.getStage())
                + "阶段，且股票发现的样本外验证与模型健康门禁已通过");
        value.getReasons().add("行业轮动得分 " + sector.getRotationScore());
        value.getReasons().add("股票发现最终排名 " + candidate.getFinalRank());
        value.getReasons().add("预测结论 " + candidate.getConclusion());
        if (sector.getStage() == SectorRotationStage.OVERHEATED) {
            value.getRisks().add("行业处于拥挤区间，可能出现高潮后分化");
        } else {
            value.getRisks().add("行业轮动可能快于公司基本面兑现");
        }
        value.getInvalidationConditions().add("行业阶段降为FADING或WEAK");
        value.getInvalidationConditions().add("预测模型健康状态不再为HEALTHY");
        value.getInvalidationConditions().add("事件或业务暴露证据被后续事实否定");
        return value;
    }

    private List<String> sectorNames(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (Exception error) {
            log.warn("市场机会候选行业快照解析失败", error);
            return List.of();
        }
    }

    private String stageLabel(SectorRotationStage stage) {
        if (stage == SectorRotationStage.ACCELERATING) {
            return "加速增强";
        }
        if (stage == SectorRotationStage.PERSISTENT) {
            return "持续强势";
        }
        if (stage == SectorRotationStage.OVERHEATED) {
            return "过热分化";
        }
        if (stage == SectorRotationStage.REVERSING) {
            return "反转修复";
        }
        return "初步转强";
    }
}
