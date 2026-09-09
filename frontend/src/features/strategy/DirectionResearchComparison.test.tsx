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
