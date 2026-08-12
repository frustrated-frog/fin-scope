import { expect, test } from 'vitest';

import { industryChainLayerDefinition, industryChainNodeLayerLabel } from './industryChainLayers';
import type { IndustryChainNodeProfile } from './industryChainTypes';

const materialProfile: IndustryChainNodeProfile = {
  nodeKey: 'material:copper', definition: '高纯导体材料', function: '承担高速信号传输',
  inputs: [], outputs: [], costDrivers: [], valueDrivers: [], barriers: [], coreMetrics: [], risks: [],
  maturity: 'MATURE', valueLevel: 'MEDIUM', bottleneckLevel: 'HIGH', localizationLevel: 'HIGH'
};

test('maps semantic profiles to visible Chinese layer labels', () => {
  expect(industryChainNodeLayerLabel(materialProfile, 'VALUE', 'MATERIAL')).toBe('中价值');
  expect(industryChainNodeLayerLabel(materialProfile, 'BOTTLENECK', 'MATERIAL')).toBe('关键卡点');
  expect(industryChainNodeLayerLabel(materialProfile, 'TECHNOLOGY', 'MATERIAL')).toBe('成熟稳定');
  expect(industryChainNodeLayerLabel(materialProfile, 'LOCALIZATION', 'MATERIAL')).toBe('国产化较高');
  expect(industryChainNodeLayerLabel(undefined, 'LOCALIZATION', 'MATERIAL')).toBe('暂无评级');
  expect(industryChainNodeLayerLabel(undefined, 'COMPANY', 'COMPANY')).toBe('代表公司');
});

test('provides an explicit legend and usage guidance for every layer', () => {
  expect(industryChainLayerDefinition('BOTTLENECK').legend.map((item) => item.label))
    .toEqual(['关键卡点', '一般约束', '低约束', '暂无评级']);
  expect(industryChainLayerDefinition('COMPANY').description).toContain('自动展示');
  expect(industryChainLayerDefinition('STRUCTURE').legend).not.toHaveLength(0);
});
