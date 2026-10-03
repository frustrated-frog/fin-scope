import { render, screen, within } from '@testing-library/react';
import { expect, test } from 'vitest';
import { AttributionResearchInsights } from '../../shared/types';
import { AttributionResearchInsightsSection } from './AttributionResearchInsightsSection';

const insights: AttributionResearchInsights = {
  status: 'PARTIAL', asOfDate: '2026-09-18',
  businesses: [{ business: '光纤产品', position: '位于光通信材料环节', financialAnchor: '半年报口径：光通信业务收入增长',
    catalyst: '光纤价格改善', transmission: '售价提升可能改善单位利润', sensitivity: '关注订单和原料成本', direction: 'POSITIVE', sourceIds: ['B1', 'bad'] }],
  expectations: [{ topic: '从收入增长到利润弹性', priorExpectation: '此前判断为量增价稳', newInformation: '本次披露售价改善',
    expectationBasis: '依据此前披露推演，并非市场一致预期', repricingPath: '利润率弹性可能扩大', realized: '价格已有改善', nextCatalyst: '后续毛利率披露', direction: 'POSITIVE' }],
  comparisons: [
    { kind: 'STOCK', name: '测试目标', code: '603618', changePct: 2, fiveSessionChangePct: 4.22, source: '目标日日线' },
    { kind: 'PEER', name: '测试可比', code: '600487', reason: '同有光纤业务，业务结构不同', changePct: 3, stockRelativePct: -1, fiveSessionChangePct: null },
    { kind: 'SECTOR', name: '通信设备', code: '881111', changePct: 0, stockRelativePct: 2 }
  ], comparisonSummary: '目标股强于板块，弱于所选可比公司。',
  sources: [{ id: 'B1', title: '公司半年报', url: 'https://example.com/annual', publishedAt: '2026-08-01' }, { id: 'bad', title: '危险链接', url: 'javascript:alert(1)' }],
  warnings: ['部分五日行情待补充']
};

test('renders business transmission, actual comparisons and expectation changes as separate chapters', () => {
  render(<AttributionResearchInsightsSection insights={insights} />);
  expect(screen.getByRole('region', { name: '公司业务关联' })).toHaveTextContent('售价提升可能改善单位利润');
  expect(screen.getByRole('region', { name: '市场预期变化' })).toHaveTextContent('后续毛利率披露');
  expect(screen.getByRole('table')).toHaveAccessibleName(/近 5 日含目标日/);
  expect(screen.getByText('+4.22')).toBeVisible();
  expect(screen.getByText('-1.00')).toBeVisible();
  const peer = screen.getByText('测试可比').closest('tr')!;
  expect(within(peer).getByLabelText('暂无数据')).toBeVisible();
  expect(screen.getByText('0.00')).toBeVisible();
  expect(screen.getByRole('link', { name: /公司半年报/ })).toHaveAttribute('href', 'https://example.com/annual');
  expect(screen.queryByRole('link', { name: /危险链接/ })).toBeNull();
  expect(screen.getByText('部分五日行情待补充').closest('details')).not.toHaveAttribute('open');
});

test('historical reports do not get empty appendix shells', () => {
  const { container } = render(<AttributionResearchInsightsSection />);
  expect(container).toBeEmptyDOMElement();
});

test('partial appendix keeps readable content when quotes are missing', () => {
  render(<AttributionResearchInsightsSection insights={{ ...insights, comparisons: [], expectations: [] }} />);
  expect(screen.getByText('光纤产品')).toBeVisible();
  expect(screen.getByText(/本次行情对照暂未取得/)).toBeVisible();
  expect(screen.getByText(/本次预期变化尚未展开/)).toBeVisible();
  expect(screen.queryByRole('table')).toBeNull();
});
