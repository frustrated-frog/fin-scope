package com.finscope.service.research;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.research.ResearchEnums;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LearningTaskService {
    private static final Set<String> VALID_STATUSES = new LinkedHashSet<String>(Arrays.asList(
            ResearchEnums.LEARNING_STATUS_TODO,
            ResearchEnums.LEARNING_STATUS_LEARNING,
            ResearchEnums.LEARNING_STATUS_REVIEWING,
            ResearchEnums.LEARNING_STATUS_DONE));

    private final LearningTaskRepository learningTaskRepository;
    private final EvidenceService evidenceService;

    public LearningTaskService(LearningTaskRepository learningTaskRepository,
                               EvidenceService evidenceService) {
        this.learningTaskRepository = learningTaskRepository;
        this.evidenceService = evidenceService;
    }

    public void generateIfAbsent(EventCluster event, Article article, boolean meaningfulUpdate) {
        if (!meaningfulUpdate || event == null || event.getId() == null) {
            return;
        }
        if (learningTaskRepository.countByEventId(event.getId()) > 0) {
            return;
        }
        List<EvidenceItem> evidenceItems = event.getId() == null
                ? Collections.<EvidenceItem>emptyList()
                : evidenceService.listByEventId(event.getId());
        for (LearningTask task : buildTasks(event, article, evidenceItems)) {
            learningTaskRepository.save(task);
        }
    }

    public List<LearningTask> list() {
        return learningTaskRepository.findAll();
    }

    public List<LearningTask> listByEventId(Long eventId) {
        return learningTaskRepository.findByEventId(eventId);
    }

    public LearningTask updateStatus(Long id, String status) {
        LearningTask existing = learningTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Learning task not found: " + id));
        String normalizedStatus = normalizeStatus(status);
        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported learning task status: " + status);
        }
        return learningTaskRepository.updateStatus(existing.getId(), normalizedStatus);
    }

    private List<LearningTask> buildTasks(EventCluster event, Article article, List<EvidenceItem> evidenceItems) {
        ResearchSignalSnapshot signals = ResearchSignalSnapshot.from(event, article, evidenceItems);
        String anchor = signals.anchorText();
        if (ResearchEnums.THEME_CHINA_MACRO.equals(event.getThemeCode())) {
            if (signals.policySignal()) {
                return Arrays.asList(
                        task(event, "MLF、逆回购和 LPR 各自影响什么，为什么" + anchor + "值得先看政策工具？",
                                "MLF,逆回购,LPR", ResearchEnums.LEARNING_DIFFICULTY_FOUNDATION,
                                "先分清政策工具，才能理解政策信号如何传导到融资成本。"),
                        task(event, anchor + "之后最该跟踪哪些流动性和信用指标？",
                                "DR007,社融,信贷投放", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                                "把政策工具和后续资金价格、信用扩张验证起来。"),
                        task(event, "政策利率变化如何传导到银行负债成本、LPR 和信用扩张？",
                                "政策利率,银行负债成本,信用扩张", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                                "建立政策工具到实体融资条件的完整传导链。"));
            }
            if (signals.inflationSignal()) {
                return Arrays.asList(
                        task(event, anchor + "为什么会直接改变市场对通胀与降息节奏的判断？",
                                "CPI,PCE,通胀预期", ResearchEnums.LEARNING_DIFFICULTY_FOUNDATION,
                                "先分清数据本身和政策反应之间的关系。"),
                        task(event, "通胀数据、实际利率和风险资产估值之间的传导链是什么？",
                                "实际利率,估值,风险偏好", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                                "把单次数据点放进资产定价框架。"));
            }
            return Arrays.asList(
                    task(event, "为什么" + anchor + "会影响利率预期和黄金定价？",
                            "美联储,利率预期,黄金", ResearchEnums.LEARNING_DIFFICULTY_FOUNDATION,
                            "先补清政策预期如何传导到资产价格。"),
                    task(event, anchor + "最值得跟踪的宏观指标有哪些？",
                            "核心PCE,就业,实际利率", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                            "把事件和后续观察指标绑定起来。"),
                    task(event, "实际利率变化如何通过预期差传导到黄金和成长资产？",
                            "实际利率,预期差,资产定价", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                            "建立可迁移的宏观传导框架。"));
        }
        if (ResearchEnums.THEME_COMPANY_IPO.equals(event.getThemeCode())) {
            if (signals.ipoSignal()) {
                return Arrays.asList(
                        task(event, anchor + "对应的招股书里最该先看哪些章节？",
                                "招股书,募集资金用途,风险披露", ResearchEnums.LEARNING_DIFFICULTY_FOUNDATION,
                                "IPO 事件要先建立招股书阅读路径。"),
                        task(event, "判断" + anchor + "时，交易所规则和估值锚分别是什么？",
                                "交易所规则,估值锚,可比公司", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                                "把监管流程和市场定价放在一起看。"));
            }
            if (signals.earningsSignal()) {
                return Arrays.asList(
                        task(event, anchor + "最需要拆开的收入驱动和利润质量指标是什么？",
                                "营收驱动,利润质量,经营现金流", ResearchEnums.LEARNING_DIFFICULTY_FOUNDATION,
                                "财报事件不能只看 headline，需要拆经营质量。"),
                        task(event, "指引变化、订单趋势和估值重定价之间怎么联动？",
                                "指引,订单趋势,估值重定价", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                                "形成从业绩到估值的分析路径。"));
            }
            return Arrays.asList(
                    task(event, anchor + "背后的商业模式和收入驱动是什么？",
                            "商业模式,营收驱动", ResearchEnums.LEARNING_DIFFICULTY_FOUNDATION,
                            "先理解公司事件的基本面根基。"),
                    task(event, "分析" + anchor + "时最该看的财务指标是什么？",
                            "毛利率,现金流,估值", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                            "把事件追踪映射到财务分析。"));
        }
        if (ResearchEnums.THEME_AI_STARTUP.equals(event.getThemeCode())) {
            if (signals.aiFundingSignal()) {
                return Arrays.asList(
                        task(event, anchor + "反映的是融资环境改善，还是单家公司竞争力提升？",
                                "融资环境,竞争力,估值", ResearchEnums.LEARNING_DIFFICULTY_FOUNDATION,
                                "区分资本市场情绪和公司基本面。"),
                        task(event, "AI 融资新闻里最需要核对的商业化指标是什么？",
                                "ARR,毛利率,客户结构", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                                "避免只看融资额，不看商业化质量。"));
            }
            if (signals.aiEcosystemSignal() || signals.aiProductSignal()) {
                return Arrays.asList(
                        task(event, anchor + "更像模型能力突破、产品分发变化，还是开发者生态扩张？",
                                "模型能力,产品分发,开发者生态", ResearchEnums.LEARNING_DIFFICULTY_FOUNDATION,
                                "把 AI 事件放进真正决定护城河的维度里。"),
                        task(event, "这个事件暴露了哪些工作流、工具链或开发者 adoption 信号？",
                                "工作流,工具链,adoption", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                                "帮助判断产品势能是不是能沉淀成生态位。"));
            }
            return Arrays.asList(
                    task(event, anchor + "对应的产品形态和商业化路径是什么？",
                            "产品形态,商业化", ResearchEnums.LEARNING_DIFFICULTY_FOUNDATION,
                            "把 AI 事件转成产品理解。"),
                    task(event, "这个 AI 事件更依赖模型能力、工作流还是开发者生态？",
                            "模型能力,工作流,开发者生态", ResearchEnums.LEARNING_DIFFICULTY_INTERMEDIATE,
                            "形成 AI 创业判断框架。"));
        }
        return Arrays.asList(
                task(event, anchor + "背后最关键的变量和验证指标是什么？",
                        "核心变量,验证指标", ResearchEnums.LEARNING_DIFFICULTY_FOUNDATION,
                        "避免只记新闻结论，不理解驱动变量。"));
    }

    private LearningTask task(EventCluster event, String question, String concepts, String difficulty, String whyNeeded) {
        LearningTask task = new LearningTask();
        task.setEventId(event.getId());
        task.setThemeCode(event.getThemeCode());
        task.setQuestion(question);
        task.setConcepts(concepts);
        task.setDifficulty(difficulty);
        task.setStatus(ResearchEnums.LEARNING_STATUS_TODO);
        task.setWhyNeeded(whyNeeded);
        return task;
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }
}
