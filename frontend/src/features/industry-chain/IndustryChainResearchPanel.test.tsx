import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { IndustryChainResearchPanel } from './IndustryChainResearchPanel';
import type { IndustryChainGraph } from './industryChainTypes';

test('guides an older graph to refresh when research content is unavailable', () => {
  const graph = {
    name: '半导体设备', summary: '半导体制造所需设备产业链', limitations: '',
    schemaVersion: 'INDUSTRY_CHAIN_V1', nodes: [], edges: [], evidence: []
  } as IndustryChainGraph;

  render(<IndustryChainResearchPanel graph={graph} />);

  expect(screen.getByText('刷新图谱后生成研究内容')).toBeInTheDocument();
  expect(screen.getByText('产业结构仍可在“产业全景”中查看。')).toBeInTheDocument();
});
