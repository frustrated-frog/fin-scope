package com.finscope.service.quant.study;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.common.enums.quant.StudyState;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.quant.PaperStockNameRepository;
import com.finscope.dao.quant.study.AutonomousStudyRepository;
import com.finscope.rpc.llm.LunaResponsesClient;
import com.finscope.rpc.quant.PythonPaperClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class AutonomousStudyService {
    @Resource
    private AutonomousStudyRepository studies;
    @Resource
    private PythonPaperClient paper;
    @Resource
    private LunaResponsesClient llm;
    @Resource
    private PaperStockNameRepository stocks;
    @Resource
    private ObjectMapper json;

    private static final String PLAN = """
            你是面向学习者的股票量化研究员。从提供的可用材料自主提出一个具体、可证伪、能够由工具检验的问题。
            优先选择还没有研究过的方向与较完整的数据。初次优先研究组合；后续参照 memory 中的负面结论、已学内容和开放问题。
            ALLOCATION 对每次冻结的选股结果比较等权、逆波动率、风险平价，独立撮合；
            CORRELATION 把期初前五只当作观察组，其余成分的等权涨跌只用于划分交易日，比较前五只相互之间的相关性；EXECUTION 分析一天模拟账户真实发生的信号、费用与盈亏。
            返回一个 JSON 对象，option 是所选材料的整数序号，title 是中文研究问题，hypothesis 为事前假设，
            criterion 为可用现有工具输出核对的事前标准，concept 为核心金融概念，motivation 为选题缘由。
            如果沿用记忆中的开放问题，用 parentId 填该研究 ID，否则填 null。每项限一两句。不要预设赢家。
            """;
    private static final String TEACH = """
            你是量化金融教师。只依据本次冻结计划和真实计算结果，用中文输出 JSON，merits 是对象，其余字段为纯文本。
            finding 用两句回答研究问题，引用具体数字和区间；background 解释问题缘起；theory 解释金融机制；
            mathematics 说明公式、变量与单位，结合一组实际输出说明资金权重、风险贡献或相关性/成本的计算。
            ALLOCATION 的 comparison 对每一指标列出同组最优方法（净收益和夏普取高、回撤和实现波动取低）。
            指标为 null 或 comparison 列表为空说明无法比较，必须写明无法判断；单个决策的目标波动率不能代替全期实现波动率。
            method 解释具体对照、时点和样本；interpretation 解释投资启发；limits 说明证据边界；
            nextQuestion 提出一个可被后续实验推翻的具体问题。outcome 只能为 SUPPORTED、MIXED 或 UNSUPPORTED。
            value 为首要推荐理由类别，仅取 学习价值、研究价值、投资价值、方法价值、实用价值、创新价值 之一；
            merits 对象必须使用 学习价值、研究价值、投资价值、方法价值、实用价值、创新价值 六个键，逐项填 0 至 3 的整数。
            0 为缺少证据，1 为初步线索，2 为有样本支持，3 为证据充分且有代表性。投资价值须考虑风险、成本和验证范围。
            reason 说明即使结果亏损或假设失败为何值得读。不要把历史收益当预测，稀少样本不声称显著性。
            提及个股时使用 names 对应的股票名称，名称缺失就描述其权重。图表与数值由工具提供，不编造检验、数据或引用。每个字段最多 350 字。
            """;

    public long enqueue(String trigger) {
        return studies.enqueue(trigger, now());
    }

    public ObjectNode page(int offset) {
        ObjectNode page = json.createObjectNode();
        var rows = studies.list(offset, 21);
        ArrayNode items = page.putArray("items");
        rows.stream().limit(20).map(this::view).forEach(items::add);
        ArrayNode highlights = page.putArray("highlights");
        studies.highlights().stream().map(this::view).forEach(highlights::add);
        page.put("hasMore", rows.size() > 20);
        page.put("model", llm.model());
        return page;
    }

    public ObjectNode detail(long id) {
        var row = studies.find(id);
        if (row == null) {
            throw new ResourceNotFoundException("研究记录不存在");
        }
        return view(row);
    }

    public ObjectNode retry(long id) {
        studies.retry(id, now());
        return detail(id);
    }

    public ObjectNode question(long id, String question) {
        if (question == null || question.isBlank() || question.length() > 500) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "研究追问须为 1 至 500 字");
        }
        ObjectNode study = detail(id);
        if (!StudyState.PUBLISHED.name().equals(study.path("state").asText())) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "请在已发表的研究中提问");
        }
        ObjectNode input = teachingEvidence(study.path("plan"), study.path("evidence"));
        input.set("report",study.path("report"));
        input.put("question",question.strip());
        try {
            String answer = llm.completeText("你是金融学习助教。直接用中文正文回答本研究的追问，引用工具的计算结果和公式，"
                    + "说明证据不足的部分。使用股票名称。", input.toString(), 1800).strip();
            if (answer.isEmpty()) {
                throw new IllegalStateException("研究解答为空");
            }
            ObjectNode response = json.createObjectNode();
            response.put("answer", answer);
            response.put("model", llm.model());
            return response;
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR,"研究助教暂未返回解答，请重试",error);
        }
    }

    public void workOne() {
        String lease = UUID.randomUUID().toString();
        long started = now();
        Long id = studies.claim(lease, started, started+900);
        if (id == null) {
            return;
        }
        try {
            ObjectNode job = detail(id);
            JsonNode plan = job.path("plan");
            if (!plan.isObject()) {
                plan = discover();
                if (plan == null) {
                    studies.finish(id, lease, StudyState.WAITING_DATA, null, "当前可用材料已研究或暂无数据，等待新材料。", now());
                    return;
                }
                if (!studies.plan(id, lease, plan.path("material").path("source").asText(), plan.toString(), now())) {
                    studies.fail(id, lease, "该材料已由另一任务领取，将重新选题。", now());
                    return;
                }
            }
            JsonNode evidence = job.path("evidence");
            if (!evidence.isObject()) {
                ObjectNode input = json.createObjectNode();
                JsonNode material = plan.path("material");
                input.set("source", material.required("source"));
                input.set("tool", material.required("tool"));
                ObjectNode computed = (ObjectNode) paper.computeStudy(input);
                attachNames(computed);
                if (!studies.evidence(id, lease, computed.toString(), now())) {
                    return;
                }
                evidence = computed;
            }
            ObjectNode report = lesson(plan,evidence);
            studies.finish(id, lease, StudyState.PUBLISHED, report.toString(), null, now());
        } catch (Exception error) {
            String stage = detail(id).path("state").asText();
            log.warn("自主量化研究未完成 id={} stage={} cause={}", id, stage, error.getClass().getSimpleName());
            studies.fail(id, lease, switch (stage) {
                case "PLANNING" -> "AI 选题暂未成功，请检查模型配置或稍后重试。";
                case "COMPUTING" -> "工具未能完成计算，请核对行情覆盖或稍后重试。";
                default -> "教学报告尚未生成，已保存成功的研究步骤。";
            }, now());
        }
    }

    private ObjectNode discover() throws Exception {
        var used = new HashSet<>(studies.usedSources());
        List<JsonNode> options = new ArrayList<>();
        for (JsonNode item : paper.studySources()) {
            if (!used.contains(item.path("source").asText())) {
                options.add(item);
            }
        }
        if (options.isEmpty()) {
            return null;
        }
        ObjectNode input = json.createObjectNode();
        var materials = input.putArray("options");
        int index = 0;
        for (JsonNode option : options) {
            var item = ((ObjectNode) option).deepCopy();
            item.remove("source");
            item.put("option", index++);
            materials.add(item);
        }
        var memory = input.putArray("memory");
        Set<Long> parents = new HashSet<>();
        for (var row : studies.memory()) {
            var past = view(row);
            var entry = memory.addObject();
            long parent = past.path("id").asLong();
            parents.add(parent);
            entry.put("id", parent);
            entry.set("state", past.path("state"));
            entry.set("title", past.path("plan").path("title"));
            entry.set("hypothesis", past.path("plan").path("hypothesis"));
            entry.set("note", past.path("note"));
            entry.set("tool", past.path("plan").path("material").path("tool"));
            for (String key : List.of("finding","outcome","nextQuestion")) {
                entry.set(key, past.path("report").path(key));
            }
        }
        ObjectNode answer = ask(PLAN, input, 1700);
        JsonNode choice = answer.path("option");
        if (!choice.isIntegralNumber() || !choice.canConvertToInt() || choice.intValue()<0 || choice.intValue()>=options.size()) {
            throw new IllegalArgumentException("无效材料编号");
        }
        ObjectNode result = fields(answer,List.of("title","hypothesis","criterion","concept","motivation"));
        result.set("material", options.get(choice.intValue()));
        JsonNode parent = answer.path("parentId");
        if (parent.isIntegralNumber() && parents.contains(parent.asLong())) {
            result.set("parentId",parent);
        }
        result.put("model",llm.model());
        return result;
    }

    private ObjectNode teachingEvidence(JsonNode plan, JsonNode evidence) {
        ObjectNode input = json.createObjectNode();
        input.set("plan", plan);
        ObjectNode metrics = ((ObjectNode) evidence.required("metrics")).deepCopy();
        for (JsonNode node : metrics.path("legs")) {
            ObjectNode leg = (ObjectNode) node;
            leg.remove("curve");
            var samples = leg.remove("samples");
            if (samples != null && !samples.isEmpty()) {
                JsonNode example = samples.get(0);
                for (JsonNode sample : samples) {
                    if (sample.path("members").size()>1) {
                        example=sample;
                        break;
                    }
                }
                leg.set("example", example);
            }
        }
        input.set("metrics",metrics);
        input.set("names",evidence.path("names"));
        return input;
    }

    private ObjectNode lesson(JsonNode plan, JsonNode evidence) throws Exception {
        ObjectNode answer = ask(TEACH, teachingEvidence(plan, evidence), 5200);
        ObjectNode report = fields(answer, List.of("finding","background","theory","mathematics","method",
                "interpretation","limits","nextQuestion","outcome","value","reason"));
        if (!Set.of("SUPPORTED","MIXED","UNSUPPORTED").contains(report.path("outcome").asText())
                || !Set.of("学习价值","研究价值","投资价值","方法价值","实用价值","创新价值").contains(report.path("value").asText())) {
            throw new IllegalArgumentException("研究结论或推荐维度无效");
        }
        ObjectNode merits = report.putObject("merits");
        for (String dimension : List.of("学习价值", "研究价值", "投资价值", "方法价值", "实用价值", "创新价值")) {
            JsonNode score = answer.path("merits").path(dimension);
            if (!score.isInt() || score.intValue() < 0 || score.intValue() > 3) {
                throw new IllegalArgumentException("研究评价维度无效 " + dimension);
            }
            merits.put(dimension, score.intValue());
        }
        report.put("model", llm.model());
        return report;
    }

    private void attachNames(ObjectNode evidence) {
        ObjectNode names = evidence.with("names");
        var missing = new LinkedHashSet<String>();
        JsonNode metrics = evidence.path("metrics");
        for (JsonNode code : metrics.path("stocks")) {
            missing.add(code.asText());
        }
        for (JsonNode row : metrics.path("events")) {
            missing.add(row.path("code").asText());
        }
        for (JsonNode leg : metrics.path("legs")) {
            for (JsonNode sample : leg.path("samples")) {
                for (JsonNode member : sample.path("members")) {
                    missing.add(member.path("code").asText());
                }
            }
        }
        missing.removeIf(code -> !names.path(code).asText("").isBlank());
        if (!missing.isEmpty()) {
            stocks.findNames(List.copyOf(missing)).forEach(names::put);
        }
    }

    private ObjectNode ask(String prompt, ObjectNode input, int tokens) throws Exception {
        if (!llm.isConfigured()) {
            throw new IllegalStateException("模型未配置");
        }
        String text = llm.completeText(prompt, input.toString(), tokens);
        JsonNode value = json.readTree(text.strip().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""));
        if (!(value instanceof ObjectNode object)) {
            throw new IllegalArgumentException("模型响应格式无效");
        }
        return object;
    }

    private ObjectNode fields(ObjectNode answer, List<String> keys) {
        ObjectNode result = json.createObjectNode();
        for (String key : keys) {
            JsonNode value=answer.path(key);
            if (!value.isTextual() || value.asText().isBlank() || value.asText().length()>1600) {
                throw new IllegalArgumentException("研究字段无效 " + key);
            }
            result.put(key,value.asText().strip());
        }
        return result;
    }

    private ObjectNode view(Map<String,Object> row) {
        ObjectNode result = json.valueToTree(row);
        for (String key : List.of("plan","evidence","report")) {
            if (result.path(key).isTextual()) {
                try {
                    result.set(key,json.readTree(result.get(key).asText()));
                } catch (JsonProcessingException error) {
                    throw new IllegalStateException("研究记录无法读取",error);
                }
            }
        }
        return result;
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }
}
