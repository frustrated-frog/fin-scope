package com.finscope.service.factorresearch;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorLifecycleStatus;
import com.finscope.domain.factorresearch.ResearchFactorDefinition;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned, human-readable research definitions. A catalog entry documents a
 * hypothesis and its limits; it is not evidence that the factor is validated.
 */
@Service
public class ResearchFactorCatalog {
    private final Map<FactorIdentity, ResearchFactorDefinition> definitions;

    public ResearchFactorCatalog() {
        ResearchFactorDefinition mainFlowShare = ResearchFactorDefinition.builder()
                .identity(CapitalFlowFactorProvider.MAIN_FLOW_SHARE)
                .name("主力流入强度")
                .category("资金行为")
                .frequency("DAILY")
                .expectedDirection("POSITIVE_HYPOTHESIS")
                .plainMeaning("主力净流入占当日成交额的比例")
                .hypothesis("在信息可得时间一致的前提下，较高的主力净流入强度可能对应更强的短期需求压力，但该关系必须经样本外检验后才能使用")
                .economicRationale("成交额归一化降低绝对资金规模和股票体量的机械影响，使同一交易日不同标的之间具有初步可比性")
                .interpretationBoundary("数据供应商的“主力”是按成交单特征划分的统计口径，不代表已识别真实交易主体；单日资金流噪声较高，可能受涨跌停、成交活跃度和行情状态影响；该因子当前仅为探索性研究假设，不证明因果关系，不构成投资建议")
                .requiredFields(Arrays.asList("datasetId", "tradeDate", "instrumentCode",
                        "availableAt", "mainNetInflow", "amount", "qualityStatus"))
                .availableAtRule("只使用已冻结数据；availableAt 严格等于源快照 retrievedAt，且不得晚于策略信号对应的下一交易日开盘时间")
                .missingPolicy("任一必需字段缺失、amount 非正、质量非 COMPLETE 或数据尚不可见时返回 MISSING_INPUT，不做零值填充")
                .calculationKey("mainNetInflow / amount，保留 10 位小数并采用 HALF_UP")
                .calculationVersion("main-flow-share-v1")
                .sourceType("FROZEN_CAPITAL_FLOW")
                .sourceRef("market_capital_flow_snapshot.DAY_1 -> quant_capital_flow_daily")
                .evaluationPolicyCode("CROSS_SECTIONAL_FORWARD_RETURN")
                .evaluationPolicyVersion("1.0.0")
                .status(FactorLifecycleStatus.EXPLORATORY)
                .build();
        Map<FactorIdentity, ResearchFactorDefinition> values =
                new LinkedHashMap<FactorIdentity, ResearchFactorDefinition>();
        values.put(mainFlowShare.getIdentity(), mainFlowShare);
        this.definitions = Collections.unmodifiableMap(values);
    }

    public List<ResearchFactorDefinition> list() {
        return Collections.unmodifiableList(
                new java.util.ArrayList<ResearchFactorDefinition>(definitions.values()));
    }

    public ResearchFactorDefinition get(String namespace, String code, String version) {
        final FactorIdentity identity;
        try {
            identity = new FactorIdentity(namespace, code, version);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "因子命名空间、编码和版本不能为空");
        }
        ResearchFactorDefinition value = definitions.get(identity);
        if (value == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "研究因子版本不存在：" + identity);
        }
        return value;
    }
}
