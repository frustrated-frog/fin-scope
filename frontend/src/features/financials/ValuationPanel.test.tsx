import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { ValuationPanel } from './ValuationPanel';
import { StockValuationView } from './financialTypes';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

afterEach(() => vi.mocked(api).mockReset());

test('shows accumulating history honestly and refreshes the provider snapshot', async () => {
  vi.mocked(api).mockResolvedValueOnce(emptyView()).mockResolvedValueOnce(readyView());

  render(<ValuationPanel instrumentId={7} />);

  expect(await screen.findByText('尚未积累估值快照')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '刷新估值数据' }));

  expect((await screen.findAllByText('21.30')).length).toBeGreaterThan(0);
  expect(screen.getByText(/历史分位积累中/)).toBeInTheDocument();
  expect(screen.getByText('现金分红')).toBeInTheDocument();
  await waitFor(() => expect(api).toHaveBeenLastCalledWith(
    '/api/financials/instruments/7/valuation/refresh', { method: 'POST' }
  ));
});

function emptyView(): StockValuationView {
  return {
    instrument: { id: 7, code: '600519', name: '贵州茅台', type: 'STOCK', market: 'SH' },
    metrics: [], history: [], corporateActions: [], warnings: ['尚未积累估值快照，请先刷新数据']
  };
}

function readyView(): StockValuationView {
  return {
    ...emptyView(),
    latest: {
      instrumentId: 7, observedDate: '2026-08-29', observedAt: '2026-08-29T02:29:58Z',
      peTtm: '21.30', pbMrq: '7.10', sourceCode: 'FUYAO', qualityStatus: 'FRESH_PRIMARY'
    },
    metrics: [{ metricCode: 'PE_TTM', value: '21.30', sampleCount3y: 1,
      sampleCount5y: 1, historyStatus: 'ACCUMULATING' }],
    history: [{ instrumentId: 7, observedDate: '2026-08-29',
      observedAt: '2026-08-29T02:29:58Z', peTtm: '21.30', pbMrq: '7.10',
      sourceCode: 'FUYAO', qualityStatus: 'FRESH_PRIMARY' }],
    corporateActions: [{ instrumentId: 7, exDate: '2026-06-20',
      eventTypes: ['CASH_DIVIDEND'], dividendPerShare: '23.957', currency: 'CNY',
      sourceCode: 'FUYAO' }],
    warnings: ['历史分位需要至少 20 个有效日快照，当前仍在积累']
  };
}
