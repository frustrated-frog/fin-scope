import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { MarketChart } from './MarketChart';
import { GlobalMarketsView } from './GlobalMarketsView';
import { chartLines, core, type Snapshot } from './markets';

function quote(closes:Record<string,number>):Snapshot {
  return {series:{symbol:'SPX',source:'测试源',asOf:'2026-11-25T21:00:00Z',price:110,reference:100,closes},
    fallback:false,retrievedAt:'2026-11-25T21:01:00Z'};
}
afterEach(()=>{vi.unstubAllGlobals();localStorage.clear();});
test('aligns returns to a common observed day and preserves weekend gaps',()=>{
  const market=quote({'2026-11-20':100,'2026-11-23':110,'2026-11-24':121});
  const btc=quote({'2026-11-21':88,'2026-11-22':89,'2026-11-23':90,'2026-11-24':99});
  const lines=chartLines([core[0],core[4]],{SPX:market,BTC:btc},7,true);
  expect(lines.map(l=>l.points[0])).toEqual([{day:'2026-11-23',value:0},{day:'2026-11-23',value:0}]);
  expect(lines[1].points[1].value).toBeCloseTo(10);
  expect(chartLines([core[0]],{SPX:market},7,false)[0].points.map(p=>p.day)).not.toContain('2026-11-22');
});
test.each([false,true])('connects actual Friday and Monday closes in a single path (compare=%s)',compare=>{
  const closes={'2026-11-20':100,'2026-11-23':110,'2026-11-24':105};
  const quotes={SPX:quote(closes),BTC:quote({...closes,'2026-11-21':101,'2026-11-22':103})};
  const assets=compare?[core[0],core[4]]:[core[0]];
  const lines=chartLines(assets,quotes,7,compare);
  const {container}=render(<MarketChart lines={lines} percent={compare}/>);
  for (const line of lines) {
    const strokes=container.querySelectorAll(`path[stroke="${line.asset.color}"]`);
    expect(strokes).toHaveLength(1);
    expect(strokes[0].getAttribute('d')?.match(/[ML]/g)).toEqual(line.points.map((_,i)=>i?'L':'M'));
  }
});
test('changes instrument and range, compares markets, persists and removes a verified US favorite',async()=>{
  vi.stubGlobal('fetch',vi.fn(async (url)=>apiResponse(String(url).includes('/companies/')?[]:quote({'2026-11-23':100,'2026-11-24':110}))));
  const user=userEvent.setup();
  const {container}=render(<GlobalMarketsView/>);
  await screen.findByText('测试源');
  await user.click(screen.getByRole('button',{name:/现货黄金 XAU/}));
  expect(screen.getByRole('heading',{name:'现货黄金'})).toBeVisible();
  await user.click(screen.getByRole('button',{name:'7 天'}));
  await user.click(screen.getByRole('button',{name:'跨市场对比'}));
  expect(within(container.querySelector<HTMLElement>('.gm-basket')!).getByRole('button',{name:'比特币'})).toHaveAttribute('aria-pressed','true');
  fireEvent.change(screen.getByRole('textbox',{name:'搜索美股'}),{target:{value:'英伟达'}});
  await user.click(await screen.findByRole('button',{name:'英伟达 NVDA'}));
  expect(await screen.findByRole('heading',{name:'英伟达'})).toBeVisible();
  expect(localStorage.getItem('finscope.global-markets.watchlist.v1')).toContain('NVDA');
  await user.click(screen.getByRole('button',{name:'移出自选'}));
  expect(localStorage.getItem('finscope.global-markets.watchlist.v1')).toBe('[]');
});

test('loads intraday points for the selected instrument and switches the chart with the instrument',async()=>{
  vi.stubGlobal('fetch',vi.fn(async url=>{
    const path=String(url);
    if (path.endsWith('/intraday')) {
      return apiResponse({source:path.includes('/XAU/')?'黄金分时源':'指数分时源',window:'一分钟价格',
        points:{'2024-01-02T15:59:00Z':100,'2024-01-02T16:00:00Z':101}});
    }
    return apiResponse(path.includes('/companies/')?[]:quote({'2024-01-02':100}));
  }));
  const user=userEvent.setup();
  render(<GlobalMarketsView/>);
  await user.click(screen.getByRole('button',{name:'分时',exact:true}));
  expect(await screen.findByText(/指数分时源/)).toBeVisible();
  await user.click(screen.getByRole('button',{name:/现货黄金 XAU/}));
  expect(await screen.findByText(/黄金分时源/)).toBeVisible();
  expect(screen.queryByText(/指数分时源/)).toBeNull();
  expect(screen.getByRole('img',{name:/分时走势/})).toBeVisible();
});
