import type {
  IndustryChainLayer, IndustryChainNodeProfile, IndustryChainNodeType
} from './industryChainTypes';

export type IndustryChainLayerLegendTone = 'high' | 'medium' | 'low' | 'neutral';

export type IndustryChainLayerDefinition = {
  value: IndustryChainLayer;
  label: string;
  hint: string;
  description: string;
  legend: Array<{ label: string; tone: IndustryChainLayerLegendTone }>;
};

export const INDUSTRY_CHAIN_LAYERS: IndustryChainLayerDefinition[] = [
  {
    value: 'STRUCTURE', label: '产业结构', hint: '节点与价值流',
    description: '阅读上中下游节点、价值流向与关系证据。',
    legend: [
      { label: '行业逻辑', tone: 'high' },
      { label: '公开披露', tone: 'medium' },
      { label: '研究推断', tone: 'low' }
    ]
  },
  {
    value: 'VALUE', label: '价值分配', hint: '价值获取能力',
    description: '比较各环节的价值获取能力与议价位置。',
    legend: ratingLegend(['高价值', '中价值', '低价值'])
  },
  {
    value: 'BOTTLENECK', label: '产业瓶颈', hint: '关键卡点强度',
    description: '突出制约扩产、交付或性能的关键卡点。',
    legend: ratingLegend(['关键卡点', '一般约束', '低约束'])
  },
  {
    value: 'TECHNOLOGY', label: '技术路线', hint: '技术成熟阶段',
    description: '识别技术萌芽、规模放量与成熟衰退阶段。',
    legend: [
      { label: '规模化成长', tone: 'high' },
      { label: '技术萌芽', tone: 'medium' },
      { label: '成熟稳定', tone: 'low' },
      { label: '路线衰退', tone: 'low' },
      { label: '暂无评级', tone: 'neutral' }
    ]
  },
  {
    value: 'LOCALIZATION', label: '国产替代', hint: '本土供给水平',
    description: '观察本土供给能力与国产替代进程。',
    legend: [
      { label: '全球领先', tone: 'high' },
      { label: '国产化较高', tone: 'high' },
      { label: '提升中', tone: 'medium' },
      { label: '国产化较低', tone: 'low' },
      { label: '暂无评级', tone: 'neutral' }
    ]
  },
  {
    value: 'COMPANY', label: '公司生态', hint: '代表参与者',
    description: '自动展示代表公司及其所在产业环节。',
    legend: [
      { label: '代表公司', tone: 'high' },
      { label: '关联环节', tone: 'low' }
    ]
  }
];

export function industryChainLayerDefinition(layer: IndustryChainLayer): IndustryChainLayerDefinition {
  return INDUSTRY_CHAIN_LAYERS.find((item) => item.value === layer) ?? INDUSTRY_CHAIN_LAYERS[0];
}

export function industryChainNodeLayerLabel(
  profile: IndustryChainNodeProfile | undefined,
  layer: IndustryChainLayer,
  nodeType: IndustryChainNodeType
): string {
  if (layer === 'STRUCTURE') return '';
  if (layer === 'COMPANY') return nodeType === 'COMPANY' ? '代表公司' : '关联环节';
  if (!profile) return '暂无评级';
  if (layer === 'VALUE') return levelLabel(profile.valueLevel, ['高价值', '中价值', '低价值']);
  if (layer === 'BOTTLENECK') return levelLabel(profile.bottleneckLevel, ['关键卡点', '一般约束', '低约束']);
  if (layer === 'TECHNOLOGY') {
    return { SCALING: '规模化成长', EMERGING: '技术萌芽', MATURE: '成熟稳定', DECLINING: '路线衰退' }[profile.maturity];
  }
  return { LEADING: '全球领先', HIGH: '国产化较高', MEDIUM: '提升中', LOW: '国产化较低' }[profile.localizationLevel];
}

function ratingLegend(labels: [string, string, string]) {
  return [
    { label: labels[0], tone: 'high' as const },
    { label: labels[1], tone: 'medium' as const },
    { label: labels[2], tone: 'low' as const },
    { label: '暂无评级', tone: 'neutral' as const }
  ];
}

function levelLabel(value: 'HIGH' | 'MEDIUM' | 'LOW', labels: [string, string, string]) {
  return { HIGH: labels[0], MEDIUM: labels[1], LOW: labels[2] }[value];
}
