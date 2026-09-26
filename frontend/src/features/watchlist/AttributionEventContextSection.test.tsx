import { fireEvent, render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { AttributionEventContext } from '../../shared/types';
import { AttributionEventContextSection } from './AttributionEventContextSection';

const context: AttributionEventContext = {
  version: 1, status: 'COMPLETE', asOfDate: '2026-09-24', summary: '从合作意向推进到正式订单',
  sources: [{ id: 'S1', title: '原始合作披露', url: 'https://example.com/notice', publishedAt: '2026-08-01', content: '披露合作事项', supplemental: true }],
  events: [{ title: '订单落地', framework: 'ORDER', changeType: 'SUBSTANTIVE_PROGRESS', currentStage: '签约待交付',
    relationshipBasis: '合同双方与此前合作披露一致', priorState: '此前只有意向', newInformation: '新增金额和交付安排',
    impactDirection: 'POSITIVE', impactAnalysis: '收入可见度改善，利润取决于交付成本',
    changedJudgment: '执行确定性提高', unchangedJudgment: '毛利率尚不明确', pendingConditions: ['后续交付公告'],
    timeline: [{ date: '2026-08-01', description: '签署合作意向', sourceIds: ['S1'] }, { date: null, description: '尚未确认时间的进展', sourceIds: ['S1'] }], sourceIds: ['S1'] }]
};

test('old reports add no empty section', () => {
  const { container } = render(<AttributionEventContextSection />);
  expect(container).toBeEmptyDOMElement();
});

test('shows chronological facts, information delta and explicit impact separately', () => {
  render(<AttributionEventContextSection context={context} />);
  expect(screen.getByRole('heading', { name: '事件脉络与本次增量' })).toBeVisible();
  expect(screen.getByText('此前只有意向')).toBeVisible();
  expect(screen.getByText('新增金额和交付安排')).toBeVisible();
  expect(screen.getByText('日期待确认')).toBeVisible();
  expect(screen.getByText('偏利好')).toBeVisible();
  expect(screen.getByText('影响分析 · 包含机制推演与成立条件')).toBeVisible();
  expect(screen.getByText('后续交付公告')).toBeVisible();
  fireEvent.click(screen.getByText(/本章研究来源/));
  expect(screen.getByText('披露合作事项')).toBeVisible();
});

test('partial and failed research remains readable without invalid links or invented placeholders', () => {
  render(<AttributionEventContextSection context={{ version: 1, status: 'UNAVAILABLE', summary: '扩展失败，原报告保留',
    sources: [{ id: 'S2', title: '无效来源', url: 'javascript:alert(1)' }],
    events: [{ title: '已有分析', impactAnalysis: '仍保留可能机制', sourceIds: ['S2'] }], warnings: ['补查暂不可用'] }} />);
  expect(screen.getByText('扩展失败，原报告保留')).toBeVisible();
  expect(screen.getByText('仍保留可能机制')).toBeVisible();
  expect(screen.getByText('补查暂不可用')).toBeVisible();
  expect(screen.queryAllByRole('link')).toHaveLength(0);
  expect(screen.queryByText('此前已经知道什么')).not.toBeInTheDocument();
});

test('long analysis is fully preserved', () => {
  const longText = '这是针对公司的完整机制推演。'.repeat(100);
  render(<AttributionEventContextSection context={{ ...context, events: [{ title: '长文', newInformation: longText }] }} />);
  expect(screen.getByText(longText)).toBeVisible();
});
