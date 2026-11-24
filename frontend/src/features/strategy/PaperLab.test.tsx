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
  await user.type(screen.getByLabelText('每个账户的初始本金（元）'), '24000');
  await user.selectOptions(screen.getByLabelText('实验股票池'), 'WATCHLIST');
  await user.click(screen.getByRole('button', { name: 'AI 编写规则并回测' }));
  expect(await screen.findByRole('button', { name: '启动模拟' })).toBeEnabled();
  expect(creates).toEqual([{ hypothesis: '', factorCode: 'MOMENTUM', capital: 24000, pool: 'WATCHLIST' }]);
  expect(posts).toEqual([endpoint]);
});
