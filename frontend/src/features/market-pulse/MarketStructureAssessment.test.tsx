import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { MarketStructureAssessment } from './MarketStructureAssessment';
import type { MarketReading } from './useMarketReading';
import type { MarketTransitionDecision } from './marketPulseTypes';
vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
const decision: MarketTransitionDecision = { transition: { code: 'RANGE_BALANCE', label: '多空暂时平衡', strength: 50, summary: '规则观察', drivers: [] }, gauges: [], trajectory: [], scenarios: [],
  discoveryContext: { transitionCode: 'RANGE_BALANCE', transitionLabel: '多空暂时平衡', riskPosture: 'BALANCED', preferredSectors: [], avoidSectors: [], chasePolicy: 'PULLBACK_ONLY', summary: '规则观察' } };
const point = { title: '参与改善', detail: '上涨参与回升，仍需观察持续性。', factIds: ['F1'] };
const reading: MarketReading = { businessDate: '2026-11-17', status: 'READY', facts: [{ id: 'F1', businessDate: '2026-11-17', label: '市场参与', value: '上涨比例 40%' }], sectorNames: {},
  structure: [{ dimension: 'REPAIR_QUALITY', label: '修复质量', baselineScore: 58, adjustment: -8, finalScore: 50, scope: '全市场参与样本', components: { 上涨参与: 40, 成交配合: 76 }, reason: '修复尚未扩散', counterEvidence: '成交支持仍需延续', factIds: ['F1'] },
    { dimension: 'LEADERSHIP', label: '主线持续性', scope: '有效行业不足', components: {}, factIds: [] }],
  report: { headline: '局部改善，扩散尚未确认', summary: '先观察参与面变化。', stateLabel: '修复观察', change: point, drivers: [point], contradictions: [point], conditions: [point], sectors: [],
    scenarios: [{ thesis: { ...point, title: '参与面继续修复' }, confirmation: { metric: 'ADVANCE_RATIO', direction: 'RISE', explanation: point }, invalidation: { metric: 'ADVANCE_RATIO', direction: 'FALL', explanation: point } }] },
  previousScenarios: [{ sourceDate: '2026-11-16', title: '前日修复观察', confirmation: 'MET', invalidation: 'UNAVAILABLE', confirmationDetail: '上涨比例：20% → 40%', invalidationDetail: '数据缺失或样本不可比' }] };
const props = { workspace: { businessDate: '2026-11-17', qualityStatus: 'READY' as const }, decision };
beforeEach(() => { vi.mocked(api).mockReset(); });
test('shows baseline adjustment final score and unavailable dimensions independently', async () => {
  vi.mocked(api).mockResolvedValue(reading);
  render(<MarketStructureAssessment {...props} />);
  expect(await screen.findByRole('heading', { name: reading.report!.headline })).toBeInTheDocument();
  const row = screen.getByRole('article', { name: '修复质量' });
  expect(within(row).getByText('58')).toBeInTheDocument();
  expect(within(row).getByText('-8')).toBeInTheDocument();
  expect(within(row).getByText('修复尚未扩散')).toBeInTheDocument();
  expect(within(screen.getByRole('article', { name: '主线持续性' })).getByText('数据不足')).toBeInTheDocument();
  expect(screen.getByText('上涨比例较 2026-11-17 上升')).toBeInTheDocument();
  expect(screen.getByText('确认条件 · 条件已出现')).toBeInTheDocument();
  expect(screen.getByText('否定条件 · 数据不足，暂不判断')).toBeInTheDocument();
  expect(vi.mocked(api).mock.calls.every(([, options]) => options?.method !== 'POST')).toBe(true);
});
test('disabled AI retains engineering scores and prior verification without generation', async () => {
  vi.mocked(api).mockResolvedValue({ ...reading, status: 'DISABLED', report: undefined, structure: reading.structure!.map(row => ({ ...row, adjustment: null, finalScore: row.baselineScore, reason: undefined })) });
  render(<MarketStructureAssessment {...props} />);
  expect(await screen.findByText('AI 已关闭，规则基准和历史情景核对继续运行。')).toBeInTheDocument();
  expect(screen.getByText('未启用')).toBeInTheDocument();
  expect(screen.getByText('确认条件 · 条件已出现')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '生成当日研判' })).not.toBeInTheDocument();
});
test('explicit generation uses the shared daily-report endpoint', async () => {
  vi.mocked(api).mockResolvedValue({ ...reading, status: 'NOT_GENERATED', report: undefined });
  render(<MarketStructureAssessment {...props} />);
  fireEvent.click(await screen.findByRole('button', { name: '生成当日研判' }));
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/market-pulse/ai/2026-11-17?retry=false', { method: 'POST' }));
});
test('rejects a different date response and retains rule navigation', async () => {
  vi.mocked(api).mockResolvedValue({ ...reading, businessDate: '2026-11-16' });
  render(<MarketStructureAssessment {...props} />);
  expect(await screen.findByRole('button', { name: '重新读取研判' })).toBeInTheDocument();
  expect(screen.queryByText('局部改善，扩散尚未确认')).not.toBeInTheDocument();
  expect(screen.getByText('规则轨迹、参考情景与股票发现')).toBeInTheDocument();
});
