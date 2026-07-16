import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, test, vi } from 'vitest';
import { ResearchDatasetWizard } from './ResearchDatasetWizard';

afterEach(() => vi.unstubAllGlobals());

test('creates a truthful real dataset container and freezes only after explicit dates', async () => {
  const user = userEvent.setup();
  const onChanged = vi.fn();
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path === '/api/quant/datasets') return new Response(JSON.stringify({
      id: 8, name: '资金行为研究集', market: 'A_SHARE', dataKind: 'REAL', datasetLevel: 'RESEARCH',
      fingerprintVersion: 'quant-dataset-v2', status: 'BUILDING', qualitySummary: '{"barCount":0}'
    }), { status: 201 });
    if (path.endsWith('/capital-flow-freeze')) return new Response(JSON.stringify({
      id: 8, name: '资金行为研究集', market: 'A_SHARE', dataKind: 'REAL', datasetLevel: 'RESEARCH',
      fingerprintVersion: 'quant-dataset-v2', status: 'BLOCKED', qualitySummary: '{"issueCodes":["missingCapitalFlow"]}'
    }));
    return new Response(JSON.stringify({}), { status: 200 });
  }));

  const { rerender } = render(<ResearchDatasetWizard dataset={undefined} suggestedIndex={1}
    onDatasetChanged={onChanged} addToast={vi.fn()} />);
  await user.click(screen.getByRole('button', { name: '建立真实研究集' }));
  expect(globalThis.fetch).toHaveBeenCalledWith('/api/quant/datasets', expect.objectContaining({ method: 'POST' }));
  const created = onChanged.mock.calls[0][0];
  rerender(<ResearchDatasetWizard dataset={created} suggestedIndex={1}
    onDatasetChanged={onChanged} addToast={vi.fn()} />);
  expect(screen.getByText('导入真实日线')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '冻结资金分区' })).toBeDisabled();
  await user.type(screen.getByLabelText('研究开始日'), '2026-01-01');
  await user.type(screen.getByLabelText('研究结束日'), '2026-06-30');
  await user.type(screen.getByLabelText('信息截止时间'), '2026-07-01T09:00');
  await user.click(screen.getByRole('button', { name: '冻结资金分区' }));
  const freezeCall = vi.mocked(globalThis.fetch).mock.calls.find(([path]) => String(path).endsWith('/capital-flow-freeze'));
  expect(JSON.parse(String(freezeCall?.[1]?.body))).toEqual({
    from: '2026-01-01', to: '2026-06-30', asOfTime: '2026-07-01T09:00'
  });
});

test('imports complete OHLC rows but rejects a current-snapshot universe', async () => {
  const user = userEvent.setup();
  const addToast = vi.fn();
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => new Response(JSON.stringify({
    id: 8, name: '真实研究集', market: 'A_SHARE', dataKind: 'REAL', status: 'BUILDING'
  }))));
  const dataset = { id: 8, name: '真实研究集', market: 'A_SHARE', dataKind: 'REAL' as const, status: 'BUILDING' };
  render(<ResearchDatasetWizard dataset={dataset} suggestedIndex={1} onDatasetChanged={vi.fn()} addToast={addToast} />);
  const bars = new File([JSON.stringify([{
    tradeDate: '2026-01-02', instrumentCode: '600519.SH', open: 10, high: 11, low: 9,
    close: 10.5, adjustedClose: 10.5, volume: 100, amount: 1000
  }])], 'bars.json', { type: 'application/json' });
  await user.upload(screen.getByLabelText('真实日线 JSON'), bars);
  await user.click(screen.getByRole('button', { name: '校验并导入日线' }));
  await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledWith('/api/quant/datasets/8/bars', expect.objectContaining({ method: 'POST' })));

  const currentUniverse = new File([JSON.stringify([{
    tradeDate: '2026-01-02', instrumentCode: '600519.SH', member: true, sourceKind: 'CURRENT_SNAPSHOT'
  }])], 'universe.json', { type: 'application/json' });
  await user.upload(screen.getByLabelText('点时股票池 JSON'), currentUniverse);
  await user.click(screen.getByRole('button', { name: '校验并导入股票池' }));
  await waitFor(() => expect(addToast).toHaveBeenCalledWith(expect.stringContaining('POINT_IN_TIME'), 'error'));
  expect(vi.mocked(globalThis.fetch).mock.calls.some(([path]) => String(path).endsWith('/universe'))).toBe(false);
});
