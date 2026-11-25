import { render,screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { FactorAssets } from './PaperArchive';

test('opens an immutable factor version and passes its identifier into a new experiment',async()=>{
  const user=userEvent.setup();
  const id='a'.repeat(64);
  vi.stubGlobal('fetch',vi.fn(async url => String(url).endsWith('/factors') ?
    apiResponse([{id,name:'低波动',version:2,studies:1,uses:0}]) :
    apiResponse({id,name:'低波动',version:2,source:'def factors(bars): ...',description:'滚动波动率',
      references:[],studies:[{job:3,candidate:1,strategy:'波动率实验',knowledgeThrough:'2026-10-01T15:00:00+08:00',report:{horizons:{},correlations:{}}}]})));
  const use=vi.fn();
  render(<FactorAssets onUse={use}/>);
  await user.click(await screen.findByRole('button',{name:/低波动 · v2/}));
  await user.click(await screen.findByRole('button',{name:'引用此版本创建实验'}));
  expect(use).toHaveBeenCalledWith(id,'低波动');
});
