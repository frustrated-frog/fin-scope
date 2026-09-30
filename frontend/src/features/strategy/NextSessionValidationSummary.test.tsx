import { fireEvent, render, screen } from '@testing-library/react';
import { test, expect, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { NextSessionValidationSummary } from './NextSessionValidationSummary';

test('uses server all-history count and explicit stock filter instead of the display window', async () => {
  const fetcher = vi.fn(async (_url: RequestInfo | URL) => apiResponse({ recordCount: 506, groups: [{ version: 'v2', loaded: 506,
    duplicates: 5, pending: 1, unavailable: 0, count: 500, days: 20, accuracy: .55, brier: .24, bins: [] }] }));
  vi.stubGlobal('fetch', fetcher);
  render(<NextSessionValidationSummary records={[]} code="605058.SH" />);
  expect(await screen.findByText(/全历史冻结账本共 506 条/)).toBeInTheDocument();
  expect(screen.getByText('500 / 20')).toBeInTheDocument();
  expect(fetcher.mock.calls[0][0]).toBe('/api/quant/next-session-predictions/validation?code=605058');
});

test('does not present a partial window as full-history when the endpoint fails', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('offline'); }));
  render(<NextSessionValidationSummary records={[]} />);
  expect(await screen.findByRole('alert')).toHaveTextContent('全历史验收读取失败');
});

test('switches model evidence without mixing versions', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse({ recordCount: 506, groups: [
    { version: 'v1', count: 500, days: 20, accuracy: .55, brier: .24, pending: 1, unavailable: 0, duplicates: 5, bins: [] },
    { version: 'v2', count: 4, days: 2, accuracy: .75, brier: .18, pending: 0, unavailable: 0, duplicates: 0, bins: [] },
  ] })));
  render(<NextSessionValidationSummary records={[]} />);
  expect(await screen.findByText('55.0%')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('模型版本'), { target: { value: 'v2' } });
  expect(screen.getByText('75.0%')).toBeInTheDocument();
  expect(screen.queryByText('55.0%')).not.toBeInTheDocument();
  expect(screen.getByText(/全历史冻结账本共 506 条/)).toBeInTheDocument();
});
