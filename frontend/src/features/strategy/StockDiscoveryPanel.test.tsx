import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { StockDiscoveryPanel } from './StockDiscoveryPanel';

const marketContext = {
  businessDate: '2026-08-21', transitionCode: 'REPAIR_EXPANSION' as const,
  transitionLabel: '修复正在扩散', riskPosture: 'BALANCED' as const,
  preferredSectors: ['创新药', '贵金属'], avoidSectors: ['半导体'],
  chasePolicy: 'PULLBACK_ONLY' as const, summary: '宽度、成交压力与家数动量同步改善'
};

test('presents the latest automatic selection without a manual refresh action', async () => {
  const onOpenResearch = vi.fn();
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path === '/api/quant/stock-discoveries/status') {
      return apiResponse({ status: 'SUCCEEDED', runId: 9, businessDate: '2026-08-14' });
    }
    if (path === '/api/quant/stock-discoveries/accuracy') {
      return apiResponse({
        schemaVersion: 'stock-discovery-evaluation-v1', asOfDate: '2026-08-20', horizonDays: 5,
        status: 'ACCUMULATING', conclusion: '真实样本尚少，继续积累，不提前宣称优势。',
        maturedRunCount: 1, maturedCandidateCount: 2, maturedFinalCount: 1, pendingCount: 12,
        probabilityQuality: { sampleCount: 1, brierScore: .21, brierSkillScore: .03, logLoss: .62, accuracy: 1, expectedCalibrationError: .14, baselineProbability: 1 },
        reliabilityBins: [
          { lowerBound: 0, upperBound: .2, count: 0 }, { lowerBound: .2, upperBound: .4, count: 0 },
          { lowerBound: .4, upperBound: .6, count: 0 }, { lowerBound: .6, upperBound: .8, count: 1, meanProbability: .64, observedUpRate: 1, calibrationError: .36 },
          { lowerBound: .8, upperBound: 1, count: 0 }
        ],
        selectionMetrics: [
          { limit: 1, maturedRunCount: 1, sampleCount: 1, hitRate: 1, averageNetReturn: .04, admittedPoolAverageReturn: .01, averageExcessVsAdmittedPool: .03 },
          { limit: 3, maturedRunCount: 1, sampleCount: 1, hitRate: 1, averageNetReturn: .04, admittedPoolAverageReturn: .01, averageExcessVsAdmittedPool: .03 },
          { limit: 5, maturedRunCount: 1, sampleCount: 1, hitRate: 1, averageNetReturn: .04, admittedPoolAverageReturn: .01, averageExcessVsAdmittedPool: .03 }
        ],
        windows: [30, 90, 180].map(windowDays => ({ windowDays, maturedRunCount: 1, probabilitySampleCount: 1, finalCount: 1, finalHitRate: 1, finalAverageNetReturn: .04, brierSkillScore: .03 })),
        sectorPerformance: [],
        rankingChallenger: { status: 'SHADOW_EVALUATING', sampleCount: 42, dateGroupCount: 9,
          developmentDateCount: 5, validationDateCount: 2, lockedDateCount: 2,
          pairCount: 116, rankIc: .18, pairwiseAccuracy: .59,
          topKAverageReturn: .034, topKExcessReturn: .012,
          method: 'DATE_GROUPED_PAIRWISE_LOGISTIC', conclusion: '排序挑战者继续影子验收。' },
        modelRace: { status: 'EVIDENCE_ACCUMULATING', sampleCount: 1, minimumPromotionSamples: 30, conclusion: '继续影子运行。', candidates: [] },
        recentOutcomes: [{ runId: 9, instrumentCode: '600001.SH', asOfDate: '2026-08-14', finalRank: 1, calibratedProbability: .64, actualNetReturn: .04, actualDirection: 'UP', sectorNames: ['人工智能'] }], warnings: []
      });
    }
    return apiResponse({
      run: { id: 9, businessDate: '2026-08-14', status: 'SUCCEEDED', budget: 6000, qualityStatus: 'FRESH_PRIMARY', finalCount: 1 },
      report: {
        as_of_date: '2026-08-14', source_family: 'TONGHUASHUN', quality_status: 'FRESH_PRIMARY',
        constituent_source_families: ['TONGHUASHUN'], constituent_quality_status: 'COMPLETE',
        retrieved_at: '2026-08-14T15:38:00', budget: 6000, duration_ms: 81233, warnings: [],
        funnel: { raw_constituent_count: 139, scope_excluded_count: 31, star_market_excluded_count: 25, beijing_market_excluded_count: 6, unsupported_scope_excluded_count: 0, constituent_count: 108, admitted_count: 42, quantified_count: 42, deep_review_count: 15, final_count: 1 },
        sectors: [{ code: '881125', name: '人工智能', category: 'INDUSTRY', source_rank: 1, change_pct: 2.4, main_net_inflow: 980000000, expected_constituent_count: 52, resolved_constituent_count: 52, constituent_source_family: 'TONGHUASHUN', constituent_quality_status: 'COMPLETE', constituent_coverage: 1 }],
        candidates: [{ code: '600001', market: 'SH', name: '样本股份', price: 12.34, lot_cost: 1239, admitted: true, rejection_reasons: [], sector_names: ['人工智能'], factors: { relative_momentum_20: .12, momentum_60: .2, trend_consistency: .65, liquidity: 21.4, volatility_20: .03, drawdown_60: -.09 }, lightweight_score: 0.72, lightweight_rank: 1 }],
        deep_evidence: [{ code: '600001', qualified: true, conclusion: 'ROBUST', calibrated_probability: 0.64, probability_lower_bound: 0.55, brier_skill_score: 0.12, locked_accuracy: 0.58, locked_log_loss: 0.63, risk_adjusted_return: 0.71, max_drawdown: -0.12, stability_score: 0.81, health_status: 'HEALTHY', deep_score: 0.78, relative_rank: 1, research_tier: 'ACTIONABLE', evidence: ['锁定样本优于基准'], risks: ['概率不是确定收益'] }],
        relative_candidates: [{ code: '600001', qualified: true, conclusion: 'ROBUST', calibrated_probability: 0.64, probability_lower_bound: 0.55, brier_skill_score: 0.12, locked_accuracy: 0.58, locked_log_loss: 0.63, risk_adjusted_return: 0.71, max_drawdown: -0.12, stability_score: 0.81, backtest_audit_status: 'PASS', health_status: 'HEALTHY', relative_score: 0.78, relative_rank: 1, research_tier: 'ACTIONABLE', evidence: ['锁定样本优于基准'], risks: ['概率不是确定收益'] }],
        final_candidates: [{ code: '600001', qualified: true, conclusion: 'ROBUST', calibrated_probability: 0.64, probability_lower_bound: 0.55, brier_skill_score: 0.12, locked_accuracy: 0.58, locked_log_loss: 0.63, risk_adjusted_return: 0.71, max_drawdown: -0.12, stability_score: 0.81, backtest_audit_status: 'PASS', backtest_entry_date_agreement_rate: 1, backtest_return_delta: .0002, health_status: 'HEALTHY', deep_score: 0.78, final_rank: 1, evidence: ['锁定样本优于基准'], risks: ['概率不是确定收益'] }]
      }
    });
  }));

  render(<StockDiscoveryPanel addToast={vi.fn()} setMessage={vi.fn()} onOpenResearch={onOpenResearch}
    marketContext={marketContext} />);

  expect(await screen.findByRole('heading', { name: /样本股份/ })).toBeInTheDocument();
  expect(screen.getAllByText('64.0%').length).toBeGreaterThanOrEqual(1);
  expect(screen.getByText('锁定样本优于基准')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '相对优势 Top 5' })).toBeInTheDocument();
  expect(screen.getByText('严格可行动 1')).toBeInTheDocument();
  expect(screen.getByText('严格通过')).toBeInTheDocument();
  expect(screen.getByText('双引擎一致')).toBeInTheDocument();
  expect(screen.getByRole('img', { name: /深度候选风险收益分布/ })).toBeInTheDocument();
  expect(screen.getByRole('table', { name: '相对候选因子对比' })).toBeInTheDocument();
  expect(screen.getByText('38.9% 保留')).toBeInTheDocument();
  expect(screen.getByText('同花顺唯一热榜')).toBeInTheDocument();
  expect(screen.getByText('净流入降序')).toBeInTheDocument();
  expect(screen.getByText('同花顺成分')).toBeInTheDocument();
  expect(screen.getByText('权限范围剔除 31 只')).toBeInTheDocument();
  expect(screen.getByText('成分覆盖 52 / 52')).toBeInTheDocument();
  expect(screen.getByText('来自市场转折雷达')).toBeInTheDocument();
  expect(screen.getByText('修复正在扩散')).toBeInTheDocument();
  expect(screen.getByText('创新药 · 贵金属')).toBeInTheDocument();
  expect(screen.getByText('均衡试错')).toBeInTheDocument();
  expect(screen.getByText('只等回撤确认')).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: '真实预测验收台' })).toBeInTheDocument();
  expect(screen.getByRole('img', { name: /股票发现真实概率校准图/ })).toBeInTheDocument();
  expect(screen.getByText('真实样本尚少，继续积累，不提前宣称优势。')).toBeInTheDocument();
  expect(screen.getByText('PAIRWISE 排序挑战者')).toBeInTheDocument();
  expect(screen.getByText('Rank IC')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /刷新|运行|选股/ })).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '进入单股完整研究' }));
  expect(onOpenResearch).toHaveBeenCalledWith('600001');
});

test('shows delivered transport separately from a failed business calculation', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    if (String(input) === '/api/quant/stock-discoveries/status') {
      return apiResponse({
        status: 'FAILED', businessStatus: 'FAILED', deliveryStatus: 'DELIVERED',
        retryPending: true, errorMessage: '所有热门板块数据源不可用',
        nextScheduledAt: '2026-08-17T15:30:00+08:00'
      });
    }
    return apiResponse({ status: 'EMPTY' });
  }));

  render(<StockDiscoveryPanel addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('任务已送达，业务计算失败')).toBeInTheDocument();
  expect(screen.getByText('所有热门板块数据源不可用')).toBeInTheDocument();
  expect(screen.getByText(/系统会自动重试/)).toBeInTheDocument();
  expect(screen.getByText(/下次自动调度：2026-08-17T15:30:00\+08:00/)).toBeInTheDocument();
});
