import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { InvestmentRecognitionDesk } from './InvestmentRecognitionDesk';

const candidate = {
  id: 7,
  subjectType: 'STOCK',
  subjectCode: '600519',
  subjectName: '贵州茅台',
  status: 'CANDIDATE' as const,
  thesis: '盈利预期是否同步改善值得验证',
  observedChange: '最新上涨 3.20%',
  mechanism: '盈利上修可能消化估值压力',
  supportingData: ['涨跌幅 +3.20%', '成交指标 12.00'],
  counterData: ['单日价格可能由情绪驱动'],
  validationMetrics: ['下一期收入增速'],
  invalidationConditions: '盈利预期不升反降',
  horizon: '下一财报期',
  confidence: 'MEDIUM' as const,
  evidenceCompleteness: 'SUFFICIENT',
  dataAsOf: '2026-08-01T10:00:00',
  revision: 0
};

test('shows the complete investment reasoning chain and candidate actions', async () => {
  const onAccept = vi.fn(async () => undefined);
  const onStatus = vi.fn(async () => undefined);
  render(<InvestmentRecognitionDesk
    candidates={[candidate]}
    topics={[]}
    loading={false}
    running={false}
    onRun={vi.fn(async () => undefined)}
    onAccept={onAccept}
    onStatus={onStatus}
    onSearch={vi.fn(async () => undefined)}
    onOpenTopic={vi.fn()}
    onCreate={vi.fn(async () => undefined)}
  />);

  expect(screen.getByRole('heading', { name: '从市场变化，形成可验证的投资认识' })).toBeInTheDocument();
  expect(screen.getByText('贵州茅台')).toBeInTheDocument();
  expect(screen.getByText('最新上涨 3.20%')).toBeInTheDocument();
  expect(screen.getByText('盈利上修可能消化估值压力')).toBeInTheDocument();
  expect(screen.getByText('单日价格可能由情绪驱动')).toBeInTheDocument();
  expect(screen.getByText('盈利预期不升反降')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '收为正式认识' }));
  expect(onAccept).toHaveBeenCalledWith(7, 0);
  await userEvent.click(screen.getByRole('button', { name: '转为待补证据' }));
  expect(onStatus).toHaveBeenCalledWith(7, 'NEEDS_EVIDENCE', 0);
});

test('separates agent candidates, formal recognitions and evidence gaps', async () => {
  render(<InvestmentRecognitionDesk
    candidates={[candidate, { ...candidate, id: 8, subjectName: '沪深300ETF', status: 'NEEDS_EVIDENCE', thesis: '缺少有效行情' }]}
    topics={[{ id: 9, name: '消费盈利修复', lifecycleStatus: 'ACTIVE', masteryStatus: 'REVIEWING', revision: 1 }]}
    loading={false}
    running={false}
    onRun={vi.fn(async () => undefined)}
    onAccept={vi.fn(async () => undefined)}
    onStatus={vi.fn(async () => undefined)}
    onSearch={vi.fn(async () => undefined)}
    onOpenTopic={vi.fn()}
    onCreate={vi.fn(async () => undefined)}
  />);

  expect(screen.getByRole('button', { name: 'Agent 候选 1' })).toHaveAttribute('aria-pressed', 'true');
  await userEvent.click(screen.getByRole('button', { name: '待补证据 1' }));
  expect(screen.getByText('沪深300ETF')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '正式认识 1' }));
  expect(screen.getByText('消费盈利修复')).toBeInTheDocument();
  expect(screen.queryByText('沪深300ETF')).not.toBeInTheDocument();
});
