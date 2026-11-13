import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { StockFundamentalResearchPanel } from './StockFundamentalResearchPanel';
import { emptyNotes } from './fundamentalResearchTypes';
import type { FundamentalWorkspace, FundamentalWatch } from './fundamentalResearchTypes';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
vi.mock('./StockFundamentalsPanel', () => ({ StockFundamentalsPanel: ({ code }: { code: string }) => <p>季度趋势 {code}</p> }));
vi.mock('./FundamentalBusinessResearchPanel', () => ({ FundamentalBusinessResearchPanel: ({ onAdopt }: { onAdopt: (finding: unknown) => void }) => <button onClick={() => onAdopt({
  aspect: 'REVENUE_MODEL', label: '收入来源', noteField: 'revenueModel', content: '来自公开资料的主营业务认识', support: 'PARTIALLY_SUPPORTED', runId: 1,
  sources: [{ evidenceRef: 'E1', title: '公司资料', url: 'https://example.com/company' }],
})}>选取研究内容</button> }));
const base = '/api/watchlist/stocks/603618/fundamental-research';
const watch: FundamentalWatch = {
  id: 7, originKey: 'FINANCIAL:143:EARNINGS_QUALITY', title: '利润有没有变成现金', hypothesis: '等待回款改善', baseline: '三季报经营现金为负',
  supportCondition: '回款改善', challengeCondition: '账龄持续拉长', nextReviewDate: '2027-01-31', status: 'TRACKING', revision: 0, reviews: [],
};
function workspace(): FundamentalWorkspace {
  return {
    code: '603618', name: '杭电股份', notes: { ...emptyNotes }, reportId: 143, periodEnd: '2026-09-30', reportType: 'Q3', currency: 'CNY',
    sourceCode: 'SOURCE', summary: '检查利润与现金差异', assembledAt: '2026-11-14T12:00:00', notices: [], watches: [], suggestions: [watch],
    insights: [{ dimension: 'EARNINGS_QUALITY', title: watch.title, conclusion: watch.hypothesis, attention: true, nextCheck: '核对期后回款',
      metrics: [{ label: '经营现金流', conceptCode: 'OPERATING_CASH_FLOW', value: '-165663284.05', unit: 'CNY', reportId: 143, periodEnd: '2026-09-30' }] }],
    events: [{ sampleId: 4808, title: '回购专项贷款承诺函', subtype: 'REPURCHASE_FINANCING', dimension: 'CAPITAL_ALLOCATION', impact: '承诺额度不等于实际借款', nextCheck: '核对实际提款' }],
  };
}
beforeEach(() => vi.mocked(api).mockReset());

test('shows real amounts and period scope while preserving the existing trend view', async () => {
  vi.mocked(api).mockResolvedValue(workspace());
  render(<StockFundamentalResearchPanel code="603618" />);
  expect(await screen.findByText('-1.66 亿元')).toBeInTheDocument();
  expect(screen.getAllByText('2026-09-30 · 合并累计报告 · CNY')).toHaveLength(2);
  expect(screen.getByText('承诺额度不等于实际借款')).toBeInTheDocument();
  expect(api).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole('button', { name: '季度趋势与估值' }));
  expect(screen.getByText('季度趋势 603618')).toBeInTheDocument();
});

test('saves notes with their revision and records a review without changing the original baseline', async () => {
  const local = workspace();
  local.watches = [watch];
  vi.mocked(api).mockResolvedValue(local);
  render(<StockFundamentalResearchPanel code="603618" />);
  await screen.findByText('-1.66 亿元');
  await userEvent.click(screen.getByRole('button', { name: '补充经营笔记' }));
  await userEvent.type(screen.getByLabelText('收入从哪里来'), '电缆销售');
  await userEvent.click(screen.getByRole('button', { name: '保存经营笔记' }));
  expect(api).toHaveBeenCalledWith(`${base}/notes`, expect.objectContaining({ method: 'PUT', body: expect.stringContaining('电缆销售') }));
  await userEvent.click(screen.getByRole('button', { name: '记录本次复核 →' }));
  await userEvent.selectOptions(screen.getByLabelText('复核结果'), 'CHALLENGED');
  await userEvent.type(screen.getByLabelText('本次看到的新事实'), '最新附注显示账龄继续拉长');
  await userEvent.click(screen.getByRole('button', { name: '保存复核' }));
  expect(api).toHaveBeenLastCalledWith(`${base}/watches/7/review`, expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ id: 7, status: 'CHALLENGED', note: '最新附注显示账龄继续拉长', revision: 0, nextReviewDate: '2027-01-31' }) }));
  expect(local.watches[0].baseline).toBe('三季报经营现金为负');
});

test('saves an editable suggested watch and keeps the draft when a request fails', async () => {
  vi.mocked(api).mockResolvedValueOnce(workspace()).mockRejectedValueOnce(new Error('验证记录已变化'));
  render(<StockFundamentalResearchPanel code="603618" />);
  await screen.findByText('-1.66 亿元');
  await userEvent.click(screen.getByRole('button', { name: '加入验证清单 →' }));
  expect(screen.getByLabelText('当前事实与基线')).toHaveValue(watch.baseline);
  await userEvent.click(screen.getByRole('button', { name: '保存验证事项' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('验证记录已变化');
  expect(screen.getByLabelText('当前事实与基线')).toHaveValue(watch.baseline);
  expect(api).toHaveBeenLastCalledWith(`${base}/watches`, expect.objectContaining({ method: 'POST' }));
});

test('ignores a previous stock response arriving after the user switches stocks', async () => {
  let finish!: (value: FundamentalWorkspace) => void;
  vi.mocked(api).mockReturnValueOnce(new Promise(resolve => { finish = resolve; })).mockResolvedValueOnce({ ...workspace(), insights: [], events: [], summary: '当前公司底稿' });
  const { rerender } = render(<StockFundamentalResearchPanel code="603618" />);
  rerender(<StockFundamentalResearchPanel code="688012" />);
  expect(await screen.findByText('当前公司底稿')).toBeInTheDocument();
  await act(async () => finish(workspace()));
  expect(screen.queryByText('-1.66 亿元')).not.toBeInTheDocument();
  expect(screen.getByText('当前公司底稿')).toBeInTheDocument();
});

test('filters due items and submits the exact displayed comparison with a new review date', async () => {
  const local = workspace();
  local.watches = [{ ...watch, due: true, comparison: {
    state: 'UPDATED', reviewed: false, token: 'a'.repeat(64), message: '已有新财报',
    changes: [{ conceptCode: 'OPERATING_CASH_FLOW', label: '经营现金流', unit: 'CNY', baseline: '-30000000', current: '20000000', change: '50000000' }],
  } }, { ...watch, id: 8, title: '已结束的验证', status: 'CLOSED' }];
  vi.mocked(api).mockResolvedValue(local);
  render(<StockFundamentalResearchPanel code="603618" />);
  expect(await screen.findByText('+5000.00 万元')).toBeInTheDocument();
  expect(screen.queryByText('已结束的验证')).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '已结束 1' }));
  expect(screen.getByText('已结束的验证')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '已到期 1' }));
  await userEvent.click(screen.getByRole('button', { name: '对照新财报复核 →' }));
  expect((screen.getByLabelText('本次看到的新事实') as HTMLTextAreaElement).value).toContain('经营现金流');
  await userEvent.clear(screen.getByLabelText('下次复核日期'));
  await userEvent.type(screen.getByLabelText('下次复核日期'), '2027-04-30');
  await userEvent.click(screen.getByRole('button', { name: '保存复核' }));
  const payload = JSON.parse(vi.mocked(api).mock.lastCall![1]!.body as string);
  expect(payload.comparisonToken).toBe('a'.repeat(64));
  expect(payload.nextReviewDate).toBe('2027-04-30');
});

test('adopting research appends sources to an unsaved draft and preserves other manual notes', async () => {
  const local = workspace();
  local.notes = { ...emptyNotes, customers: '我记录的客户情况', sourceUrl: 'https://example.com/my-notes', revision: 3 };
  vi.mocked(api).mockResolvedValue(local);
  render(<StockFundamentalResearchPanel code="603618" />);
  await screen.findByText('-1.66 亿元');
  await userEvent.click(screen.getByRole('button', { name: '补充经营笔记' }));
  await userEvent.type(screen.getByLabelText('收入从哪里来'), '尚未保存的收入笔记');
  await userEvent.click(screen.getByRole('button', { name: '选取研究内容' }));
  const content = (screen.getByLabelText('收入从哪里来') as HTMLTextAreaElement).value;
  expect(content).toContain('尚未保存的收入笔记');
  expect(content).toContain('来自公开资料的主营业务认识');
  expect(content).toContain('https://example.com/company');
  expect(screen.getByLabelText('客户与回款')).toHaveValue('我记录的客户情况');
  expect(screen.getByLabelText('参考材料链接')).toHaveValue('https://example.com/my-notes');
  expect(api).toHaveBeenCalledTimes(1);
});
