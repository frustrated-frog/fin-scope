import { fireEvent, render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { ExperimentReturnChart } from './ExperimentReturnChart';

test('reads the original account at the same instant and uses each account’s initial capital',()=>{
  const points=[{at:'2026-11-23T09:35:00+08:00',equity:100,benchmark:100},
    {at:'2026-11-23T09:40:00+08:00',equity:105,benchmark:102}];
  render(<ExperimentReturnChart points={points} capital={100} name="周频"
    base={{capital:200,name:'日频',curve:[{at:'2026-11-23T01:40:00Z',equity:196,benchmark:204}]}}/>);
  const slider=screen.getByRole('slider');
  expect(slider.getAttribute('aria-valuetext')).toContain('周频 +5.00%，日频 -2.00%，股票池等权基准 +2.00%');
  fireEvent.change(slider,{target:{value:'0'}});
  expect(slider.getAttribute('aria-valuetext')).toContain('日频 缺数');
});
