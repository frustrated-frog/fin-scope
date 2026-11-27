import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { useState } from 'react';
import { PaperLab, usePaperCatalog } from './PaperLab';

test('research freezes the requested pool and leaves deployment to the user', async () => {
  const endpoint = '/api/quant/paper/versions';
  const creates: unknown[] = [];
  const posts: string[] = [];
  const spec = {
    capital: 24000, codes: ['600000'], source: '自选股', origin: '学习实验',
    rule: { name: '测试动量', hypothesis: '观察短窗动量', slots: 1, entry: .01, exit: -.01,
      terms: [{ feature: 'momentum', window: 5, weight: 1 }] }
  };
  vi.stubGlobal('fetch', vi.fn(async (url, options) => {
    if (options?.method === 'POST') {
      posts.push(String(url));
      creates.push(JSON.parse(options.body));
      return apiResponse({ id: 7 });
    }
    if (String(url) === endpoint) {
      return apiResponse(creates.length ? [{ id: 7, spec }] : []);
    }
    return apiResponse({ history: { curve: [], fees: 0, fills: 0 }, daily: [], days: [], points: [] });
  }));

  const user = userEvent.setup();
  function Lab() {
    const catalog = usePaperCatalog();
    const [id, setId] = useState<number>();
    return <PaperLab key={id ?? 'new'} seed={{hypothesis: '', factorCode: 'MOMENTUM'}} initialId={id}
      catalog={catalog} onExperiment={setId} onAccount={vi.fn()} onBrowse={vi.fn()} onCompare={vi.fn()} />;
  }
  render(<Lab />);
  await user.selectOptions(screen.getByLabelText('策略引擎'), 'luna');
  await user.type(screen.getByLabelText('每个账户的初始本金（元）'), '24000');
  await user.selectOptions(screen.getByLabelText('实验股票池'), 'WATCHLIST');
  const option = screen.getByRole('checkbox', { name: '回测完成后自动启动模拟账户' });
  expect(option).toBeChecked();
  await user.click(option);
  await user.click(screen.getByRole('button', { name: 'AI 编写规则并回测' }));
  expect(await screen.findByRole('button', { name: '启动模拟' })).toBeEnabled();
  expect(creates).toEqual([{ hypothesis: '', autoStart: false, factorCode: 'MOMENTUM', capital: 24000, pool: 'WATCHLIST' }]);
  expect(posts).toEqual([endpoint]);
});

test('account holdings, replay and a saved review display names for an existing experiment', async () => {
  const code = '000823';
  const at = '2026-11-25T10:30:00';
  const rule = { name: '动量', hypothesis: '短窗动量', terms: [], entry: .01, exit: 0, slots: 1 };
  const spec = { capital: 20000, codes: [code], source: '自选股', pool: 'WATCHLIST', origin: '', rule };
  const book = { equity: 20000, cash: 19000, realized: 0, fees: 0, fills: 1,
    positions: { [code]: { lots: [[at, 100] as [string, number]], cost: 1000, price: 10 } } };
  const detail = { stockNames: { [code]: '超声电子' }, history: { curve: [], fills: 0, fees: 0 },
    daily: [], days: [at.slice(0, 10)], day: at.slice(0, 10), review: { at, model: 'test', text: '持有000823.SZ。' },
    points: [{ at, equity: 20000, reason: '已检查', holdings: { [code]: 100 }, scores: { [code]: .02 },
      events: [{ action: '成交', side: 'BUY', code, score: .02, rank: 1, reason: '达到阈值', available: at }] }] };
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse(detail)));
  const catalog = { rows: [{ id: 1, spec, book, started: at }], error: '', loading: false, refresh: vi.fn() };
  const { container } = render(<PaperLab accountsOnly catalog={catalog} onExperiment={vi.fn()} onAccount={vi.fn()}
    onBrowse={vi.fn()} onCompare={vi.fn()} />);
  const event = await screen.findByText('成交 · 超声电子');
  await userEvent.click(event);
  expect(screen.getByText('买入 超声电子')).toBeVisible();
  expect(screen.getByText('持有超声电子。')).toBeVisible();
  expect(container.querySelector('.paper-position b')).toHaveTextContent('超声电子');
  expect(container.querySelector('.paper-scores')).toHaveTextContent('超声电子');
  expect(container).not.toHaveTextContent(code);
});

test('queues a Codex strategy without a handwritten hypothesis and returns to its task list', async () => {
  const requests: { url: string; body: Record<string, unknown> }[] = [];
  vi.stubGlobal('fetch', vi.fn(async (url, options) => {
    requests.push({ url: String(url), body: JSON.parse(options.body) });
    return apiResponse({ jobId: 42 });
  }));
  const browse = vi.fn();
  const user = userEvent.setup();
  render(<PaperLab seed={{ hypothesis: '' }} catalog={{rows: [], loading: false, error: '', refresh: vi.fn()}}
    onBrowse={browse} onExperiment={vi.fn()} onAccount={vi.fn()} onCompare={vi.fn()}/>);
  expect(screen.getByLabelText('策略引擎')).toHaveValue('codex');
  await user.type(screen.getByLabelText('每个账户的初始本金（元）'), '25000');
  await user.click(screen.getByLabelText(/满五个模拟交易日/));
  await user.click(screen.getByRole('button', {name: '提交 Codex 研究'}));
  expect(requests).toEqual([{url: '/api/quant/paper/versions/jobs?autoIterate=true',
    body: {hypothesis: '', capital: 25000, pool: 'DISCOVERY', autoStart: true}}]);
  expect(browse).toHaveBeenCalledOnce();
});

test('starts a dated Ridge comparison with the specified capital and deployment choice', async () => {
  const fetcher = vi.fn(async () => apiResponse({ jobId: 43 }));
  vi.stubGlobal('fetch', fetcher);
  const user = userEvent.setup();
  render(<PaperLab seed={{ hypothesis: '' }} catalog={{rows: [], loading: false, error: '', refresh: vi.fn()}}
    onBrowse={vi.fn()} onExperiment={vi.fn()} onAccount={vi.fn()} onCompare={vi.fn()}/>);
  await user.selectOptions(screen.getByLabelText('策略引擎'), 'ridge');
  expect(screen.getByLabelText('实验股票池')).toBeDisabled();
  await user.type(screen.getByLabelText('每个账户的初始本金（元）'), '10000');
  await user.click(screen.getByLabelText('回测完成后自动启动模拟账户'));
  await user.click(screen.getByRole('button', { name: '训练 Ridge 并对照回测' }));
  expect(fetcher).toHaveBeenCalledWith('/api/quant/paper/versions/jobs/ridge', expect.objectContaining({
    method: 'POST', body: JSON.stringify({ autoStart: false, capital: 10000 }),
  }));
});

test('submits the library selection with explicit liquidity units and a separate deployment choice', async () => {
  const requests: Array<{url:string; body:unknown}> = [];
  vi.stubGlobal('fetch', vi.fn(async (url, options) => {
    requests.push({url:String(url), body:JSON.parse(options.body)});
    return apiResponse({jobId:44});
  }));
  const browse = vi.fn();
  const user = userEvent.setup();
  render(<PaperLab seed={{hypothesis:'', classic:'REVERSION'}} catalog={{rows:[], loading:false, error:'', refresh:vi.fn()}}
    onBrowse={browse} onCompare={vi.fn()} onExperiment={vi.fn()} onAccount={vi.fn()}/>);
  expect(screen.getByLabelText('策略类型')).toHaveValue('REVERSION');
  expect(screen.getByLabelText('回测完成后自动启动模拟账户')).not.toBeChecked();
  await user.type(screen.getByLabelText('每个账户的初始本金（元）'), '10000');
  const turnover = screen.getByLabelText(/最低平均成交额估算/);
  await user.clear(turnover);
  await user.type(turnover, '25');
  await user.selectOptions(screen.getByLabelText('权重分配'), 'INVERSE_VOL');
  await user.click(screen.getByRole('button', {name:'运行经典策略回测'}));
  expect(requests).toEqual([{url:'/api/quant/paper/versions/jobs/classic', body:expect.objectContaining({
    capital:10000, autoStart:false, recipe:expect.objectContaining({family:'REVERSION', allocation:'INVERSE_VOL', minTurnover:250000}),
  })}]);
  expect(browse).toHaveBeenCalledOnce();
});
