import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { IndustryChainInspector } from './IndustryChainInspector';
import type { IndustryChainGraph } from './industryChainTypes';

const graph: IndustryChainGraph = {
  name: '人形机器人', summary: '核心部件与技术路线', limitations: '结构示意',
  schemaVersion: 'INDUSTRY_CHAIN_V3', nodes: [
    { nodeKey: 'stage:parts', type: 'STAGE', name: '核心部件', description: '传动与执行', stageOrder: 1, confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'component:reducer', type: 'COMPONENT', name: '减速器', description: '精密传动部件', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'technology:harmonic', type: 'TECHNOLOGY', name: '谐波传动', description: '高减速比技术路线', confidence: 'HIGH', evidenceRefs: ['E1'] }
  ], edges: [
    { edgeKey: 'belongs', sourceKey: 'component:reducer', targetKey: 'stage:parts', type: 'BELONGS_TO_STAGE', nature: 'INDUSTRY_LOGIC', description: '属于核心部件', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { edgeKey: 'enables', sourceKey: 'technology:harmonic', targetKey: 'component:reducer', type: 'ENABLES', nature: 'INDUSTRY_LOGIC', description: '支撑精密传动', confidence: 'HIGH', strength: 'PRIMARY', directionNote: '技术支撑产品性能', evidenceRefs: ['E1'] }
  ], evidence: [{ evidenceCode: 'E1', title: '机器人产业资料', source: 'example.com' }],
  researchContent: {
    overview: { lifecycle: 'GROWTH', prosperity: 'RISING', supplyDemand: 'STRUCTURAL', cycleType: '成长周期', demandDrivers: [], supplyDrivers: [], keyVariables: [], bottlenecks: [], overcapacityRisks: [], trendTags: [] },
    stageProfiles: [], companyProfiles: [], nodeProfiles: [{
      nodeKey: 'technology:harmonic', definition: '柔轮弹性变形实现精密传动', function: '提供高减速比与低回差',
      inputs: ['柔轮', '刚轮'], outputs: ['谐波减速器'], costDrivers: ['精密加工'], valueDrivers: ['寿命与精度'],
      barriers: ['齿形设计', '材料工艺'], coreMetrics: ['传动精度', '使用寿命'], risks: ['批量一致性'],
      maturity: 'SCALING', valueLevel: 'HIGH', bottleneckLevel: 'HIGH', localizationLevel: 'MEDIUM'
    }]
  }
};

test('renders a type-aware semantic dossier with related-node actions', async () => {
  const onSelectNode = vi.fn();
  const onToggleExpanded = vi.fn();
  render(<IndustryChainInspector graph={graph} selectedNodeKey="technology:harmonic"
    expanded={false} onSelectNode={onSelectNode} onToggleExpanded={onToggleExpanded} />);

  expect(screen.getAllByText('技术路线')).toHaveLength(2);
  expect(screen.getByText('规模化成长')).toBeInTheDocument();
  expect(screen.getByText('柔轮弹性变形实现精密传动')).toBeInTheDocument();
  expect(screen.getByText('柔轮')).toBeInTheDocument();
  expect(screen.getByText('谐波减速器')).toBeInTheDocument();
  expect(screen.getByText('齿形设计')).toBeInTheDocument();
  expect(screen.getByText('传动精度')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '查看关联节点 减速器' }));
  await userEvent.click(screen.getByRole('button', { name: '展开 谐波传动 的关联分支' }));
  expect(onSelectNode).toHaveBeenCalledWith('component:reducer');
  expect(onToggleExpanded).toHaveBeenCalledWith('technology:harmonic');
});
