import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {expect, test, vi} from 'vitest';
import {apiResponse} from '../../test/apiEnvelope';
import {ReturnAttribution} from './ReturnAttribution';
import {PlanReport} from './ExecutionReport';
import type {PaperVersion} from './PaperLab';

const version={id:9} as PaperVersion;
const ledger={id:9,name:'趋势',capital:10000,first:'2026-11-24T14:45',last:'2026-11-25T10:00',
  net:-300,benchmark:-200,frequency:'WEEKLY',fees:20,fills:4,slippage:1};
const report={current:ledger,base:{...ledger,id:8,frequency:'DAILY',net:-400},paired:true,
  bridge:{net:100,holdings:-60,fees:150,slippage:10,points:1},stockStatus:'',residual:0,
  days:[{day:'2026-11-25',at:ledger.last,net:12,base:2,gap:10}],
  stocks:[{code:'600123',name:'样本股份',base:null,current:{net:100,shares:100,fills:4,value:800,fees:20,dividends:0},gap:100}]};

test('explains a frozen comparison in currency and points and carries the selected hypothesis into the next experiment', async ()=>{
  const posts:string[]=[];
  vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
    if (init?.method==='POST') {
      posts.push(String(input));
      return apiResponse({model:'Luna',observation:'周频成交较少',theory:'换手产生费用',calculation:'账本核算',limits:'区间有限',hypothesis:'调整每笔仓位',criterion:'固定股票池与费用'});
    }
    return apiResponse(report);
  }));
  const compare=vi.fn(),user=userEvent.setup();
  render(<ReturnAttribution version={version} onCompare={compare}/>);
  expect(await screen.findByRole('img',{name:/最终净差 \+100.00元/})).toBeVisible();
  await user.click(screen.getByRole('button',{name:'百分点'}));
  expect(screen.getByRole('img',{name:/最终净差 \+1.00百分点/})).toBeVisible();
  await user.click(screen.getByRole('button',{name:'样本股份'}));
  expect(screen.getByText('本组无成交')).toBeVisible();
  expect(screen.queryByText('600123')).toBeNull();
  await user.click(screen.getByRole('button',{name:'交易日'}));
  expect(screen.getByText('截至 10:00')).toBeVisible();
  expect(posts).toEqual([]);
  await user.click(screen.getByRole('button',{name:'用这组数据教我'}));
  await user.click(await screen.findByRole('button',{name:/用这个问题设计对照/}));
  expect(compare).toHaveBeenCalledWith(version,'调整每笔仓位\n验证标准：固定股票池与费用');
  expect(posts).toEqual(['/api/quant/paper/versions/attribution/9/teach']);
});

test('shows standalone or incomplete histories without invented comparative attribution',async()=>{
  vi.stubGlobal('fetch',vi.fn(async()=>apiResponse({...report,base:null,paired:false,bridge:null,stocks:[],stockStatus:'该版本没有归档逐笔成交'})));
  render(<ReturnAttribution version={version} onCompare={vi.fn()}/>);
  expect(await screen.findByText('该版本没有归档逐笔成交')).toBeVisible();
  expect(screen.queryByRole('heading',{name:'收益差从哪里来'})).toBeNull();
  expect(screen.queryByText('净差额')).toBeNull();
});

test('compares original, risk adjusted and actual shares and marks an unrecorded target', async()=>{
  const plan={at:'2026-11-25T14:45',updated:'2026-11-25T14:50',expires:'2026-11-26T14:45',status:'进行中',fills:0,fees:0,
    legs:{A:{weight:0.3,target:0,originalTarget:300,actual:100,remaining:-100,reason:'T+1 待解冻'},B:{weight:0,target:0,actual:0}}};
  const {rerender}=render(<PlanReport visual name={c=>c} plan={plan}/>);
  const chart=within(screen.getByRole('region',{name:'调仓股数对照'}));
  expect(chart.getByText('300 股')).toBeVisible();
  expect(chart.getByText('100 股')).toBeVisible();
  expect(chart.getByText('0 股')).toBeVisible();
  expect(chart.getByText('T+1 待解冻')).toBeVisible();
  rerender(<PlanReport visual name={c=>c} plan={{...plan,source:'盘中风控'}}/>);
  expect(chart.getByText('未记录')).toBeVisible();
});
