package com.finscope.service.radar;

import com.finscope.domain.radar.NewsworthinessAssessment;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** 公共价值与传播热度分开计算；复用热度阶段已提取的特征，不引入模型或外部调用。 */
@Service
public class RadarNewsworthinessService {
    public static final String VERSION = "NEWS_VALUE_V1";
    private static final int MAX_REMEMBERED_FACTS = 64;
    private static final Pattern POLICY = Pattern.compile("(央行|人民银行|美联储|国务院|证监会|政府|国会).{0,30}(降准|降息|加息|下调.*准备金率|维持.*利率|出口管制|批准|通过|实施|禁止|取消限制)");
    private static final Pattern EARNINGS = Pattern.compile("业绩预告|季度业绩|季度.*(收入|营收|净利润)|净利润.*同比|营收.*同比|预增|预亏|扭亏");
    private static final Pattern PUBLIC_POLICY = Pattern.compile("(财政部|商务部|国务院|发改委|全国|央行|人民银行).*(发布|公布|出台|实施|调整|批准|取消|修订)|停火|重大事故|立案调查|留置|终止.*合资");
    private static final Pattern COMPANY_PREFIX = Pattern.compile("^([^:：]{2,16})[:：]");
    private static final Pattern DEVELOPMENT = Pattern.compile("降准|降息|加息|出口管制|正式实施|获批|业绩预告|重大资产重组|收购|中标|新一代|新模型|新款|量产|突破|投产");
    private static final Pattern ROUTINE = Pattern.compile("^预告[:：]|(定于|将于).*新闻发布会|投资日历|大事提醒|(召开|举行|召开情况).{0,20}业绩说明会|业绩说明会.{0,15}(召开|通知|公告|纪要)|董事会.*(会议|决议)|股东.*(通知|决议)|融资余额|融资买入|龙虎榜|前十名.*持股|前十名股东|律师事务所|法律意见|核查意见|投资者关系活动记录表|授予.*(结果|登记)|归属名单|收购报告书摘要|闲置募集资金");
    private static final Pattern UNCERTAIN = Pattern.compile("传闻|网传|未经证实|据传|有望|或将|可能会|考虑|研究是否|拟议|否认|尚未|不会|未宣布|是否|还.*吗|[？?]");
    @Resource
    private NewswireRankingProperties properties;

    public NewsworthinessAssessment assess(RadarSourceIndependenceService.Analysis analysis, int heat,
                                          NewsworthinessAssessment previous, LocalDateTime now) {
        NewsworthinessAssessment result = new NewsworthinessAssessment();
        result.setVersion(VERSION);
        result.setEvaluatedAt(now);
        result.setHeat(Math.max(0, Math.min(100, heat)));
        Map<String, RadarSourceIndependenceService.Observation> facts = facts(analysis, now);
        Set<String> known = new LinkedHashSet<>();
        if (previous != null && previous.getKnownFacts() != null) {
            known.addAll(previous.getKnownFacts());
        }
        LocalDateTime meaningful = previous == null ? null : previous.getMeaningfulChangeAt();
        int added = 0;
        for (Map.Entry<String, RadarSourceIndependenceService.Observation> entry : facts.entrySet()) {
            LocalDateTime at = eventTime(entry.getValue().getSignal());
            if (!known.contains(entry.getKey()) && (previous == null || previous.getEvaluatedAt() == null
                    || at.isAfter(previous.getEvaluatedAt()))) {
                added++;
                if (meaningful == null || at.isAfter(meaningful)) {
                    meaningful = at;
                }
            }
            known.add(entry.getKey());
        }
        // 初次升级建立基线也使用原始发布时间，不能把一批旧闻变成刚发生的进展。
        if (meaningful == null) {
            meaningful = facts.values().stream().map(value -> eventTime(value.getSignal())).max(Comparator.naturalOrder()).orElse(null);
        }
        List<String> memory = new ArrayList<>(known);
        result.setKnownFacts(memory.subList(Math.max(0, memory.size() - MAX_REMEMBERED_FACTS), memory.size()));
        result.setMeaningfulChangeAt(meaningful);
        result.setNewFactCount(previous == null ? 0 : added);
        double freshness = decay(meaningful, now, 6);
        result.setFreshness(points(freshness * 100));
        result.setNovelty(points(decay(meaningful, now, 2) * 100));
        result.setImportance(importance(facts.values()));
        result.setQuality(quality(analysis, facts.values()));
        double weighted = result.getImportance() * properties.getImportanceWeight()
                + result.getNovelty() * properties.getNoveltyWeight() + result.getHeat() * properties.getHeatWeight()
                + result.getFreshness() * properties.getFreshnessWeight();
        // 重要性也需要时间衰减；转载更新采集时间不会刷新 meaningful。
        double qualityFactor = 0.50 + result.getQuality() / 200.0;
        double importanceFactor = 0.40 + result.getImportance() * 0.006;
        result.setPublicScore(points(weighted * qualityFactor * Math.sqrt(freshness) * importanceFactor));
        result.setDiversityKey(diversityKey(facts.values()));
        result.setExplanation("公共资讯价值 " + result.getPublicScore() + "；重要性 " + result.getImportance()
                + "；事实新意 " + result.getNovelty() + "；传播热度 " + result.getHeat()
                + "；进展时效 " + result.getFreshness() + "；内容质量 " + result.getQuality()
                + "；本轮新增事实特征 " + result.getNewFactCount() + "；评分版本 " + VERSION);
        return result;
    }

    private Map<String, RadarSourceIndependenceService.Observation> facts(RadarSourceIndependenceService.Analysis analysis, LocalDateTime now) {
        Map<String, RadarSourceIndependenceService.Observation> facts = new LinkedHashMap<>();
        if (analysis == null) {
            return facts;
        }
        List<RadarSourceIndependenceService.Observation> ordered = new ArrayList<>(analysis.getObservations());
        ordered.sort(Comparator.comparing(value -> eventTime(value.getSignal()), Comparator.nullsLast(Comparator.naturalOrder())));
        for (RadarSourceIndependenceService.Observation observation : ordered) {
            RadarSignalFeatures value = observation.getFeatures();
            LocalDateTime at = eventTime(observation.getSignal());
            if (at == null || at.isAfter(now) || value.getNormalizedTitle().isBlank()) {
                continue;
            }
            String signature;
            if (!value.getNumericFacts().isEmpty() || (!value.getActions().isEmpty()
                    && (!value.getSubjects().isEmpty() || !value.getEntities().isEmpty()))) {
                Set<String> actions = new TreeSet<>(value.getActions());
                actions.remove("公告");
                actions.remove("正式发布");
                signature = sorted(value.getSubjects()) + "|" + sorted(value.getEntities()) + "|" + actions
                        + "|" + sorted(value.getVariables()) + "|" + sorted(value.getDirections()) + "|" + sorted(value.getNumericFacts());
            } else {
                signature = value.getNormalizedContent().isBlank() ? value.getNormalizedTitle() : value.getNormalizedContent();
            }
            facts.putIfAbsent(hash(signature), observation);
        }
        return facts;
    }

    private int importance(java.util.Collection<RadarSourceIndependenceService.Observation> facts) {
        int best = 0;
        for (RadarSourceIndependenceService.Observation observation : facts) {
            RadarSignal signal = observation.getSignal();
            String title = safe(signal.getTitle());
            int score = 35;
            if (DEVELOPMENT.matcher(title).find()) {
                score = 70;
            }
            if (EARNINGS.matcher(title).find()) {
                score = Math.max(score, 75);
            }
            if (PUBLIC_POLICY.matcher(title).find()) {
                score = Math.max(score, 80);
            }
            if (POLICY.matcher(title).find()) {
                score = 95;
            }
            if (ROUTINE.matcher(title).find()) {
                score = Math.min(score, 10);
            }
            if (UNCERTAIN.matcher(title).find()) {
                score = Math.min(score, 40);
            }
            best = Math.max(best, score);
        }
        return best;
    }

    private int quality(RadarSourceIndependenceService.Analysis analysis, java.util.Collection<RadarSourceIndependenceService.Observation> facts) {
        int best = 0;
        for (RadarSourceIndependenceService.Observation observation : facts) {
            RadarSignal signal = observation.getSignal();
            RadarSignalFeatures features = observation.getFeatures();
            int value = safe(signal.getTitle()).length() >= 8 ? 25 : 10;
            if (safe(signal.getContent()).length() >= 20) {
                value += 25;
            }
            if (!features.getNumericFacts().isEmpty() || !features.getActions().isEmpty()) {
                value += 25;
            }
            if (UNCERTAIN.matcher(safe(signal.getTitle())).find()) {
                value = Math.min(value, 30);
            }
            best = Math.max(best, value);
        }
        return facts.isEmpty() ? 0 : points(best + analysis.getAuthorityScore() * 25);
    }

    private String diversityKey(java.util.Collection<RadarSourceIndependenceService.Observation> facts) {
        Set<String> subjects = new TreeSet<>();
        for (RadarSourceIndependenceService.Observation observation : facts) {
            subjects.addAll(observation.getFeatures().getEntities());
            subjects.addAll(observation.getFeatures().getSubjects());
            java.util.regex.Matcher company = COMPANY_PREFIX.matcher(safe(observation.getSignal().getTitle()));
            if (company.find()) {
                return "subject:" + company.group(1).trim();
            }
        }
        return subjects.isEmpty() ? null : "subject:" + subjects.iterator().next();
    }

    private String sorted(Set<String> values) { return new TreeSet<>(values).toString(); }
    private String safe(String text) { return text == null ? "" : text; }
    private LocalDateTime eventTime(RadarSignal signal) { return signal.getAiMeaningfulAt() != null ? signal.getAiMeaningfulAt() : signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt(); }
    private double decay(LocalDateTime at, LocalDateTime now, double halfLifeHours) {
        if (at == null || at.isAfter(now) || !at.isAfter(now.minusHours(48))) {
            return 0;
        }
        return Math.pow(0.5, Duration.between(at, now).toMinutes() / 60.0 / halfLifeHours);
    }
    private int points(double value) { return (int) Math.max(0, Math.min(100, Math.round(value))); }
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)), 0, 8);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("无法计算资讯事实摘要", error);
        }
    }
}
