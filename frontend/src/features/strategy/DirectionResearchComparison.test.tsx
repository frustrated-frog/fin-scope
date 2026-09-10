import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { DirectionResearchComparison } from './DirectionResearchComparison';

test('shows the preselected experiment against both strong baselines with retrospective scope', () => {
  render(<DirectionResearchComparison />);
  expect(screen.getByText(/240 只股票.*60 个交易日.*13290 个股票日/)).toBeInTheDocument();
  expect(screen.getByText(/50.12%/)).toBeInTheDocument();
  expect(screen.getByText(/54.04%/)).toBeInTheDocument();
  expect(screen.getByText(/52.10%/)).toBeInTheDocument();
  expect(screen.getByText(/未超过较强对照/)).toBeInTheDocument();
  expect(screen.getByText(/未替换线上模型/)).toBeInTheDocument();
});

test('shows industry information experiment separately with data and uncertainty limitations', () => {
  render(<DirectionResearchComparison />);
  expect(screen.getByText('行业信息增量 · 直接涨跌分类')).toBeInTheDocument();
  expect(screen.getByText(/180 个交易日/)).toBeInTheDocument();
  expect(screen.getByText(/供应商历史重建/)).toBeInTheDocument();
  expect(screen.getByText(/本轮行业信息未形成可信提升/)).toBeInTheDocument();
});
