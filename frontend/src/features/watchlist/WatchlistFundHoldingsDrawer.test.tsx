import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { WatchlistFundHoldingsDrawer } from './WatchlistFundHoldingsDrawer';

vi.mock('../../shared/api/client', () => ({
  api: vi.fn()
}));

const detail = {
  fundCode: '021894',
  fundName: '易方达半导体设备ETF联接C',
  disclosureDate: '2026-06-30',
  retrievedAt: '2026-08-10T14:30:00',
  quoteAsOf: '2026-08-10T14:29:58',
  quoteRetrievedAt: '2026-08-10T14:30:00',
  quoteSource: 'TENCENT_STOCK',
  quoteQualityStatus: 'PARTIAL_FRESH',
  quoteWarning: '部分行情不可用',
  refreshId: 'refresh-1',
  topHoldingsWeightPct: 12,
  estimatedContributionPct: 0.12,
  estimatedHoldingCount: 1,
  totalHoldingCount: 2,
  lookThrough: false,
  note: '该基金为 ETF 联接基金；当前仅展示基金直接披露的股票持仓，未穿透目标 ETF。',
  holdings: [
    {
      rank: 1,
      stockCode: '688012',
      stockName: '中微公司',
      weightPct: 8,
      sharesTenThousand: 4.2,
      marketValueTenThousand: 1967.7,
      latestPrice: 468.5,
      changePct: 2,
      estimatedContributionPct: 0.16,
      quoteValid: true,
      quoteTime: '2026-08-10T14:29:58',
      qualityStatus: 'FRESH_PRIMARY'
    },
    {
      rank: 2,
      stockCode: '688120',
      stockName: '华海清科',
      weightPct: 4,
      quoteValid: false,
      qualityStatus: 'STALE_FALLBACK',
      quoteNote: '旧行情不参与估算'
    }
  ]
};

describe('WatchlistFundHoldingsDrawer', () => {
  beforeEach(() => {
    vi.mocked(api).mockReset();
  });

  test('loads fresh fund holdings and presents disclosure coverage', async () => {
    vi.mocked(api).mockResolvedValue(detail as never);

    render(<WatchlistFundHoldingsDrawer
      item={{ code: '021894', name: '易方达半导体设备ETF联接C' }}
      onClose={vi.fn()}
    />);

    expect(api).toHaveBeenCalledWith('/api/watchlist/021894/fund-holdings?refresh=true');
    expect(await screen.findByRole('dialog', { name: '易方达半导体设备ETF联接C 持仓透视' })).toBeInTheDocument();
    expect(screen.getByText('最近披露 2026-06-30')).toBeInTheDocument();
    expect(screen.getByText('中微公司')).toBeInTheDocument();
    expect(screen.getByText('+0.160 个百分点')).toBeInTheDocument();
    expect(screen.getByText('1 / 2')).toBeInTheDocument();
    expect(screen.getByText(/未穿透目标 ETF/)).toBeInTheDocument();
    expect(screen.getByText('旧行情不参与估算')).toBeInTheDocument();
  });

  test('refreshes the whole aggregate instead of keeping browser quote cache', async () => {
    vi.mocked(api).mockResolvedValue(detail as never);
    render(<WatchlistFundHoldingsDrawer item={{ code: '021894' }} onClose={vi.fn()} />);

    await screen.findByText('中微公司');
    await userEvent.click(screen.getByRole('button', { name: '刷新基金持仓和股票行情' }));

    expect(api).toHaveBeenCalledTimes(2);
    expect(api).toHaveBeenLastCalledWith('/api/watchlist/021894/fund-holdings?refresh=true');
  });

  test('shows an honest empty state when the fund has no published holdings yet', async () => {
    vi.mocked(api).mockResolvedValue({
      ...detail,
      fundCode: '024195',
      fundName: '新成立基金',
      disclosureDate: undefined,
      quoteAsOf: undefined,
      topHoldingsWeightPct: 0,
      estimatedContributionPct: undefined,
      estimatedHoldingCount: 0,
      totalHoldingCount: 0,
      note: '该基金尚无公开股票持仓披露。',
      holdings: []
    } as never);

    render(<WatchlistFundHoldingsDrawer
      item={{ code: '024195', name: '新成立基金' }}
      onClose={vi.fn()}
    />);

    expect(await screen.findByText('尚无持仓披露')).toBeInTheDocument();
    expect(screen.getByText('尚无公开持仓')).toBeInTheDocument();
    expect(screen.queryByText(/最近披露 undefined/)).not.toBeInTheDocument();
  });

  test('keeps the dialog usable after failure and retries explicitly', async () => {
    vi.mocked(api)
      .mockRejectedValueOnce(new Error('基金持仓数据暂不可用') as never)
      .mockResolvedValueOnce(detail as never);
    render(<WatchlistFundHoldingsDrawer item={{ code: '021894' }} onClose={vi.fn()} />);

    expect(await screen.findByText('持仓详情加载失败')).toBeInTheDocument();
    expect(screen.getByText('基金持仓数据暂不可用')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '重新加载基金持仓' }));

    expect(await screen.findByText('中微公司')).toBeInTheDocument();
    expect(api).toHaveBeenCalledTimes(2);
  });

  test('closes on Escape and locks scrolling only while mounted', async () => {
    const onClose = vi.fn();
    vi.mocked(api).mockResolvedValue(detail as never);
    const { unmount } = render(
      <WatchlistFundHoldingsDrawer item={{ code: '021894' }} onClose={onClose} />
    );

    expect(document.documentElement).toHaveClass('watchlist-kline-open');
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
    unmount();
    expect(document.documentElement).not.toHaveClass('watchlist-kline-open');
  });
});
