import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { CapitalRuleExplanationCard } from './CapitalRuleExplanationCard';

test('summarizes traceable evidence without exposing internal metric identifiers', () => {
  render(<CapitalRuleExplanationCard explanation={{
    summary: '日内资金方向发生变化。',
    ruleVersion: 'capital-rules-v2',
    items: [{
      level: 'OBSERVATION',
      text: '日内主力净流向发生有效方向反转。',
      metricRefs: [
        'flow:3544:mainNetInflow',
        'flow:3545:mainNetInflow',
        'flow:3546:mainNetInflow'
      ]
    }],
    dataGaps: []
  }} />);

  expect(screen.getByText('已关联 3 条可追溯证据')).toBeInTheDocument();
  expect(screen.queryByText(/flow:3544/)).not.toBeInTheDocument();
});
