package com.finscope.domain.industrychain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Research Radar 聚合事件与产业链节点之间的影响分析。 */
public class IndustryChainEventImpact {
    public enum Direction { POSITIVE, NEGATIVE, MIXED, UNCERTAIN }
    public enum Mechanism { SUPPLY, DEMAND, PRICE, CAPACITY, POLICY, ORDER, TECHNOLOGY }
    public enum Horizon { SHORT, MEDIUM, LONG }
    public enum Confidence { HIGH, MEDIUM, LOW }

    private Long id;
    private Long chainId;
    private Long radarEventId;
    private String directNodeKey;
    private Direction direction = Direction.UNCERTAIN;
    private Mechanism mechanism = Mechanism.DEMAND;
    private Horizon horizon = Horizon.SHORT;
    private Confidence confidence = Confidence.LOW;
    private String impactSummary;
    private String analysisVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> pathNodeKeys = new ArrayList<String>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChainId() { return chainId; }
    public void setChainId(Long chainId) { this.chainId = chainId; }
    public Long getRadarEventId() { return radarEventId; }
    public void setRadarEventId(Long radarEventId) { this.radarEventId = radarEventId; }
    public String getDirectNodeKey() { return directNodeKey; }
    public void setDirectNodeKey(String directNodeKey) { this.directNodeKey = directNodeKey; }
    public String getDirection() { return direction.name(); }
    public Direction direction() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction == null ? Direction.UNCERTAIN : direction; }
    public void setDirection(String direction) { this.direction = Direction.valueOf(direction); }
    public String getMechanism() { return mechanism.name(); }
    public Mechanism mechanism() { return mechanism; }
    public void setMechanism(Mechanism mechanism) { this.mechanism = mechanism == null ? Mechanism.DEMAND : mechanism; }
    public void setMechanism(String mechanism) { this.mechanism = Mechanism.valueOf(mechanism); }
    public String getHorizon() { return horizon.name(); }
    public Horizon horizon() { return horizon; }
    public void setHorizon(Horizon horizon) { this.horizon = horizon == null ? Horizon.SHORT : horizon; }
    public void setHorizon(String horizon) { this.horizon = Horizon.valueOf(horizon); }
    public String getConfidence() { return confidence.name(); }
    public Confidence confidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence == null ? Confidence.LOW : confidence; }
    public void setConfidence(String confidence) { this.confidence = Confidence.valueOf(confidence); }
    public String getImpactSummary() { return impactSummary; }
    public void setImpactSummary(String impactSummary) { this.impactSummary = impactSummary; }
    public String getAnalysisVersion() { return analysisVersion; }
    public void setAnalysisVersion(String analysisVersion) { this.analysisVersion = analysisVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getPathNodeKeys() { return pathNodeKeys; }
    public void setPathNodeKeys(List<String> pathNodeKeys) {
        this.pathNodeKeys = pathNodeKeys == null ? new ArrayList<String>() : new ArrayList<String>(pathNodeKeys);
    }
}
