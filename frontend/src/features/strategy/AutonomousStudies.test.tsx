import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { AutonomousStudies } from './AutonomousStudies';

test('opens the actual evidence, teaches a losing example, and links to the source account', async () => {
  const user = userEvent.setup();
  const onSource = vi.fn();
  const url = '/api/quant/research-center';
  const material = {
    tool: 'EXECUTION',
    first: '2026-11-02',
    last: '2026-11-02',
    version: 8,
    source: 'frozen',
    title: '账户交易记录',
    observations: 24,
  };
  const record = {
    id: 7,
    state: 'PUBLISHED',
    trigger_key: 'daily:2026-11-03',
    created: 42,
    updated: 43,
    plan: {
      title: '费用解释了多少亏损？',
      material,
      concept: '交易成本',
      criterion: '比较扣费前后',
      hypothesis: '费用减损',
      motivation: '识别成本',
    },
    report: {
      finding: '当日净损失五元，夏普为 null',
      background: '成本与换手',
      theory: '频繁交易会积累费用',
      mathematics: '净损失等于费用',
      interpretation: '注意成交量',
      limits: '单日观察',
      nextQuestion: '其他日期呢？',
      method: '独立比较',
      outcome: 'UNSUPPORTED',
      value: '学习价值',
      merits: { 学习价值: 2 },
      reason: '亏损样本可以解释费用',
      model: 'Luna',
    },
  };
  const evidence = {
    source: material,
    names: {},
    metrics: {
      formula: '净损益 = 毛损益 − 费用',
      beforeFees: 0,
      fees: 5,
      net: -5,
      fills: 1,
      observations: 24,
      events: [],
      design: '按实际记录计算',
      limitation: '单日',
    },
  };
  const calls: string[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (path: string, init?: RequestInit) => {
      if (init?.method === 'POST') {
        calls.push(path);
        return apiResponse({ answer: '这一天的五元费用来自一笔成交。' });
      }
      return apiResponse(
        path === url
          ? { items: [record], highlights: [record], model: 'Luna', hasMore: false }
          : { ...record, evidence },
      );
    }),
  );
  render(<AutonomousStudies onSource={onSource} onStrategies={vi.fn()} />);
  const report = await screen.findByRole('article');
  expect(await within(report).findByText('扣费后盈亏（元）')).toBeInTheDocument();
  expect(within(report).getByText('假设未获支持')).toBeInTheDocument();
  expect(within(report).getByText('当日净损失五元，夏普为 暂无估计值')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '值得学习的研究' })).toBeInTheDocument();
  await user.click(within(report).getByRole('button', { name: '金融课堂' }));
  expect(within(report).getByText(evidence.metrics.formula)).toBeInTheDocument();
  await user.click(within(report).getByRole('button', { name: '实验设计' }));
  await user.click(within(report).getByRole('button', { name: /查看.*账户/ }));
  expect(onSource).toHaveBeenCalledWith(material);
  expect(calls).toEqual([]);
  await user.click(within(report).getByRole('button', { name: '追问助教' }));
  await user.type(within(report).getByRole('textbox'), '为什么？');
  await user.click(within(report).getByRole('button', { name: '请 AI 讲解' }));
  expect(await within(report).findByText('这一天的五元费用来自一笔成交。')).toBeInTheDocument();
  expect(calls).toEqual([url + '/7/question']);
});
