import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { ThsDesktopView } from './ThsDesktopView';

const snapshot = {
  status: 'PARTIAL', message: '已读取部分页面内容', source: 'THS_DESKTOP_AX',
  capturedAt: '2026-09-13T03:00:00Z', dataDate: '2026-09-11', stockCode: '605069', stockName: '正和生态',
  price: '11.50', changePct: '+10.05%', netBuy: null, reason: '日涨幅偏离值达7%的证券',
  fields: [], buySeats: ['某证券营业部 +1202.89'], sellSeats: [], warnings: ['净买入未获取。']
};

test('manual capture displays dated facts and passes a frozen research question', async () => {
  const fetch = vi.fn().mockResolvedValue(apiResponse(snapshot));
  vi.stubGlobal('fetch', fetch);
  const research = vi.fn();
  render(<ThsDesktopView onResearch={research} />);
  expect(fetch).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole('button', { name: '读取当前页面' }));
  expect(await screen.findByText('正和生态')).toBeInTheDocument();
  expect(screen.getByText('2026-09-11')).toBeInTheDocument();
  expect(screen.getByText('净买入未获取。')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '继续研究' }));
  expect(research).toHaveBeenCalledWith(expect.stringContaining('605069'));
  expect(research.mock.calls[0][0]).toContain('2026-09-13T03:00:00Z');
  expect(fetch).toHaveBeenCalledTimes(1);
});

test('failed recapture removes previous facts and prevents research of stale context', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(apiResponse(snapshot))
    .mockResolvedValueOnce(apiResponse({ ...snapshot, status: 'NO_WINDOW', message: '请展开同花顺主窗口' })));
  render(<ThsDesktopView onResearch={vi.fn()} />);
  await userEvent.click(screen.getByRole('button', { name: '读取当前页面' }));
  await screen.findByText('正和生态');
  await userEvent.click(screen.getByRole('button', { name: '读取当前页面' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('请展开同花顺主窗口');
  expect(screen.queryByText('正和生态')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '继续研究' })).not.toBeInTheDocument();
});

test('recorded example is explicit and never invokes capture or a model', async () => {
  const fetch = vi.fn();
  vi.stubGlobal('fetch', fetch);
  render(<ThsDesktopView onResearch={vi.fn()} />);
  await userEvent.click(screen.getByRole('button', { name: '查看实测示例' }));
  expect(screen.getByRole('heading', { name: /正和生态/ })).toBeInTheDocument();
  expect(screen.getByText('实测示例 · 非当前读取')).toBeInTheDocument();
  expect(fetch).not.toHaveBeenCalled();
});
