import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import {
  EquityDrawdownChart,
  FactorContributionChart,
  ParameterStabilityMap
} from './SingleStockQuantVisuals';

test('shows factor contribution around a zero axis with signed evidence', () => {
  render(<FactorContributionChart factors={[
    { code: 'MOMENTUM_20', name: '20 日动量', category: '趋势', formula: 'close / close20 - 1',
      window: '20D', currentValue: .08, historicalPercentile: .78, standardizedValue: 1.2,
      coefficient: .4, contribution: .48, direction: '支持上涨', economicMeaning: '趋势延续', boundary: '不代表因果' },
    { code: 'VOLATILITY_20', name: '20 日波动率', category: '风险', formula: 'std(return)',
      window: '20D', currentValue: .03, historicalPercentile: .82, standardizedValue: 1.1,
      coefficient: -.3, contribution: -.33, direction: '压低概率', economicMeaning: '波动较高', boundary: '极端事件例外' }
  ]} />);

  expect(screen.getByRole('img', { name: /因子贡献坐标轴/ })).toBeInTheDocument();
  expect(screen.getByText('零贡献轴')).toBeInTheDocument();
  expect(screen.getByText('+0.480')).toBeInTheDocument();
  expect(screen.getByText('-0.330')).toBeInTheDocument();
  expect(screen.getByText('历史分位 78.0%')).toBeInTheDocument();
});

test('links strategy and benchmark equity to the underwater drawdown', () => {
  render(<EquityDrawdownChart
    points={[
      { tradeDate: '2026-01-02', strategyNav: 1, benchmarkNav: 1, drawdown: 0, invested: false },
      { tradeDate: '2026-01-03', strategyNav: 1.08, benchmarkNav: 1.04, drawdown: 0, invested: true },
      { tradeDate: '2026-01-04', strategyNav: 1.02, benchmarkNav: 1.03, drawdown: -.0556, invested: true }
    ]}
    strategyReturn={.02}
    benchmarkReturn={.03}
    maxDrawdown={.0556}
    drawdownStart="2026-01-03"
    drawdownTrough="2026-01-04"
  />);

  expect(screen.getByRole('img', { name: /收益与水下回撤联动图/ })).toBeInTheDocument();
  expect(screen.getByText('策略净值')).toBeInTheDocument();
  expect(screen.getByText('同股买入并持有')).toBeInTheDocument();
  expect(screen.getByText('水下回撤')).toBeInTheDocument();
  expect(screen.getByText(/2026-01-03 → 2026-01-04/)).toBeInTheDocument();
});

test('renders sparse parameter evidence without inventing missing combinations', () => {
  render(<ParameterStabilityMap stability={{
    positiveExcessRatio: .67, worstExcessReturn: -.01, worstSharpeRatio: .2,
    scenarios: [
      { holdingDays: 3, threshold: .6, primary: false, annualizedReturn: .1, excessReturn: .02, sharpeRatio: .8, maxDrawdown: .1, tradeCount: 14 },
      { holdingDays: 5, threshold: .6, primary: true, annualizedReturn: .13, excessReturn: .04, sharpeRatio: 1.1, maxDrawdown: .09, tradeCount: 11 },
      { holdingDays: 5, threshold: .65, primary: false, annualizedReturn: .08, excessReturn: -.01, sharpeRatio: .4, maxDrawdown: .08, tradeCount: 8 }
    ]
  }} />);

  expect(screen.getByRole('img', { name: /参数稳定性面板/ })).toBeInTheDocument();
  expect(screen.getByText('主方案')).toBeInTheDocument();
  expect(screen.getByText('缺少组合不插值')).toBeInTheDocument();
  expect(screen.getByText('+4.0%')).toBeInTheDocument();
  expect(screen.getByText('-1.0%')).toBeInTheDocument();
});

