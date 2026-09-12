import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { DailyResearchPanel } from './DailyResearchPanel';
import { THEME_STORAGE_KEY } from './marketResearch';

const research = { businessDate: '2026-09-11', selectionDate: '2026-09-10', sampleCount: 1, sourceCode: 'LOCAL_DAILY_BAR_PANEL', qualityStatus: 'PARTIAL', warnings: ['本地样本'], stocks: [{ instrumentCode: '600519.SH', return1d: -2, groupCodes: ['STRONG'] }], groups: [{ code: 'STRONG', label: '昨日强势组', definition: '昨日涨幅≥3%', eligibleCount: 1, memberCount: 1, validCount: 1, members: ['600519.SH'] }] };
function response(data: unknown) {
  return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, code: 'SUCCESS', message: '', traceId: 'test', timestamp: '', data }) };
}
beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(research)));
});
const sectors = [{ sectorCode: '1', sectorName: '半导体', return1d: 1, return5d: 4, return20d: -2, excessReturn5d: 2, breadthRatio: .7, rotationScore: 70 }];
test('filters compares and hands the selected sector to research', async () => {
  const open = vi.fn();
  render(<DailyResearchPanel businessDate="2026-09-11" sectors={sectors} onOpenStockDiscovery={open} />);
  fireEvent.change(screen.getByLabelText('机会形态'), { target: { value: 'REPAIR' } });
  expect(screen.getByText('1 / 1 个行业')).toBeInTheDocument();
  fireEvent.click(screen.getByLabelText('比较半导体'));
  fireEvent.click(screen.getByRole('button', { name: '研究已选行业' }));
  expect(open).toHaveBeenCalledWith(expect.objectContaining({ preferredSectors: ['半导体'], businessDate: '2026-09-11' }));
  await screen.findByText('昨日强势组');
});
test('keeps small sample member visible and adds stock to watchlist', async () => {
  render(<DailyResearchPanel businessDate="2026-09-11" sectors={[]} />);
  await screen.findByText('昨日强势组');
  expect(screen.getByText(/有效样本少于5只/)).toBeInTheDocument();
  fireEvent.click(screen.getByText('查看成员（1）'));
  fireEvent.click(screen.getByRole('button', { name: '加入自选' }));
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/watchlist', expect.objectContaining({ method: 'POST', body: JSON.stringify({ code: '600519', type: 'STOCK', groupName: '市场研究' }) })));
});
test('theme editor validates then persists manual relationships', async () => {
  render(<DailyResearchPanel businessDate="2026-09-11" sectors={[]} />);
  fireEvent.click(screen.getAllByRole('button', { name: '维护成员' })[0]);
  fireEvent.change(screen.getByLabelText('主题成员'), { target: { value: '600519.SH,贵州茅台,白酒,人工笔记' } });
  fireEvent.click(screen.getByRole('button', { name: '保存主题' }));
  expect(JSON.parse(localStorage.getItem(THEME_STORAGE_KEY) ?? '[]')[0].members[0].name).toBe('贵州茅台');
  await screen.findByText('昨日强势组');
});
test('request failure is local and retryable', async () => {
  vi.mocked(fetch).mockRejectedValueOnce(new Error('offline'));
  render(<DailyResearchPanel businessDate="2026-09-11" sectors={sectors} />);
  await screen.findByRole('button', { name: '重试样本加载' });
  expect(screen.getByText('1 / 1 个行业')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重试样本加载' }));
  await screen.findByText('昨日强势组');
});
test('discards a response from a previous date', async () => {
  let resolveOld!: (value: Response) => void;
  vi.mocked(fetch).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
  const { rerender } = render(<DailyResearchPanel businessDate="2026-09-10" sectors={[]} />);
  rerender(<DailyResearchPanel businessDate="2026-09-11" sectors={[]} />);
  await screen.findByText('昨日强势组');
  resolveOld(response({ ...research, businessDate: '2026-09-10', groups: [{ ...research.groups[0], label: '旧分组' }] }) as Response);
  await waitFor(() => expect(screen.queryByText('旧分组')).not.toBeInTheDocument());
});
test('saved members are filled automatically and completion refreshes cached research', async () => {
  let researchReads = 0;
  vi.mocked(fetch).mockImplementation(async (url) => {
    if (String(url).includes('/members/')) {
      return response({ instrumentCode: '600519.SH', businessDate: research.businessDate, status: 'READY', reason: 'COMPLETE', validBars: 22, requiredBars: 22, message: '行情完整' }) as Response;
    }
    researchReads += 1;
    return response({ ...research, cacheHit: true, calculatedAt: '2026-09-12T16:00:00+08:00' }) as Response;
  });
  const first = render(<DailyResearchPanel businessDate="2026-09-11" sectors={[]} />);
  fireEvent.click(screen.getAllByRole('button', { name: '维护成员' })[0]);
  fireEvent.change(screen.getByLabelText('主题成员'), { target: { value: '600519.SH,贵州茅台,白酒,人工笔记' } });
  fireEvent.click(screen.getByRole('button', { name: '保存主题' }));
  await screen.findByText(/主题行情检查完成.*完整 1只/);
  await screen.findByText(/使用日频缓存/);
  expect(researchReads).toBeGreaterThanOrEqual(2);
  first.unmount();
  vi.mocked(fetch).mockClear();
  render(<DailyResearchPanel businessDate="2026-09-11" sectors={[]} />);
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/market-pulse/research/2026-09-11/members/600519.SH', expect.objectContaining({ method: 'POST' })));
  await screen.findByText(/主题行情检查完成.*完整 1只/);
});
