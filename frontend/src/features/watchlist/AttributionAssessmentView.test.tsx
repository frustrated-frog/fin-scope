import { fireEvent, render, screen } from '@testing-library/react';
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

test('shows focus and judgment without duplicating the lead, with explicit relative return units', () => {
  render(<AttributionAssessmentView assessment={assessment} />);
  expect(screen.getByRole('heading', { name: '订单进展改变了什么' })).toBeInTheDocument();
  expect(screen.getAllByText(assessment.mainJudgment)).toHaveLength(1);
  expect(screen.getByText('+2.00 个百分点')).toBeInTheDocument();
  expect(screen.getByText('订单取消则削弱判断')).toBeInTheDocument();
  const summary = screen.getByText('为什么采用这个解释');
  fireEvent.click(summary);
  expect(summary.closest('details')).toHaveAttribute('open');
  expect(screen.getByText('缺少利润数据')).toBeVisible();
  expect(screen.getAllByRole('link')).toHaveLength(1);
});

test('shows insufficient evidence and data gaps without inventing a comparison or revision scenario', () => {
  render(<AttributionAssessmentView assessment={{ ...assessment, status: 'INSUFFICIENT_EVIDENCE', hypotheses: [], commentary: [],
    marketContext: { instrumentCode: '600519', quoteVerified: false, limitations: ['目标日行情缺失'] } }} />);
  expect(screen.getByText('保留分歧')).toBeInTheDocument();
  expect(screen.getAllByText('暂无对照').length).toBeGreaterThan(1);
  expect(screen.queryByRole('heading', { name: '什么情况下需要改判' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByText('仍缺少哪些信息'));
  expect(screen.getByText('目标日行情缺失')).toBeVisible();
});

test('degraded history shows failure first and keeps market data without presenting fallback text as analysis', () => {
  render(<AttributionAssessmentView assessment={{ ...assessment, status: 'DEGRADED',
    researchFocus: '解释目标日价格变化及公开信息能够解释的边界',
    mainJudgment: '当前公开信息不足以形成可核验的主判断。', focusReason: '',
    hypotheses: [], commentary: [], explainedScope: [], unexplainedScope: [],
    warnings: ['确定研究焦点阶段未完成'] }} />);
  expect(screen.getByRole('heading', { name: '本次研判未完成' })).toBeVisible();
  expect(screen.getByText('确定研究焦点阶段未完成')).toBeVisible();
  expect(screen.getByText('+6.00%')).toBeVisible();
  expect(screen.queryByText('当前公开信息不足以形成可核验的主判断。')).not.toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '当前判断' })).not.toBeInTheDocument();
  expect(screen.queryByLabelText('解释边界')).not.toBeInTheDocument();
  expect(screen.queryByText('为什么采用这个解释')).not.toBeInTheDocument();
});
