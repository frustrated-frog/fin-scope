import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { KlineChart } from './KlineChart';
import { WatchlistKlineDrawer } from './WatchlistKlineDrawer';

vi.mock('../../shared/api/client', () => ({
  api: vi.fn()
}));

const bars = [
  { code: '600519', market: 'SH', tradeDate: '2026-07-30', open: 1500, high: 1520, low: 1490, close: 1510, volume: 50000 },
  { code: '600519', market: 'SH', tradeDate: '2026-07-31', open: 1510, high: 1530, low: 1505, close: 1528, volume: 60000 }
];

describe('KlineChart', () => {
  test('renders one crisp candle and volume column per bar', () => {
    const { container } = render(<KlineChart bars={bars} />);

    expect(screen.getByRole('img', { name: '最近 2 个交易日日 K 线' })).toBeInTheDocument();
    expect(screen.getByRole('img')).toHaveAttribute('viewBox', '0 0 1024 500');
    expect(container.querySelectorAll('.watchlist-kline-candle')).toHaveLength(2);
    expect(container.querySelectorAll('.watchlist-kline-candle rect')).toHaveLength(2);
    expect(container.querySelectorAll('.watchlist-kline-volume')).toHaveLength(2);
    // 两根均收涨，对应 watchlist-up（红涨）
    expect(container.querySelectorAll('.watchlist-kline-candle.watchlist-up')).toHaveLength(2);
    expect(container.querySelectorAll('.watchlist-kline-candle.watchlist-down')).toHaveLength(0);
  });

  test('narrows candle bodies when a dense range is displayed', () => {
    const denseBars = Array.from({ length: 120 }, (_, index) => ({
      ...bars[index % bars.length],
      tradeDate: `2026-07-${String((index % 28) + 1).padStart(2, '0')}`
    }));
    const { container } = render(<KlineChart bars={denseBars} />);

    expect(Number(container.querySelector('.watchlist-kline-candle rect')?.getAttribute('width'))).toBeLessThan(6);
  });

  test('shows empty state when no bars are given', () => {
    render(<KlineChart bars={[]} />);
    expect(screen.getByText('暂无日线数据')).toBeInTheDocument();
  });
});

describe('WatchlistKlineDrawer', () => {
  beforeEach(() => {
    vi.mocked(api).mockReset();
  });

  test('loads daily bars on open and shows latest summary', async () => {
    vi.mocked(api).mockResolvedValue(bars as never);

    render(<WatchlistKlineDrawer item={{ code: '600519', name: '贵州茅台' }} onClose={vi.fn()} />);

    expect(await screen.findByText('贵州茅台')).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith('/api/watchlist/600519/daily-bars?limit=120');
    expect(await screen.findByText('MARKET VIEW · DAILY')).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: '贵州茅台 行情图表' })).toBeInTheDocument();
    expect(screen.getByText('2026-07-31')).toBeInTheDocument();
  });

  test('surfaces load errors and closes on Escape', async () => {
    const onClose = vi.fn();
    vi.mocked(api).mockRejectedValue(new Error('行情数据暂不可用，请稍后重试') as never);

    render(<WatchlistKlineDrawer item={{ code: '600519', name: '贵州茅台' }} onClose={onClose} />);

    expect(await screen.findByText('日线加载失败')).toBeInTheDocument();
    expect(screen.getByText('行情数据暂不可用，请稍后重试')).toBeInTheDocument();

    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  test('positions the desktop overlay inside the workspace below the topbar', () => {
    vi.mocked(api).mockResolvedValue(bars as never);
    const rect = (values: Partial<DOMRect>): DOMRect => ({
      x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0,
      toJSON: () => ({}), ...values
    });
    const bounds = vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
      if (this.classList.contains('workspace')) return rect({ left: 224, right: 1440, width: 1216 });
      if (this.classList.contains('topbar')) return rect({ top: 0, bottom: 80, height: 80 });
      return rect({});
    });

    render(
      <main className="workspace">
        <header className="topbar" />
        <WatchlistKlineDrawer item={{ code: '600519', name: '贵州茅台' }} onClose={vi.fn()} />
      </main>
    );

    const backdrop = document.querySelector<HTMLElement>('.watchlist-kline-backdrop');
    expect(backdrop?.style.getPropertyValue('--watchlist-kline-left')).toBe('224px');
    expect(backdrop?.style.getPropertyValue('--watchlist-kline-top')).toBe('80px');
    bounds.mockRestore();
  });
});
