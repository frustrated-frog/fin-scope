import {render,screen} from '@testing-library/react';
import {expect,test} from 'vitest';
import {ForwardPnl} from './ForwardPnl';

test('older paper observations show the observed account bridge and disclose missing position marks',()=>{
  render(<ForwardPnl value={{fromAt:null,toAt:'2026-11-27T14:50',opening:10000,closing:9985,net:-15,
    beforeFees:-10,fees:5,stocks:null,receivableChange:null,residual:null,missedBars:1}} name={()=>''}/>);
  expect(screen.getByText('-15.00')).toBeVisible();
  expect(screen.getByText('-10.00')).toBeVisible();
  expect(screen.getByText(/起止持仓市值记录不全/)).toBeVisible();
});
