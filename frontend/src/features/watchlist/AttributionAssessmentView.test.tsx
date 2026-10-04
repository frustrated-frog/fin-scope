import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { AttributionAssessment } from '../../shared/types';
import { AttributionAssessmentView } from './AttributionAssessmentView';

const assessment: AttributionAssessment = {
  version: 1, status: 'COMPLETE', researchFocus: '订单进展改变了什么', focusReason: '区分商业化与盈利兑现',
  mainJudgment: '商业化有所推进，利润贡献仍需核验', pricingDebate: '能否转化为规模交付',
  explainedScope: ['产品验证进展'], unexplainedScope: ['无法解释具体涨幅'], missingInformation: ['订单金额未披露'],
  commentary: ['商业化有所推进，利润贡献仍需核验', '新增订单意味着验证进入新阶段'], warnings: [],
  marketContext: { instrumentCode: '600519', reportDate: '2026-09-18', quoteVerified: true, stockChangePct: 6,
    benchmarkName: '沪深300', benchmarkChangePct: 4, relativeChangePct: 2, limitations: [], source: '日线快照' },
  hypotheses: [{ id: 'h1', disposition: 'PREFERRED', explanation: '商业化推进', selectionReason: '公告直接支持',
    pricingMechanism: '兑现概率发生变化', explains: '商业化阶段', doesNotExplain: '具体涨幅',
    assumptions: ['能够复制交付'], revisionConditions: ['订单取消则削弱判断'], evidenceUrls: ['https://example.com/notice'] },
    { id: 'h2', disposition: 'NOT_ADOPTED', explanation: '利润已经大幅增长', selectionReason: '缺少利润数据',
      pricingMechanism: '盈利兑现尚未确认', explains: '无法确认', doesNotExplain: '盈利贡献',
      assumptions: [], revisionConditions: [], evidenceUrls: ['javascript:alert(1)'] }]
};

test('omits candidate explanations while retaining market context and research limitations', () => {
  render(<AttributionAssessmentView assessment={assessment} />);
  expect(screen.queryByText(assessment.researchFocus)).not.toBeInTheDocument();
  expect(screen.queryByLabelText('候选解释与改判条件')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('候选解释解读')).not.toBeInTheDocument();
  expect(screen.queryByText('订单取消则削弱判断')).not.toBeInTheDocument();
  expect(screen.queryByText('商业化推进')).not.toBeInTheDocument();
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
  expect(screen.getByText('+2.00 个百分点')).toBeVisible();
  expect(screen.getByText('证据缺口与研究限制')).toBeVisible();
  expect(screen.getByText('订单金额未披露')).toBeInTheDocument();
});

test('degraded report retains warnings and market data without candidate cards', () => {
  render(<AttributionAssessmentView assessment={{ ...assessment, status: 'DEGRADED', warnings: ['模型调用失败'] }} />);
  expect(screen.getByText('研判未完成')).toBeVisible();
  expect(screen.getByText('模型调用失败')).toBeVisible();
  expect(screen.queryByText(assessment.mainJudgment)).not.toBeInTheDocument();
  expect(screen.queryByText('候选解释与改判条件')).not.toBeInTheDocument();
  expect(screen.getByText('+6.00%')).toBeVisible();
});

test('does not leave an empty supplement when only candidate explanations are present', () => {
  const { container } = render(<AttributionAssessmentView assessment={{ ...assessment, marketContext: undefined, missingInformation: [] }} />);
  expect(container).toBeEmptyDOMElement();
});
