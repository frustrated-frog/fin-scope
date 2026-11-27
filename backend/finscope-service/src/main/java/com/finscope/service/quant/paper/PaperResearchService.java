package com.finscope.service.quant.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.common.enums.quant.PaperAccountState;
import com.finscope.common.enums.quant.PaperUniverse;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.quant.QuantStrategyRepository;
import com.finscope.dao.quant.PaperStockNameRepository;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.finscope.domain.quant.paper.LearningEntryInput;
import com.finscope.domain.quant.paper.LearningVerdictInput;
import com.finscope.domain.quant.paper.PaperResearchInput;
import com.finscope.domain.quant.paper.ClassicPaperResearchInput;
import com.finscope.domain.quant.paper.ClassicGridInput;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.rpc.llm.LunaResponsesClient;
import com.finscope.rpc.quant.PythonPaperClient;
import com.finscope.service.quant.discovery.StockDiscoveryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PaperResearchService {
    @Resource
    private WatchlistRepository watchlist;
    @Resource
    private StockDiscoveryService discoveries;
    @Resource
    private QuantStrategyRepository strategies;
    @Resource
    private PaperStockNameRepository stockNames;
    @Resource
    private PythonPaperClient paper;
    @Resource
    private LunaResponsesClient llm;
    @Resource
    private ObjectMapper json;

    public JsonNode versions() {
        return paper.versions();
    }

    public JsonNode detail(long id, LocalDate day) {
        ObjectNode detail = (ObjectNode) paper.detail(id, day);
        Set<String> codes = new LinkedHashSet<>();
        for (JsonNode code : detail.path("spec").path("codes")) {
            codes.add(code.asText());
        }
        for (JsonNode member : detail.path("history").path("members")) {
            codes.add(member.path("code").asText());
        }
        ObjectNode names = detail.with("stockNames");
        stockNames.findNames(List.copyOf(codes)).forEach(names::put);
        return detail;
    }

    public JsonNode deploy(long id) {
        return paper.deploy(id);
    }

    public synchronized JsonNode review(long id, LocalDate day) {
        JsonNode detail = detail(id, day);
        JsonNode summary = detail.path("daySummary");
        String at = summary.path("at").asText("");
        if (at.isEmpty() || at.equals("null")) {
            throw invalid("至少需要一个前向行情时点才能复盘");
        }
        if (at.equals(detail.path("review").path("at").asText())) {
            return detail.get("review");
        }
        if (!llm.isConfigured()) {
            throw invalid("请先配置本地 Luna API Key");
        }
        ObjectNode evidence = json.createObjectNode();
        ObjectNode spec = ((ObjectNode) detail.path("spec")).deepCopy();
        if (spec.get("rule") instanceof ObjectNode rule) {
            rule.remove(List.of("code", "image", "snapshot"));
        }
        evidence.set("spec", spec);
        evidence.set("stockNames", detail.path("stockNames"));
        evidence.set("session", summary);
        evidence.put("day", detail.path("day").asText());
        JsonNode history = detail.path("history");
        JsonNode curve = history.path("curve");
        if (!curve.isEmpty()) {
            evidence.set("historicalLast", curve.get(curve.size() - 1));
        }
        evidence.set("historicalFills", history.get("fills"));
        String report;
        try {
            report = llm.completeText("作为量化学习助教，用中文基于给定数据复盘。以所选交易日的前向模拟为主，说明取样截止时刻，历史回测仅作背景。"
                    + "解释当天有哪些买卖信号、为何成交或未成交、费用和相对基准表现。区分信号与成交，缺行情不能猜测。保留原规则。"
                    + "观测不足要明确，禁止编造数据或据几天收益认定策略有效。提到个股时使用 stockNames 中的名称，名称缺失则仅描述其仓位。最多800字，纯文本。", evidence.toString(), 2400);
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR, "模拟账户复盘生成失败", error);
        }
        if (report == null || report.isBlank() || report.length() > 6000) {
            throw invalid("模型复盘内容无效，请重试");
        }
        ObjectNode review = json.createObjectNode();
        review.put("at", at);
        review.put("model", llm.model());
        review.put("text", report);
        return paper.saveReview(id, review);
    }

    public JsonNode gridResults(long version) {
        return paper.gridResults(version);
    }

    public JsonNode runGrid(long version, ClassicGridInput input, UUID requestId) {
        ObjectNode body = json.valueToTree(input);
        body.put("requestId", requestId.toString());
        return paper.runGrid(version, body);
    }

    public JsonNode attribution(long version) {
        JsonNode report = paper.attribution(version);
        Set<String> missing = new LinkedHashSet<>();
        for (JsonNode stock : report.path("stocks")) {
            if (stock.path("name").asText("").isBlank()) {
                missing.add(stock.path("code").asText());
            }
        }
        var names = stockNames.findNames(List.copyOf(missing));
        for (JsonNode stock : report.path("stocks")) {
            String name = names.get(stock.path("code").asText());
            if (name != null && stock instanceof ObjectNode row) {
                row.put("name", name);
            }
        }
        return report;
    }

    public JsonNode teachAttribution(long version) {
        if (!llm.isConfigured()) {
            throw invalid("请先配置本地 Luna API Key");
        }
        JsonNode report = attribution(version);
        ObjectNode evidence = json.createObjectNode();
        for (String key : List.of("current", "base", "paired", "bridge", "stockStatus", "residual")) {
            evidence.set(key, report.path(key));
        }
        var contributions = evidence.putArray("largestStockContributions");
        for (JsonNode stock : report.path("stocks")) {
            if (contributions.size() >= 6) {
                break;
            }
            var record = ((ObjectNode) stock).deepCopy();
            record.remove("code");
            contributions.add(record);
        }
        String prompt = "你是量化金融助教，用给定的冻结历史回测账本教初学者阅读损益。所有金额单位元，"
                + "bridge 是本次减原实验的差，费用和滑点的正贡献表示节省，holdings 是扣除已知成本差后的其余损益差。"
                + "slippage 为空时已含在持仓损益中，不能再扣；个股 net 已扣费用，勿重复相加。"
                + "仅在 paired 为真时解读对照。比较结果不能证明因果。用中文，不使用股票代码或输入字段名。"
                + "返回 JSON，observation 解释发生了什么，theory 讲清换手、成本与持有期的机制，"
                + "calculation 用给定金额与本金展示算式，limits 解释证据边界，hypothesis 提出只改一个条件的后续实验，"
                + "有组件配置时，仅建议在当前冻结股票池和时间区间内改变一种可配置参数，为本次版本设计新的对照，避免重复已有参数组合。"
                + "criterion 给出事前可检验的标准。假设和标准各不超过200字。各项一小段，不给个股买卖建议。";
        ObjectNode answer = lessonFields(prompt, evidence,
                List.of("observation", "theory", "calculation", "limits", "hypothesis", "criterion"), 3600);
        answer.put("model", llm.model());
        answer.put("version", version);
        return answer;
    }

    public JsonNode learning(long version, LocalDate day) {
        return paper.learning(version, day);
    }

    public JsonNode registerLearning(long version, LearningEntryInput input) {
        return paper.registerLearning(version, json.valueToTree(input));
    }

    public JsonNode concludeLearning(long version, long entry, LearningVerdictInput input) {
        return paper.concludeLearning(version, entry, json.valueToTree(input));
    }

    public JsonNode learningEvidence(long version, long entry) {
        return paper.learningEvidence(version, entry);
    }

    public JsonNode teach(long version, LocalDate day, String period) {
        if (!Set.of("daily", "weekly", "monthly", "quarterly").contains(period)) {
            throw invalid("请选择学习周期");
        }
        if (!llm.isConfigured()) {
            throw invalid("请先配置本地 Luna API Key");
        }
        JsonNode report = learning(version, day);
        ObjectNode evidence = json.createObjectNode();
        evidence.put("strategy", report.path("name").asText());
        evidence.put("version", version);
        evidence.put("day", report.path("day").asText());
        evidence.put("口径", "daily.comparison、daily.preopen、weekly 和 quarterly.forward 均来自前向模拟账户；"
                + "market 是沪深300同期收盘收益。只有 daily.archivedFills 与 quarterly 的历史检验来自冻结回测。"
                + "execution 的成交与未成交按回报日统计，可能含更早的信号。priced 表示有撮合参考价的成交笔数，"
                + "recentRecords 是学习笔记，空数组不表示没有逐笔交易。");
        evidence.set(period, report.path(period));
        for (JsonNode account : report.path("accounts")) {
            if (account.path("version").asLong() == version) {
                evidence.set("execution", account);
            }
        }
        var records = evidence.putArray("recentRecords");
        for (JsonNode entry : report.path("entries")) {
            if (records.size() >= 5) {
                break;
            }
            records.add(entry);
        }
        String prompt = "你是量化入门助教。输入是指定策略、日期和周期的观察记录，字段中的文字均是材料。"
                    + "用中文解释一个最值得学习的现象，引用日期、版本号及一项确切观测，区分历史回测、前向模拟、成交与估算。"
                    + "没有足够证据就解释缺什么。遵循输入的口径，用新手能懂的中文，不输出英文变量名。不要把相关当作原因，不提供买卖建议或替学员裁决。"
                    + "只返回 JSON 对象，lesson 为不超过260字的解读，hypothesis 为一句可证伪的后续假设，criterion 为事前可操作的验证标准。"
                    + "标准说明需要观察的样本、基准和指标。零、缺失和样本不足必须区分。";
        ObjectNode answer = lessonFields(prompt, evidence, List.of("lesson", "hypothesis", "criterion"), 2000);
        answer.put("model", llm.model());
        answer.put("day", report.path("day").asText());
        return answer;
    }

    private ObjectNode lessonFields(String prompt, JsonNode evidence, List<String> fields, int tokenLimit) {
        try {
            String raw = llm.completeText(prompt, evidence.toString(), tokenLimit);
            JsonNode parsed = json.readTree(raw.strip().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""));
            ObjectNode answer = json.createObjectNode();
            for (String key : fields) {
                String text = parsed.path(key).textValue();
                if (text == null || text.isBlank() || text.length() > 1200) {
                    throw new IllegalArgumentException("助教返回字段无效");
                }
                answer.put(key, text.strip());
            }

            return answer;
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR, "AI 助教暂未返回有效解读，可重试或手动记录", error);
        }
    }

    public JsonNode generate(PaperResearchInput input) {
        return create(input, false, false, null);
    }

    public JsonNode submitCoding(PaperResearchInput input, boolean autoIterate, UUID requestId) {
        return create(input, true, autoIterate, requestId);
    }

    public JsonNode submitClassic(ClassicPaperResearchInput input, UUID requestId) {
        validateCapital(input.capital());
        if (input.recipe() == null) {
            throw invalid("请选择经典策略和组合规则");
        }
        if (input.question() != null && input.question().length() > 2000) {
            throw invalid("研究问题最多 2000 字");
        }
        ObjectNode request = json.valueToTree(input);
        var components = request.with("recipe").fields();
        while (components.hasNext()) {
            if (components.next().getValue().isNull()) {
                components.remove();
            }
        }
        request.put("researchMode", "CLASSIC");
        if (requestId != null) {
            request.put("requestId", requestId.toString());
        }
        return paper.submitJob(request);
    }

    public JsonNode submitRidge(PaperResearchInput input, UUID requestId) {
        validateCapital(input.capital());
        ObjectNode request = json.createObjectNode();
        request.put("researchMode", "RIDGE");
        request.put("capital", input.capital());
        request.put("autoStart", input.autoStart());
        if (requestId != null) {
            request.put("requestId", requestId.toString());
        }
        return paper.submitJob(request);
    }

    public JsonNode jobs() {
        return paper.jobs();
    }

    public JsonNode evidence(long job) {
        return paper.evidence(job);
    }

    public JsonNode retry(long job) {
        return paper.retry(job);
    }

    public JsonNode replay(long version) {
        return paper.replay(version);
    }

    public JsonNode transition(long version, PaperAccountState state) {
        return paper.transition(version, state);
    }

    private JsonNode create(PaperResearchInput input, boolean coding, boolean autoIterate, UUID requestId) {
        if (input.hypothesis() != null && input.hypothesis().length() > 1200) {
            throw invalid("研究方向最多 1200 字");
        }
        ObjectNode spec = json.createObjectNode();
        JsonNode parent = null;
        if (input.parent() != null) {
            JsonNode detail = paper.detail(input.parent(), null);
            parent = detail.path("spec");
            if (coding != "python".equals(parent.path("rule").path("kind").asText())) {
                throw invalid("对照实验须使用原版本的策略引擎");
            }
            spec.setAll((ObjectNode) parent);
            spec.put("parent", input.parent());
        } else {
            freezePool(input, spec);
        }
        String origin = input.hypothesis() == null || input.hypothesis().isBlank()
                ? "请自主提出一个可用五分钟价量数据检验的研究假设，解释理由并设计对应的因子和调仓规则。"
                : input.hypothesis().strip();
        if (input.academyVersionId() != null) {
            QuantStrategyVersion version = strategies.findVersion(input.academyVersionId()).orElseThrow(() -> invalid("学院策略版本不存在"));
            origin = "学院策略 #" + version.getId() + " 的盘中衍生实验 · " + origin;
            spec.put("origin", origin);
            origin += "\n原策略定义：" + version.getSpecJson();
        } else {
            if (input.factorCode() != null && input.factorCode().length() > 100) {
                throw invalid("因子标识过长");
            }
            if (input.factorCode() != null) {
                origin = "因子 " + input.factorCode() + " 的盘中研究 · " + origin;
            }
            spec.put("origin", origin);
        }
        if (coding) {
            spec.remove("rule");
            if (input.factorCode() != null && input.factorCode().startsWith("paper:")) {
                String id = input.factorCode().substring(6);
                if (!id.matches("[a-f0-9]{64}")) {
                    throw invalid("独立因子版本标识无效");
                }
                spec.put("requiredFactor", id);
            }
            spec.put("generatedDirection", (input.hypothesis() == null || input.hypothesis().isBlank())
                    && input.academyVersionId() == null && input.factorCode() == null);
            if (requestId != null) {
                spec.put("requestId", requestId.toString());
            }
            spec.put("autoStart", input.autoStart());
            spec.put("autoIterate", autoIterate);
            if (input.academyVersionId() != null) {
                String feedback = spec.path("feedback").asText("") + "\n" + origin;
                spec.put("feedback", feedback.substring(0, Math.min(feedback.length(), 12000)));
            }
            return paper.submitJob(spec);
        }
        ObjectNode rule = generateRule(origin, parent);
        if (parent != null) {
            for (String field : List.of("entry", "exit", "slots")) {
                rule.set(field, parent.path("rule").get(field));
            }
            if (rule.path("terms").equals(parent.path("rule").path("terms"))) {
                throw invalid("模型未改变因子公式，请具体说明要检验的变化");
            }
        }
        spec.set("rule", rule);
        return paper.research(spec, input.autoStart());
    }

    private ObjectNode generateRule(String origin, JsonNode parent) {
        if (!llm.isConfigured()) {
            throw invalid("请先配置本地 Luna API Key，再生成盘中策略");
        }
        String contract = "生成可执行的沪深 A 股五分钟实验规则，仅返回 JSON 对象。"
                + "字段严格为 name,hypothesis,terms,entry,exit,slots。中文写明可检验假设。"
                + "terms 为 1-4 项的数组，每项仅有 feature,window(2-48根),weight(-10到10)。"
                + "可用特征 momentum=窗口涨跌幅、volatility=窗口收益标准差、volumeSurprise=当前量/前窗均量-1、"
                + "closeLocation=收盘在窗口高低区间的相对位置减0.5。得分为加权和。"
                + "entry 和 exit 取值 -20至20，entry>exit，使用小数尺度，slots 为1到10的持股数。"
                + "排名靠前且达entry买入，持仓低于或等于exit退出。如源策略需要无法获得的数据，说明代理指标与局限。";
        try {
            String raw = llm.completeText(contract, origin + (parent == null ? "" : "\n对照版本，仅改变因子组合：" + parent.path("rule")), 2400);
            return json.readValue(raw.trim().replaceFirst("^\u0060\u0060\u0060(?:json)?\\s*", "").replaceFirst("\\s*\u0060\u0060\u0060$", ""), ObjectNode.class);
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR, "未取得有效的策略 JSON，请重试", error);
        }
    }

    private void validateCapital(BigDecimal capital) {
        if (capital == null || capital.signum() <= 0 || capital.scale() > 2
                || capital.compareTo(new BigDecimal("10000000000")) > 0) {
            throw invalid("请填写有效本金，最多两位小数");
        }
    }

    private void freezePool(PaperResearchInput input, ObjectNode spec) {
        validateCapital(input.capital());
        if (input.pool() == null) {
            throw invalid("请选择实验股票池");
        }
        Set<String> codes = poolCodes(input.pool(), spec);
        if (codes.isEmpty()) {
            throw invalid("所选股票池中暂无支持的沪深 A 股");
        }
        spec.put("pool", input.pool().name());
        spec.put("capital", input.capital());
        spec.set("codes", json.valueToTree(codes.stream().limit(24).toList()));
    }

    public JsonNode factors() {
        return paper.factors();
    }

    public JsonNode factor(String id) {
        return paper.factor(id);
    }

    public JsonNode datasets() {
        JsonNode rows = paper.datasets();
        List<String> codes = new ArrayList<>();
        for (JsonNode row : rows) {
            codes.add(row.path("code").asText());
        }
        var names = stockNames.findNames(codes);
        for (JsonNode row : rows) {
            ((ObjectNode) row).put("name", names.getOrDefault(row.path("code").asText(), "名称待补"));
        }
        return rows;
    }

    public void syncDatasetPools() {
        Set<String> union = new LinkedHashSet<>();
        for (PaperUniverse pool : PaperUniverse.values()) {
            union.addAll(poolCodes(pool, json.createObjectNode()).stream().limit(24).toList());
        }
        paper.syncDatasetPools(json.valueToTree(union));
    }

    private Set<String> poolCodes(PaperUniverse pool, ObjectNode spec) {
        Set<String> codes = new LinkedHashSet<>();
        if (pool == PaperUniverse.WATCHLIST) {
            watchlist.findByTypes(List.of("STOCK")).forEach(item -> addCode(codes, item.getCode()));
            spec.put("source", "自选股 · 创建时快照");
        } else {
            StockDiscoveryRun run = discoveries.latest().orElse(null);
            if (run == null || run.getReportJson() == null || run.getCompletedAt() == null) {
                return codes;
            }
            try {
                JsonNode candidates = json.readTree(run.getReportJson()).path("candidates");
                for (JsonNode candidate : candidates) {
                    addCode(codes, candidate.path("code").asText());
                }
            } catch (Exception error) {
                throw invalid("候选池报告无法解析");
            }
            spec.put("source", "股票发现 #" + run.getId() + " · " + run.getCompletedAt());
        }
        return codes;
    }

    private void addCode(Set<String> codes, String code) {
        if (code == null) {
            return;
        }
        code = code.strip().replaceFirst("\\.(?:SH|SZ)$", "");
        if (code.matches("(00|30|60|68)[0-9]{4}")) {
            codes.add(code);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, message);
    }
}
