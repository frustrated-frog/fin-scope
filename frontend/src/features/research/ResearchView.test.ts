import { describe, expect, it } from 'vitest';
import { remainingSearchActions } from './ResearchView';

describe('remainingSearchActions', () => {
  it('counts public and structured material calls against the same budget', () => {
    const detail = {
      run: { mode: 'DEEP' },
      agentCore: {
        decisions: [
          { toolCode: 'public_news_search', status: 'COMPLETED' },
          { toolCode: 'research_material_search', status: 'FAILED' },
          { toolCode: 'evidence_assess', status: 'COMPLETED' }
        ]
      },
      runtime: null
    } as unknown as Parameters<typeof remainingSearchActions>[0];

    expect(remainingSearchActions(detail)).toBe(4);
  });
});
