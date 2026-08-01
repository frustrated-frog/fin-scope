import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { KnowledgeMarkdown } from './KnowledgeMarkdown';

test('renders headings, paragraphs, and lists without markdown markers', () => {
  render(<KnowledgeMarkdown value={'## 投资命题\n半导体景气回升。\n\n### 后续验证\n- 跟踪月度营收\n- 跟踪现货价格'} />);

  expect(screen.getByRole('heading', { name: '投资命题' })).toBeInTheDocument();
  expect(screen.getByText('半导体景气回升。')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '后续验证' })).toBeInTheDocument();
  expect(screen.getByRole('list')).toHaveTextContent('跟踪月度营收');
  expect(screen.queryByText(/##|###/)).not.toBeInTheDocument();
});
