package com.finscope.service.quant.overnight;

import com.finscope.common.enums.overnight.OvernightMode;
import com.finscope.domain.quant.overnight.OvernightCapturePlan;
import com.finscope.domain.quant.overnight.OvernightCaptureState;
import com.finscope.domain.quant.overnight.OvernightResearchInput;
import com.finscope.domain.quant.overnight.OvernightResearchReport;
import com.finscope.domain.quant.overnight.OvernightValidationSummary;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.rpc.quant.PythonOvernightClient;
import com.finscope.service.strategy.holding.StockAccountService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

@Service
public class OvernightResearchService {
    @Resource
    private PythonOvernightClient client;
    @Resource
    private StockAccountService accounts;

    public OvernightResearchReport generate(OvernightResearchInput input) {
        if (input == null || input.getInstrumentCode() == null || input.getSignalDate() == null
                || input.getMode() == null || !Double.isFinite(input.getCostBps())
                || input.getCostBps() < 0 || input.getCostBps() > 200) {
            throw new IllegalArgumentException("请填写股票、日期、研究场景及 0–200 基点的成本假设");
        }
        String code = input.getInstrumentCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (code.matches("\\d{6}")) {
            code += code.startsWith("6") ? ".SH" : ".SZ";
        }
        if (!code.matches("(?:(?:600|601|603|605)\\d{3}\\.SH|(?:000|001|002|003|300|301)\\d{3}\\.SZ)")) {
            throw new IllegalArgumentException("当前隔夜研究仅支持账户范围内的沪深 A 股代码");
        }
        input.setInstrumentCode(code);
        input.setCostBasis(null);
        input.setQuantity(null);
        input.setPositionOpenedOn(null);
        if (input.getMode() == OvernightMode.AFTER_CLOSE_HOLDING) {
            input.setCutoff("15:00");
            attachPosition(input);
        } else if (!"14:30".equals(input.getCutoff()) && !"14:45".equals(input.getCutoff()) && !"14:50".equals(input.getCutoff())) {
            throw new IllegalArgumentException("尾盘决策时点请选择 14:30、14:45 或 14:50");
        }
        return client.generate(input);
    }

    public List<OvernightResearchReport> history(boolean settle) {
        return client.history(settle);
    }

    public OvernightCaptureState captureState() {
        return client.captureState();
    }

    public OvernightValidationSummary validation() {
        return client.validation();
    }

    public OvernightCapturePlan configureCapture(OvernightCapturePlan plan) {
        if (plan == null || plan.getInstrumentCodes() == null || plan.getInstrumentCodes().size() > 10
                || !Double.isFinite(plan.getCostBps()) || plan.getCostBps() < 0 || plan.getCostBps() > 200) {
            throw new IllegalArgumentException("观察名单最多 10 只，费用假设需为 0–200 基点");
        }
        List<String> normalized = new ArrayList<>();
        for (String value : plan.getInstrumentCodes()) {
            String code = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
            if (code.matches("\\d{6}")) {
                code += code.startsWith("6") ? ".SH" : ".SZ";
            }
            if (!code.matches("(?:(?:600|601|603|605)\\d{3}\\.SH|(?:000|001|002|003|300|301)\\d{3}\\.SZ)")) {
                throw new IllegalArgumentException("观察名单中存在不支持的沪深股票代码");
            }
            if (!normalized.contains(code)) {
                normalized.add(code);
            }
        }
        if (plan.isEnabled() && normalized.isEmpty()) {
            throw new IllegalArgumentException("启用自动留档前请设置观察名单");
        }
        plan.setInstrumentCodes(normalized);
        return client.configureCapture(plan);
    }

    private void attachPosition(OvernightResearchInput input) {
        for (StockPosition position : accounts.snapshot().getPositions()) {
            if (input.getInstrumentCode().equals(position.getInstrumentCode())
                    && position.getQuantity().signum() > 0 && position.getAverageCost().signum() > 0) {
                if (position.getOpenedOn() == null || position.getOpenedOn().isAfter(input.getSignalDate())) {
                    throw new IllegalArgumentException("所选日期早于建仓日，不能生成持仓研判");
                }
                input.setCostBasis(position.getAverageCost().doubleValue());
                input.setQuantity(position.getQuantity().doubleValue());
                input.setPositionOpenedOn(position.getOpenedOn());
                return;
            }
        }
        throw new IllegalArgumentException("盘后持仓研判需要账本中的有效持仓，请先在真实持仓中记录交易");
    }
}
