import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { AttributionNewsAnalysisView } from './AttributionNewsAnalysisView';

test('shows specialist reasoning and only the relevant time horizons', () => {
  render(<AttributionNewsAnalysisView analysis={{
    types: ['ORDER', 'PRICE_CHANGE'], direction: 'MIXED', keyChange: '订单增量与成本压力并存',
    businessImpacts: [{ label: '收入确认', analysis: '按交付确认收入，签约不等于当期利润' }],
    mediumTermImpact: '交付期观察收入与回款', chainReaction: '原材料供应商受益，公司面临成本压力'
  }} />);
  expect(screen.getByRole('region', { name: '新闻深读' })).toBeInTheDocument();
  expect(screen.getByText('订单兑现')).toBeInTheDocument();
  expect(screen.getByText('多空交织')).toBeInTheDocument();
  expect(screen.getByText('按交付确认收入，签约不等于当期利润')).toBeInTheDocument();
  expect(screen.getByText('中期 · 经营兑现')).toBeInTheDocument();
  expect(screen.queryByText('短期 · 交易反应')).not.toBeInTheDocument();
  expect(screen.queryByText('长期 · 竞争格局')).not.toBeInTheDocument();
});

test('legacy and empty analysis do not add empty panels', () => {
  const { container, rerender } = render(<AttributionNewsAnalysisView />);
  expect(container).toBeEmptyDOMElement();
  rerender(<AttributionNewsAnalysisView analysis={{ types: ['ORDER'], direction: 'POSITIVE', keyChange: ' ' }} />);
  expect(container).toBeEmptyDOMElement();
});
