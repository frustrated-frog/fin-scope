import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { KlineChart } from './KlineChart';
import { StockMarketInsights } from './StockMarketInsights';
import { compareStockBenchmark, summarizeStockHistory } from './stockMarketAnalysis';

function history() {
  return Array.from({ length: 270 }, (_, index) => {
    const date = new Date(Date.UTC(2025, 10, 1 + index)).toISOString().slice(0, 10);
    const close = 100 + index + (index === 269 ? 20 : 0);
    return { code: '600519', market: 'SH', tradeDate: date, open: close - 1, high: close + 1, low: close - 2, close, volume: index === 269 ? 300 : 100, adjustment: 'QFQ', isComplete: true };
  });
}

test('automatically presents all performance observations comparisons levels and dated events', () => {
  const bars = history();
  const summary = summarizeStockHistory(bars);
  const comparison = compareStockBenchmark(bars, bars.map((bar) => ({ ...bar, code: '000300', open: 99, high: 101, low: 98, close: 100 })));
  render(<StockMarketInsights summary={summary} comparison={comparison} history={{ stock: { code: '600519', market: 'SH', sourceCode: 'TENCENT_DAILY', qualityStatus: 'STALE_FALLBACK', asOf: '2026-07-28', bars, warnings: ['缓存来源'] }, benchmarkName: '沪深300', benchmarkCode: '000300.SH', warnings: [] }} />);
  expect(screen.getByRole('heading', { name: '多周期表现' })).toBeInTheDocument();
  expect(screen.getByText('近 252 日')).toBeInTheDocument();
  expect(screen.getByRole('img', { name: '近 252 日收盘走势' })).toBeInTheDocument();
  expect(screen.getByRole('img', { name: '年初至今收盘走势' })).toBeInTheDocument();
  expect(screen.getByText('3.00 倍')).toBeInTheDocument();
  expect(screen.getByRole('img', { name: '个股与沪深300累计涨跌幅对比' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '近期价格位置' })).toBeInTheDocument();
  expect(screen.queryByText('缓存来源')).not.toBeInTheDocument();
  expect(screen.queryByText(/来源 TENCENT_DAILY/)).not.toBeInTheDocument();
  expect(screen.queryByText(/质量 旧快照/)).not.toBeInTheDocument();
  expect(screen.queryByText(/均线穿越与历史价量变化为已发生的事实/)).not.toBeInTheDocument();
  expect(screen.getAllByText('收盘突破此前 20 日最高价').length).toBeGreaterThan(0);
});

test('chart marks existing factual events and automatic price references without user drawing', () => {
  const bars = history();
  const summary = summarizeStockHistory(bars);
  const { container } = render(<KlineChart bars={bars.slice(-120)} events={summary.events} levels={summary.levels} />);
  expect(container.querySelectorAll('.stock-kline-event').length).toBeGreaterThan(0);
  expect(container.querySelectorAll('.stock-kline-reference').length).toBeGreaterThan(0);
  expect(screen.getAllByRole('img').some((element) => element.getAttribute('aria-label')?.includes('收盘突破此前 20 日最高价'))).toBe(true);
});

test('missing histories are explicit and never render fabricated percentages or event claims', () => {
  const { container } = render(<StockMarketInsights summary={summarizeStockHistory([])} comparison={compareStockBenchmark([], [])} history={{ stock: { code: '600519', market: 'SH', bars: [], warnings: ['无前复权历史'] }, benchmarkName: '沪深300', benchmarkCode: '000300.SH', warnings: [] }} />);
  expect(screen.queryByText('无前复权历史')).not.toBeInTheDocument();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
  expect(screen.getByText('历史不足，暂不能判断完整窗口事件')).toBeInTheDocument();
  expect(container.textContent).not.toContain('NaN');
  expect(container.textContent).not.toContain('Infinity');
});
