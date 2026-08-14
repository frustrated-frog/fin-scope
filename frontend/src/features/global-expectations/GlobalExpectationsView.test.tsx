import { render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest';

import { GlobalExpectationsView } from './GlobalExpectationsView';

test('presents probability movement as a research lead instead of a trading call', async () => {
  render(<GlobalExpectationsView addToast={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: /全球预期/ })).toBeInTheDocument();
  expect(screen.getByText(/重新定价什么/)).toBeInTheDocument();
  expect(screen.getByText('待核验 · 刚刚')).toBeInTheDocument();
});
