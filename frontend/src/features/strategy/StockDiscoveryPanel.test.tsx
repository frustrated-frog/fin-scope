import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { StockDiscoveryPanel } from './StockDiscoveryPanel';

test('presents the latest automatic selection without a manual refresh action', async () => {
  const onOpenResearch = vi.fn();
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path === '/api/quant/stock-discoveries/status') {
      return apiResponse({ status: 'SUCCEEDED', runId: 9, businessDate: '2026-08-14' });
    }
    return apiResponse({
      run: { id: 9, businessDate: '2026-08-14', status: 'SUCCEEDED', budget: 6000, qualityStatus: 'FRESH_PRIMARY', finalCount: 1 },
      report: {
        as_of_date: '2026-08-14', source_family: 'EASTMONEY', quality_status: 'FRESH_PRIMARY',
        retrieved_at: '2026-08-14T15:38:00', budget: 6000, duration_ms: 81233, warnings: [],
        funnel: { constituent_count: 108, admitted_count: 42, quantified_count: 42, deep_review_count: 15, final_count: 1 },
        sectors: [{ code: 'BK001', name: '人工智能', category: 'CONCEPT', source_rank: 1, change_pct: 2.4, main_net_inflow: 980000000 }],
        candidates: [{ code: '600001', market: 'SH', name: '样本股份', price: 12.34, lot_cost: 1239, admitted: true, rejection_reasons: [], sector_names: ['人工智能'], factors: { relative_momentum_20: .12, momentum_60: .2, trend_consistency: .65, liquidity: 21.4, volatility_20: .03, drawdown_60: -.09 }, lightweight_score: 0.72, lightweight_rank: 1 }],
        deep_evidence: [{ code: '600001', qualified: true, conclusion: 'ROBUST', calibrated_probability: 0.64, probability_lower_bound: 0.55, brier_skill_score: 0.12, locked_accuracy: 0.58, locked_log_loss: 0.63, risk_adjusted_return: 0.71, max_drawdown: -0.12, stability_score: 0.81, health_status: 'HEALTHY', deep_score: 0.78, final_rank: 1, evidence: ['锁定样本优于基准'], risks: ['概率不是确定收益'] }],
        final_candidates: [{ code: '600001', qualified: true, conclusion: 'ROBUST', calibrated_probability: 0.64, probability_lower_bound: 0.55, brier_skill_score: 0.12, locked_accuracy: 0.58, locked_log_loss: 0.63, risk_adjusted_return: 0.71, max_drawdown: -0.12, stability_score: 0.81, backtest_audit_status: 'PASS', backtest_entry_date_agreement_rate: 1, backtest_return_delta: .0002, health_status: 'HEALTHY', deep_score: 0.78, final_rank: 1, evidence: ['锁定样本优于基准'], risks: ['概率不是确定收益'] }]
      }
    });
  }));

  render(<StockDiscoveryPanel addToast={vi.fn()} setMessage={vi.fn()} onOpenResearch={onOpenResearch} />);

  expect(await screen.findByRole('heading', { name: /样本股份/ })).toBeInTheDocument();
  expect(screen.getByText('64.0%')).toBeInTheDocument();
  expect(screen.getByText('锁定样本优于基准')).toBeInTheDocument();
  expect(screen.getByText('双引擎一致')).toBeInTheDocument();
  expect(screen.getByRole('img', { name: /深度候选风险收益分布/ })).toBeInTheDocument();
  expect(screen.getByRole('table', { name: '最终候选因子对比' })).toBeInTheDocument();
  expect(screen.getByText('38.9% 保留')).toBeInTheDocument();
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
