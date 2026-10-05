package com.finscope.service.financials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.common.enums.financials.FinancialInterpretationStatus;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinancialInterpretationAgent {
    @Autowired
    private LlmChatClient llm;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private FinancialInterpretationResponseParser parser;
    @Autowired
    private FinancialInterpretationGate gate;
    @Autowired
    private FinancialInterpretationFallbackBuilder fallback;

    public FinancialInterpretation interpret(FinancialEvidencePacket packet) {
        return interpretWithMetrics(packet).getValue();
    }

    public Execution interpretWithMetrics(FinancialEvidencePacket packet) {
        if (!llm.isConfigured()) {
            return new Execution(fallback(packet, "LLM_NOT_CONFIGURED", new ArrayList<String>()), 0);
        }
        List<String> errors = new ArrayList<String>();
        String output = null;
        int llmCallCount = 0;
        try {
            llmCallCount++;
            output = llm.complete(systemPrompt(packet), modelPayload(packet));
            try {
                return new Execution(success(packet, output, "LLM", errors), llmCallCount);
            } catch (IllegalArgumentException first) {
                errors.add("首次输出：" + shorten(first.getMessage(), 6000));
            }
            llmCallCount++;
            output = llm.complete(systemPrompt(packet) + "修复validationError，只返回完整修复JSON。", repairInput(packet, output, errors.get(0)));
            try {
                return new Execution(success(packet, output, "REPAIRED", errors), llmCallCount);
            } catch (IllegalArgumentException second) {
                errors.add("修复输出：" + shorten(second.getMessage(), 6000));
                return new Execution(fallback(packet, "OUTPUT_REJECTED_BY_GATE", errors), llmCallCount);
            }
        } catch (SocketTimeoutException error) {
            errors.add("模型调用超时：" + message(error));
            return new Execution(fallback(packet, "LLM_TIMEOUT", errors), llmCallCount);
        } catch (Exception error) {
            errors.add("模型调用失败：" + message(error));
            return new Execution(fallback(packet, "LLM_UNAVAILABLE", errors), llmCallCount);
        }
    }

    public static final class Execution {
        private final FinancialInterpretation value;
        private final int llmCallCount;

        public Execution(FinancialInterpretation value, int llmCallCount) {
            this.value = value;
            this.llmCallCount = llmCallCount;
        }

        public FinancialInterpretation getValue() {
            return value;
        }

        public int getLlmCallCount() {
            return llmCallCount;
        }
    }

    public String modelName() {
        return llm.modelName();
    }

    private FinancialInterpretation success(FinancialEvidencePacket packet, String output,
                                            String mode, List<String> priorErrors) {
        try {
            JsonNode root = parser.parse(output);
            FinancialInterpretation.Result accepted = gate.apply(root, packet);
            FinancialInterpretation value = base(packet);
            value.setStatus(FinancialInterpretationStatus.SUCCESS.code());
            value.setGenerationMode(mode);
            value.setResult(accepted);
            value.setValidationErrors(new ArrayList<String>(priorErrors));
            return value;
        } catch (Exception error) {
            throw error instanceof IllegalArgumentException
                    ? (IllegalArgumentException) error
                    : new IllegalArgumentException(message(error), error);
        }
    }

    private FinancialInterpretation fallback(FinancialEvidencePacket packet, String reason,
                                             List<String> errors) {
        FinancialInterpretation value = fallback.build(packet, reason);
        value.setModelName(llm.modelName());
        value.setValidationErrors(new ArrayList<String>(errors));
        value.setFailureMessage(errors.isEmpty() ? reason : errors.get(errors.size() - 1));
        return value;
    }

    private FinancialInterpretation base(FinancialEvidencePacket packet) {
        FinancialInterpretation value = new FinancialInterpretation();
        value.setReportId(packet.getReportId());
        value.setPromptVersion(packet.getPromptVersion());
        value.setModelName(llm.modelName());
        return value;
    }

    private String repairInput(FinancialEvidencePacket packet, String invalidOutput,
                               String validationError) throws Exception {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("validationError", validationError);
        value.put("invalidOutput", shorten(invalidOutput, 48000));
        value.put("evidencePacket", json.readTree(modelPayload(packet)));
        return json.writeValueAsString(value);
    }

    private String legacyPrompt() {
        return "你是上市公司财报解读Agent，必须按report中市场、币种、实际报告期和合并口径分析。只能使用evidence中的证据和数字，只能引用现有id；" +
                "输出单个JSON对象，字段为operatingState、confidence、executiveSummary、periodChanges、" +
                "crossStatementInsights、dimensions、positiveSignals、risks、turningPoints、watchpoints、" +
                "limitations、disclaimer。所有Claim必须包含claim、claimType、refs；executiveSummary必须是数组，输出3条；" +
                "periodChanges输出2至3条最重要的同比、环比或连续趋势，证据不足可为空；" +
                "crossStatementInsights输出1至3条利润表、资产负债表、现金流量表之间的联动观察，证据不足可为空；" +
                "positiveSignals、risks、turningPoints、watchpoints必须是数组；积极信号和风险各不超过3条，拐点和观察项各不超过2条。" +
                "dimensions必须是数组，元素必须包含code、assessment、summary、refs、details；" +
                "details为1至2条Claim，分别说明最关键的事实、趋势、驱动或反证。" +
                "limitations必须是字符串数组，disclaimer必须是字符串。" +
                "operatingState只能是IMPROVING、STABLE、UNDER_PRESSURE、INSUFFICIENT_EVIDENCE之一；" +
                "confidence只能是HIGH、MEDIUM、LOW之一；每个dimension的assessment只能是POSITIVE、" +
                "NEUTRAL、NEGATIVE、INSUFFICIENT_EVIDENCE之一。" +
                "每条实质结论必须有refs；dimensions必须完整覆盖GROWTH、PROFITABILITY、" +
                "EARNINGS_QUALITY、CASH_QUALITY、ASSET_QUALITY、SOLVENCY_CAPITAL_DISCIPLINE。" +
                "优先描述方向、关系和经营含义，页面会通过refs展示精确数值；除非结论必须精确量化，否则不要复述数字。" +
                "不得出现evidence未原样提供的数字，包括自行计算的比率、倍数、天数、预测值和序号。" +
                "不得重新计算、创造数字、给出买卖建议、目标价或收益承诺；事实、推断和观察项分别标记" +
                "FACT、INFERENCE、WATCHPOINT；原因和影响必须标为INFERENCE；单条claim不超过80个中文字符，" +
                "维度summary不超过60个中文字符；数据不足时使用INSUFFICIENT_EVIDENCE。只返回JSON。";
    }

    private String systemPrompt(FinancialEvidencePacket packet) {
        if (!FinancialInterpretationReportFramework.VERSION.equals(packet.getPromptVersion())) {
            return legacyPrompt();
        }
        return "你是严谨的公司财务研究员，为初学者撰写详细中文报告。读者要理解发生了什么、如何判断和如何核查。" +
                "严格根据reportScope的市场、币种、实际报告期和合并口径分析；未知行业和会计准则不猜测，不套统一阈值。" +
                "chapterPlan是服务端分析提纲，refs列出本章可用材料，limitations列出缺口，教学说明是一般知识而非公司事实。" +
                "先按提纲组织论证，再输出一个完整JSON，不要Markdown。顶层字段：operatingState、confidence、executiveSummary、" +
                "periodChanges、crossStatementInsights、sections、dimensions、positiveSignals、risks、turningPoints、watchpoints、limitations、disclaimer。" +
                "operatingState只能是IMPROVING、STABLE、UNDER_PRESSURE、INSUFFICIENT_EVIDENCE；confidence只能是HIGH、MEDIUM、LOW且不超过qualityCeiling。" +
                "executiveSummary必须是数组，三至五条综合判断；periodChanges与crossStatementInsights必须是数组，无材料允许空。" +
                "dimensions、positiveSignals、risks、turningPoints、watchpoints输出空数组，避免重复sections。" +
                "sections必须按chapterPlan顺序覆盖全部十章。每章字段code、assessment、confidence、summary、refs、facts、analysis、counterEvidence、watchpoints、limitations。" +
                "assessment只能是POSITIVE、NEUTRAL、NEGATIVE、INSUFFICIENT_EVIDENCE；summary为两至四句综合判断，refs引用本章证据。" +
                "facts、analysis、counterEvidence、watchpoints均为Claim数组。Claim字段claim、claimType、confidence、refs。" +
                "Claim的文本键必须叫claim，不能叫summary或text。结构示例（省略的章节仍必须完整输出，示例占位id必须替换）：" +
                "{\"operatingState\":\"STABLE\",\"confidence\":\"MEDIUM\",\"executiveSummary\":[{\"claim\":\"综合判断\",\"claimType\":\"INFERENCE\",\"confidence\":\"MEDIUM\",\"refs\":[\"已有id\"]}]," +
                "\"periodChanges\":[],\"crossStatementInsights\":[],\"sections\":[{\"code\":\"PERFORMANCE_TRENDS\",\"assessment\":\"NEUTRAL\",\"confidence\":\"MEDIUM\",\"summary\":\"本章判断\",\"refs\":[\"已有id\"]," +
                "\"facts\":[{\"claim\":\"事实\",\"claimType\":\"FACT\",\"confidence\":\"MEDIUM\",\"refs\":[\"已有id\"]}],\"analysis\":[{\"claim\":\"完整分析段落\",\"claimType\":\"INFERENCE\",\"confidence\":\"MEDIUM\",\"refs\":[\"已有id\"]}]," +
                "\"counterEvidence\":[{\"claim\":\"替代解释\",\"claimType\":\"INFERENCE\",\"confidence\":\"LOW\",\"refs\":[\"已有id\"]}],\"watchpoints\":[{\"claim\":\"下一步核查\",\"claimType\":\"WATCHPOINT\",\"confidence\":\"LOW\",\"refs\":[\"已有id\"]}],\"limitations\":[]}]," +
                "\"dimensions\":[],\"positiveSignals\":[],\"risks\":[],\"turningPoints\":[],\"watchpoints\":[],\"limitations\":[],\"disclaimer\":\"仅用于研究\"}。" +
                "facts使用FACT，逐项描述两至四个关键事实并说明期间口径；analysis使用INFERENCE，以两至三个完整段落解释数据关系、" +
                "经营含义及条件，每段约一百五十至三百中文字符，证据充分时写深，避免堆砌术语或重复事实。" +
                "counterEvidence使用INFERENCE，至少一条替代解释或能推翻判断的条件；watchpoints使用WATCHPOINT，至少一条可执行的验证清单，" +
                "说明要看哪一科目或附注、哪种方向支持或削弱判断。所有数组最多八条。" +
                "FACT置信度不超过数据质量上限；INFERENCE原因解释最高MEDIUM，不能将已核查数值等同于已证实原因。" +
                "所有公司判断必须引用已有证据id；refs不能为空且来自本章或关联三表。无本章材料时assessment=INSUFFICIENT_EVIDENCE、" +
                "confidence=LOW，summary解释缺什么材料、不能下什么结论，refs和四组Claim数组为空。不要凭公司名称补写业务或风险。" +
                "原文尚未接入，不能把自己的解释写成管理层解释，不能确认审计意见、客户、产品、销量或价格变化。" +
                "增长解释需核查基期正负与低基数，单期背离不直接等于财务造假或盈利恶化；合同负债增长不能保证未来收入。" +
                "所有原因、影响、替代解释均标INFERENCE，不使用必然、保证等绝对表达。" +
                "连续或逐季趋势必须引用至少三个相邻同口径时点的TREND，并核查时点是否连续；同比指标不能替代连续趋势。" +
                "比较某科目的升降，必须引用该科目对应的同比指标、趋势或两个同口径时点；只有本期比率不能说明它已经改善。" +
                "上年同季度与本年同季度间隔一年，不是相邻季度；不要把两个同比时点叫作相邻季度，不得据此声称环比变化。" +
                "不要用收入同比证明毛利率变化，refs要支持本条整句中每个比较。第一季度累计等于单季，优先使用CURRENT_YTD原始数值。" +
                "crossStatementInsights每条必须引用至少两个不同报表域的L_原始科目，不能只用两个指标冒充三表联动。" +
                "summary、analysis、counterEvidence、watchpoints优先用文字解释方向，不写阿拉伯数字、列举序号、具体年份或数值阈值，页面会通过refs展示精确数值。" +
                "facts和FACT摘要若写数字，只使用本条refs证据中原样数值或保留零至两位小数，注意实际引用必须支持该数字，" +
                "不要自算、换算万亿、造比率、预测值或阈值。facts中不写原因和未来影响。" +
                "limitations是材料限制字符串数组，不在无引用限制段落补写公司数据；disclaimer说明仅用于研究。" +
                "不要按字数凑内容；材料不足就解释边界，全文以证据覆盖和完整论证为准。";
    }

    private String message(Throwable error) {
        String value = error == null || error.getMessage() == null
                ? "未知错误" : error.getMessage().replace('\n', ' ').replace('\r', ' ').trim();
        return shorten(value, 500);
    }

    private String shorten(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String modelPayload(FinancialEvidencePacket packet) {
        return packet.getModelPayloadJson() == null || packet.getModelPayloadJson().trim().isEmpty()
                ? packet.getPayloadJson() : packet.getModelPayloadJson();
    }
}
