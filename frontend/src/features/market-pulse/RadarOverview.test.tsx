import { fireEvent, render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest';
import { RadarOverview } from './RadarOverview';
import type { MarketPulseWorkspace } from './marketPulseTypes';

const workspace: MarketPulseWorkspace = {
  businessDate: '2026-09-30', qualityStatus: 'PARTIAL',
  regime: { confidenceScore: 84, explanation: '趋势与流动性尚未形成同向突破' },
  breadth: { advanceRatio: .46, totalAmount: 1.45e12, medianChangePct: -.09, advanceCount: 2566, declineCount: 2824,
    trendBreadth: { ma20Ratio: .55, ma60Ratio: .61 }, volumePressure: { advanceAmountRatio: .43 } },
};
const props = { workspace, stage: '震荡轮动', dimensions: [{ name: '趋势', value: '下行' }], dates: ['2026-09-30', '2026-09-29'], refreshing: false, onLoad: vi.fn(), onRefresh: vi.fn() };

test('draws actual participation ratios with an accessible text equivalent', () => {
  const { container } = render(<RadarOverview {...props} />);
  expect(screen.getByRole('heading', { name: '震荡轮动' })).toBeInTheDocument();
  const chart = screen.getByRole('img', { name: /市场参与度雷达/ });
  expect(chart).toHaveAccessibleDescription('上涨家数 46%；MA20 上方 55%；上涨成交 43%；MA60 上方 61%。四轴比例为0至100%，缺失指标不绘制。');
  expect(container.querySelectorAll('.mpr-point')).toHaveLength(4);
  expect(container.querySelector('.mpr-shape')).toHaveAttribute('points', '180,84 235,130 180,173 119,130');
});

test('does not draw missing or invalid ratios as zero-valued vertices', () => {
  const { container } = render(<RadarOverview {...props} workspace={{ ...workspace, regime: {}, breadth: { advanceRatio: .46, trendBreadth: { ma20Ratio: NaN, ma60Ratio: 1.5 } } }} />);
  expect(container.querySelector('.mpr-shape')).not.toBeInTheDocument();
  expect(container.querySelectorAll('.mpr-point')).toHaveLength(1);
  expect(screen.getByRole('img', { name: /市场参与度雷达/ })).toHaveAccessibleDescription(/MA20 上方 —/);
  expect(container.textContent).not.toContain('NaN');
});

test('preserves the date selector and refresh action with a pending state', () => {
  const onLoad = vi.fn();
  const onRefresh = vi.fn();
  const { rerender } = render(<RadarOverview {...props} onLoad={onLoad} onRefresh={onRefresh} />);
  fireEvent.change(screen.getByLabelText('历史截面'), { target: { value: '2026-09-29' } });
  expect(onLoad).toHaveBeenCalledWith('2026-09-29');
  fireEvent.click(screen.getByRole('button', { name: '立即补刷新' }));
  expect(onRefresh).toHaveBeenCalledTimes(1);
  rerender(<RadarOverview {...props} refreshing onRefresh={onRefresh} />);
  expect(screen.getByRole('button', { name: '立即补刷新' })).toBeDisabled();
  expect(screen.getByText('正在更新')).toBeInTheDocument();
});
