import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { BacktestAuditPanel } from './BacktestAuditPanel';
import { ForecastBacktestAudit } from './quantTypes';

function audit(status: ForecastBacktestAudit['status']): ForecastBacktestAudit {
  return {
    status, mode: 'SHADOW', tradeCountAgreement: status === 'PASS',
    entryDateAgreementRate: status === 'PASS' ? 1 : .8,
    exitDateAgreementRate: status === 'PASS' ? 1 : .8,
    returnDelta: .002, maxDrawdownDelta: .001, sharpeDelta: .03,
    costDelta: .0001, durationMs: 18,
    primaryEngine: { engine: 'FIN_SCOPE', tradeCount: 5, totalReturn: .12,
      maxDrawdown: .08, sharpeRatio: .9, totalCost: .003 },
    shadowEngine: status === 'UNAVAILABLE' ? undefined : { engine: 'BACKTESTING_PY',
      tradeCount: status === 'PASS' ? 5 : 4, totalReturn: .118,
      maxDrawdown: .081, sharpeRatio: .87, totalCost: .0031 },
    mismatches: status === 'WARNING' ? [{ category: 'ENTRY_DATE', tradeIndex: 2,
      primaryValue: '2026-08-03', shadowValue: '2026-08-04',
      detail: '下一交易日开盘入场日期不一致' }] : [],
    limitations: ['影子验证不参与本期方向决策'],
  };
}

test('renders two independent ledgers and a passing verdict', () => {
  render(<BacktestAuditPanel audit={audit('PASS')} />);

  expect(screen.getByRole('heading', { name: '独立回测审计' })).toBeInTheDocument();
  expect(screen.getByText('两套账本一致')).toBeInTheDocument();
  expect(screen.getByText('FinScope 原生账本')).toBeInTheDocument();
  expect(screen.getByText('Backtesting.py 影子账本')).toBeInTheDocument();
  expect(screen.getAllByText('100.0%')).toHaveLength(2);
  expect(screen.getByText(/不参与本期方向决策/)).toBeInTheDocument();
});

test('renders actionable trade mismatch evidence', () => {
  render(<BacktestAuditPanel audit={audit('WARNING')} />);

  expect(screen.getByText('账本存在差异')).toBeInTheDocument();
  expect(screen.getByText(/第 2 笔 · 入场日期/)).toBeInTheDocument();
  expect(screen.getByText('下一交易日开盘入场日期不一致')).toBeInTheDocument();
});

test('renders a non-blocking unavailable state', () => {
  render(<BacktestAuditPanel audit={audit('UNAVAILABLE')} />);

  expect(screen.getByText('影子账本暂不可用')).toBeInTheDocument();
  expect(screen.getByText(/原生预测和回测结果仍然有效/)).toBeInTheDocument();
});
