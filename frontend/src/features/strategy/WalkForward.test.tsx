import {render,screen,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {expect,test,vi} from 'vitest';
import {apiResponse} from '../../test/apiEnvelope';
import {defaultRecipe} from './ClassicStrategies';
import {WalkForward} from './WalkForward';

test('compares the selected and fixed parameters and grounds the tutor in the chosen run', async ()=>{
  const posts:string[]=[];
  const metrics={net:-.01,drawdown:.02,fills:2,fees:10};
  vi.stubGlobal('fetch',vi.fn(async (url: string, init?:RequestInit)=>{
    if (init?.method==='POST') {
      posts.push(String(url));
      return apiResponse({model:'Luna',observation:'本轮持平',calculation:'差值为零',limits:'一个窗口',hypothesis:'改变窗口长度',nextPlan:{parameter:'window',values:[4,6],trainDays:20,testDays:5}});
    }
    return apiResponse([{id:14,state:'DONE',created:'2026-11-28T01:00',plan:{parameter:'window',values:[4,6],trainDays:15,testDays:5},
      result:{baseline:6,capital:10000,wins:0,meanSpread:0,coverage:{closeDays:20,spans:1,tailDays:0},folds:[{
        trainStart:'2026-10-08',trainEnd:'2026-10-28',testStart:'2026-10-29',testEnd:'2026-11-04',selected:6,
        spread:0,candidates:[{...metrics,value:4},{...metrics,value:6,net:.01}],validation:metrics,baseline:metrics}]} }]);
  }));
  render(<WalkForward version={9} recipe={defaultRecipe}/>);
  expect(await screen.findByRole('heading',{name:'训练期参数热力图'})).toBeVisible();
  expect(within(screen.getByRole('heading',{name:'逐轮验证收益'}).closest('section')!).getByText('2026-10-29 — 11-04')).toBeVisible();
  expect(posts).toEqual([]);
  await userEvent.click(screen.getByRole('button',{name:'解读本次验证'}));
  expect(await screen.findByText('本轮持平',{exact:false})).toBeVisible();
  await userEvent.click(screen.getByRole('button',{name:'填入 20 日训练方案'}));
  expect(screen.getByLabelText('训练天数')).toHaveValue(20);
  expect(posts).toHaveLength(1);
  expect(posts[0]).toMatch(/walk-forward\/9\/14\/teach$/);
});

test('submits one bounded study and keeps retries on the same key',async ()=>{
  const posts:RequestInit[]=[];
  vi.stubGlobal('fetch',vi.fn(async (_url: unknown,init?:RequestInit)=>{
    if (init?.method==='POST') { posts.push(init);return apiResponse({jobId:42}); }
    return apiResponse([]);
  }));
  render(<WalkForward version={9} recipe={defaultRecipe}/>);
  const input=await screen.findByLabelText('候选值');
  await userEvent.clear(input);
  await userEvent.type(input,'6,6');
  const run=screen.getByRole('button',{name:'运行滚动验证'});
  await userEvent.click(run);
  expect(await screen.findByRole('alert')).toHaveTextContent('不同的数值');
  expect(posts).toEqual([]);
  await userEvent.clear(input);
  await userEvent.type(input,'4,6,8');
  await userEvent.click(run);
  await userEvent.click(run);
  expect(posts).toHaveLength(2);
  expect(posts[0].headers).toEqual(posts[1].headers);
  expect(JSON.parse(String(posts[0].body))).toEqual({parameter:'window',values:[4,6,8],trainDays:15,testDays:5});
});
