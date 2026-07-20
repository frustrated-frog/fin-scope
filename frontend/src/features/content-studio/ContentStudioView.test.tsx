import { render, screen, within } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';

import { ContentStudioView } from './ContentStudioView';

describe('ContentStudioView', () => {
  test('presents format and workflow status as one readable metadata rail', () => {
    render(
      <ContentStudioView
        contentIdeas={[{
          id: 1,
          eventId: 7,
          themeCode: 'ai_startup',
          title: 'Agent 产品的真实壁垒在哪里？',
          angle: '从工作流和数据闭环切入。',
          format: 'LONG_ARTICLE',
          audience: 'AI 创业者',
          score: 92,
          scoreReason: '选题具备长期解释价值。',
          outline: '1. 产品能力\n2. 数据闭环',
          status: 'IDEA'
        }]}
        onIdeaStatusChange={vi.fn().mockResolvedValue(undefined)}
        addToast={vi.fn()}
      />
    );

    const metadata = screen.getByLabelText('内容标签');
    expect(within(metadata).getByText('长文章')).toHaveAttribute('aria-label', '内容形态：长文章');
    expect(within(metadata).getByText('想法')).toHaveAttribute('aria-label', '推进状态：想法');
    expect(within(metadata).queryByText('LONG_ARTICLE')).not.toBeInTheDocument();
  });
});
