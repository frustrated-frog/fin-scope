import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { StrategyCatalogPanel } from './StrategyCatalogPanel';

const datasets = [{ id: 3, name: 'A股真实研究集', market: 'A_SHARE', dataKind: 'REAL' as const, status: 'READY' }];
const candidates = [
  { id: 7, title: '价值（账面价值）因素', assetClass: 'EQUITY', sourceCommitSha: 'abc123456789', reportedSharpe: .526, reportedVolatility: .119, rebalanceCadence: '月度', implementationUrl: 'https://example.com/code', paperUrl: 'https://example.com/paper', compatibilityStatus: 'ADAPTABLE', adaptationNote: '使用披露时点 BP 形成 A 股版本', mappedFactors: ['BP'], missingFactors: [], archived: false },
  { id: 8, title: '股票内部的ROA效应', assetClass: 'EQUITY', sourceCommitSha: 'abc123456789', reportedSharpe: .155, reportedVolatility: .087, rebalanceCadence: '月度', compatibilityStatus: 'NEEDS_FACTOR', adaptationNote: '需要新增 ROA 因子', mappedFactors: [], missingFactors: ['ROA'], archived: false },
  { id: 9, title: '空头利息效应--多空版本', assetClass: 'EQUITY', sourceCommitSha: 'abc123456789', rebalanceCadence: '月度', compatibilityStatus: 'UNSUPPORTED', adaptationNote: '当前引擎不支持多空', mappedFactors: [], missingFactors: [], archived: false }
];

test('filters candidates and hands an adaptable source into the existing draft flow', async () => {
  const requests: string[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input); requests.push(`${init?.method ?? 'GET'} ${path}`);
    if (path === '/api/quant/catalog/source') return apiResponse({ code: 'AWESOME_SYSTEMATIC_TRADING', repositoryUrl: 'https://github.com/paperswithbacktest/awesome-systematic-trading', branch: 'main', commitSha: 'abc123456789', status: 'READY', lastSyncedAt: '2026-08-01T09:00:00' });
    if (path === '/api/quant/catalog/candidates') return apiResponse(candidates);
    if (path === '/api/quant/catalog/candidates/7/drafts') return apiResponse({ id: 11, datasetId: 3, status: 'VALIDATED', validationIssues: [] });
    return apiResponse({});
  }));
  const onDraftCreated = vi.fn();
  const user = userEvent.setup();

  render(<StrategyCatalogPanel datasets={datasets} addToast={vi.fn()} onDraftCreated={onDraftCreated} />);

  expect(await screen.findByRole('button', { name: /价值（账面价值）因素/ })).toBeInTheDocument();
  expect(screen.getByText('abc12345')).toBeInTheDocument();
  await user.click(screen.getAllByRole('button', { name: /缺少因子/ })[0]);
  expect(screen.getAllByText('股票内部的ROA效应')).toHaveLength(2);
  expect(screen.queryByRole('button', { name: /价值（账面价值）因素/ })).not.toBeInTheDocument();
  await user.click(screen.getAllByRole('button', { name: /可适配/ })[0]);
  await user.click(screen.getByRole('button', { name: /价值（账面价值）因素/ }));
  expect(screen.getByText('来源记录，不代表本地验证结果')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '生成本地策略草案' }));

  await waitFor(() => expect(onDraftCreated).toHaveBeenCalledWith(expect.objectContaining({ id: 11 })));
  expect(requests).toContain('POST /api/quant/catalog/candidates/7/drafts');
});

test('keeps unsupported candidates visible but prevents draft generation', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    if (String(input) === '/api/quant/catalog/source') return apiResponse({ code: 'AWESOME_SYSTEMATIC_TRADING', commitSha: 'abc123456789', status: 'READY', lastSyncedAt: '2026-08-01T09:00:00' });
    return apiResponse(candidates);
  }));
  const user = userEvent.setup();
  render(<StrategyCatalogPanel datasets={datasets} addToast={vi.fn()} onDraftCreated={vi.fn()} />);

  await user.click((await screen.findAllByRole('button', { name: /暂不支持/ }))[0]);

  expect(screen.getByRole('button', { name: '当前引擎不可生成' })).toBeDisabled();
  expect(screen.getByText('当前引擎不支持多空')).toBeInTheDocument();
});
