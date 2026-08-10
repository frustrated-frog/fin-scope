import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { KlineChart } from './KlineChart';
import { WatchlistKlineDrawer } from './WatchlistKlineDrawer';
import { clearWatchlistDailyBarCache } from './watchlistDailyBarCache';

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

  test('keeps the first and last candle bodies inside the price plot', () => {
    const { container } = render(<KlineChart bars={bars} />);
    const plot = container.querySelector<SVGRectElement>('.watchlist-kline-plot-bg')!;
    const candles = [...container.querySelectorAll<SVGRectElement>('.watchlist-kline-candle rect')];
    const plotLeft = Number(plot.getAttribute('x'));
    const plotRight = plotLeft + Number(plot.getAttribute('width'));
    const left = Number(candles[0].getAttribute('x'));
    const right = Number(candles[candles.length - 1].getAttribute('x'))
      + Number(candles[candles.length - 1].getAttribute('width'));

    expect(left).toBeGreaterThanOrEqual(plotLeft);
    expect(right).toBeLessThanOrEqual(plotRight);
  });

  test('labels the right axis as price and the lower panel as volume', () => {
    render(<KlineChart bars={bars} />);

    expect(screen.getByText('价格（元）')).toBeInTheDocument();
    expect(screen.getByText('成交量（手）')).toBeInTheDocument();
  });

  test('draws cached MA5 MA20 and MA60 trend layers', () => {
    const denseBars = Array.from({ length: 65 }, (_, index) => ({
      code: '600519', tradeDate: `2026-07-${String((index % 28) + 1).padStart(2, '0')}`,
      open: 100 + index, high: 102 + index, low: 99 + index, close: 101 + index, volume: 10_000
    }));
    const { container } = render(<KlineChart bars={denseBars} />);

    expect(container.querySelector('.watchlist-kline-ma-5')).toBeInTheDocument();
    expect(container.querySelector('.watchlist-kline-ma-20')).toBeInTheDocument();
    expect(container.querySelector('.watchlist-kline-ma-60')).toBeInTheDocument();
  });

  test('shows empty state when no bars are given', () => {
    render(<KlineChart bars={[]} />);
    expect(screen.getByText('暂无日线数据')).toBeInTheDocument();
  });
});

describe('WatchlistKlineDrawer', () => {
  beforeEach(() => {
    clearWatchlistDailyBarCache();
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
    expect(screen.getByRole('tab', { name: '行情走势' })).toHaveAttribute('aria-selected', 'true');
  });

  test('loads the evidence map only after opening the supply-chain tab', async () => {
    vi.mocked(api)
      .mockResolvedValueOnce(bars as never)
      .mockResolvedValueOnce({
        code: '600519', name: '贵州茅台',
        snapshot: {
          companyCode: '600519', companyName: '贵州茅台', summary: '连接原料种植与消费市场。', position: '白酒生产',
          schemaVersion: 'SUPPLY_CHAIN_V1', nodes: [], evidence: []
        },
        refreshRun: { id: 1, status: 'READY', stage: 'COMPLETED' }
      } as never);

    render(<WatchlistKlineDrawer item={{ code: '600519', name: '贵州茅台' }} onClose={vi.fn()} />);
    await screen.findByText('2026-07-31');
    expect(api).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole('tab', { name: '产业链' }));

    expect(await screen.findByText('连接原料种植与消费市场。')).toBeInTheDocument();
    expect(api).toHaveBeenNthCalledWith(2, '/api/stocks/600519/supply-chain');
    expect(screen.getByRole('tab', { name: '产业链' })).toHaveAttribute('aria-selected', 'true');
  });

  test('shows cached market activity and range statistics without another request', async () => {
    const history = Array.from({ length: 25 }, (_, index) => ({
      code: '600519', market: 'SH', tradeDate: `2026-07-${String(index + 1).padStart(2, '0')}`,
      open: 100 + index, high: 103 + index, low: 98 + index, close: 101 + index,
      volume: 50_000 + index, amount: 680_000_000, turnoverRate: 1.26
    }));
    vi.mocked(api).mockResolvedValue(history as never);

    render(<WatchlistKlineDrawer item={{ code: '600519', name: '贵州茅台' }} onClose={vi.fn()} />);

    expect(await screen.findByText('6.80 亿')).toBeInTheDocument();
    expect(screen.getByText('1.26%')).toBeInTheDocument();
    expect(screen.getByText('20 日涨跌')).toBeInTheDocument();
    expect(screen.getByText('区间高 / 低')).toBeInTheDocument();
    expect(document.querySelector('.watchlist-kline-content .watchlist-kline-meta')).toBeInTheDocument();
    expect(api).toHaveBeenCalledTimes(1);
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

  test('locks background scrolling only while the kline dialog is open', () => {
    vi.mocked(api).mockResolvedValue(bars as never);

    const { unmount } = render(<WatchlistKlineDrawer item={{ code: '600519', name: '贵州茅台' }} onClose={vi.fn()} />);

    expect(document.documentElement).toHaveClass('watchlist-kline-open');
    unmount();
    expect(document.documentElement).not.toHaveClass('watchlist-kline-open');
  });

  test('does not restart loading or focus the header when the parent supplies a new close callback', async () => {
    vi.mocked(api).mockResolvedValue(bars as never);
    const focus = vi.spyOn(HTMLButtonElement.prototype, 'focus').mockImplementation(() => undefined);
    const { rerender } = render(
      <WatchlistKlineDrawer item={{ code: '600519', name: '贵州茅台' }} onClose={vi.fn()} />
    );

    expect(await screen.findByText('2026-07-31')).toBeInTheDocument();
    expect(focus).toHaveBeenCalledTimes(1);

    rerender(<WatchlistKlineDrawer item={{ code: '600519', name: '贵州茅台' }} onClose={vi.fn()} />);

    expect(focus).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('正在加载日线…')).not.toBeInTheDocument();
    expect(api).toHaveBeenCalledTimes(1);
    focus.mockRestore();
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

  test('keeps a narrow overlay below the topbar while removing the sidebar offset', () => {
    vi.mocked(api).mockResolvedValue(bars as never);
    const originalWidth = window.innerWidth;
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 900 });
    const rect = (values: Partial<DOMRect>): DOMRect => ({
      x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0,
      toJSON: () => ({}), ...values
    });
    const bounds = vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
      if (this.classList.contains('workspace')) return rect({ left: 224, right: 900, width: 676 });
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
    expect(backdrop?.style.getPropertyValue('--watchlist-kline-left')).toBe('0px');
    expect(backdrop?.style.getPropertyValue('--watchlist-kline-top')).toBe('80px');
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalWidth });
    bounds.mockRestore();
  });
});
