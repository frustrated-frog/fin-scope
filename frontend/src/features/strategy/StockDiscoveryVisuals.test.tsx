import { render, screen, within } from '@testing-library/react';
import { expect, test } from 'vitest';
import {
  CandidateFactorMatrix,
  DiscoveryFunnel,
  RiskReturnMap
} from './StockDiscoveryVisuals';

const candidates = [
  { code: '600001', market: 'SH', name: '样本股份', price: 12.3, lot_cost: 1230,
    admitted: true, rejection_reasons: [], sector_names: ['人工智能'], factors: {
      relative_momentum_20: .12, momentum_60: .20, trend_consistency: .65,
      liquidity: 22.1, volatility_20: .025, drawdown_60: -.08
    } },
  { code: '000002', market: 'SZ', name: '观察股份', price: 9.2, lot_cost: 920,
    admitted: true, rejection_reasons: [], sector_names: ['机器人'], factors: {
      relative_momentum_20: .04, momentum_60: .08, trend_consistency: .52,
      liquidity: 20.4, volatility_20: .05, drawdown_60: -.18
    } }
];

const evidence = [
  { code: '600001', qualified: true, conclusion: 'ROBUST', calibrated_probability: .66,
    probability_lower_bound: .57, brier_skill_score: .12, locked_accuracy: .59,
    locked_log_loss: .61, risk_adjusted_return: 1.12, max_drawdown: -.08,
    stability_score: .8, health_status: 'HEALTHY', final_rank: 1, evidence: [], risks: [] },
  { code: '000002', qualified: false, conclusion: 'NO_CLEAR_ADVANTAGE', calibrated_probability: .58,
    probability_lower_bound: .48, brier_skill_score: -.01, locked_accuracy: .51,
    locked_log_loss: .7, risk_adjusted_return: .22, max_drawdown: -.18,
    stability_score: .3, health_status: 'DEGRADED', evidence: [], risks: [] }
];

test('turns funnel counts into stage retention without hiding zero stages', () => {
  render(<DiscoveryFunnel funnel={{ constituent_count: 108, admitted_count: 42,
    quantified_count: 42, deep_review_count: 15, final_count: 0 }} />);

  expect(screen.getByLabelText('股票发现筛选漏斗')).toBeInTheDocument();
  expect(screen.getByText('38.9% 保留')).toBeInTheDocument();
  expect(screen.getByText('0.0% 保留')).toBeInTheDocument();
  expect(screen.getByText('最终入选')).toBeInTheDocument();
});

test('plots every deep candidate and highlights the final shortlist', () => {
  const { container } = render(<RiskReturnMap evidence={evidence} candidates={candidates} finalCodes={new Set(['600001'])} />);

  const chart = screen.getByRole('img', { name: /深度候选风险收益分布/ });
  expect(chart).toBeInTheDocument();
  expect(chart).toHaveAttribute('viewBox', '0 0 600 430');
  expect(container.querySelectorAll('[data-axis="x"]')).toHaveLength(5);
  expect(container.querySelectorAll('[data-axis="y"]')).toHaveLength(5);
  expect(screen.getByText('0.0%')).toBeInTheDocument();
  const firstYTick = container.querySelector('[data-axis="y"] text');
  const yAxisLabel = container.querySelector('.risk-axis-label[transform]');
  expect(Number(firstYTick?.getAttribute('x')) - Number(yAxisLabel?.getAttribute('x'))).toBeGreaterThan(60);
  expect(container.querySelectorAll('circle')).toHaveLength(2);
  expect(screen.getByText('样本股份')).toBeInTheDocument();
  expect(screen.getByText('全部深度候选 2 只')).toBeInTheDocument();
  expect(screen.getByText('最终入选 1 只')).toBeInTheDocument();
});

test('compares final candidates using six explainable factor columns', () => {
  render(<CandidateFactorMatrix evidence={[evidence[0], { ...evidence[1], final_rank: 2 }]}
    candidates={candidates} />);

  expect(screen.getByRole('table', { name: '最终候选因子对比' })).toBeInTheDocument();
  expect(screen.getByText('相对动量')).toBeInTheDocument();
  expect(screen.getByText('60日回撤')).toBeInTheDocument();
  expect(screen.getAllByText('强').length).toBeGreaterThan(0);
  expect(screen.getAllByText('弱').length).toBeGreaterThan(0);
  expect(screen.getByText('+12.0%')).toBeInTheDocument();

  const leadingRow = screen.getByText('样本股份').closest('tr');
  expect(leadingRow).not.toBeNull();
  expect(within(leadingRow!).getAllByRole('cell')[5]).toHaveAttribute('data-level', '强');
});

test('does not turn missing historical factors into real zero values', () => {
  render(<CandidateFactorMatrix evidence={[evidence[0]]}
    candidates={[{ ...candidates[0], factors: undefined }]} />);

  expect(screen.getAllByText('无数据')).toHaveLength(6);
  expect(screen.getAllByText('—')).toHaveLength(6);
});

test('keeps visual explanations honest when no candidates are available', () => {
  render(<><RiskReturnMap evidence={[]} candidates={[]} finalCodes={new Set()} />
    <CandidateFactorMatrix evidence={[]} candidates={[]} /></>);

  expect(screen.getByText('本批没有可绘制的深度候选。')).toBeInTheDocument();
  expect(screen.getByText('没有最终候选，因此不生成因子对比。')).toBeInTheDocument();
});
