import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import { FactorStudy, type Trial } from './PaperResearchEvidence';

const root = '/api/quant/paper/versions';
type Asset = {id:string; name:string; version:number; studies:number; uses:number};
type Observation = {job:number; candidate:number; knowledgeThrough?:string; strategy:string;
  report:Trial['metrics']['factors'][string]};
type Factor = Omit<Asset,'studies'> & {source:string; description:string; studies:Observation[];
  references:{job:number; candidate:number; strategy:string}[]};
type Dataset = {code:string;name:string;status:string;bars:number;measured:number;complete:number;lastDay?:string;checked?:string;error?:string};

function useArchive<T>(path:string | undefined, expectList=false) {
  const [data,setData] = useState<T>();
  const [error,setError] = useState('');
  const [revision,refresh] = useState(0);
  useEffect(() => {
    let active=true;
    setData(undefined);
    setError('');
    if (path) {
      api<T>(root+path).then(v => {
        if (expectList && !Array.isArray(v)) { throw new Error('档案列表格式异常'); }
        if (active) {setData(v);}
      })
        .catch(e => {if (active) {setError(e instanceof Error ? e.message : '读取失败');}});
    }
    return () => {active=false;};
  },[path,revision,expectList]);
  return {data,error,reload:() => refresh(v=>v+1)};
}

export function DatasetArchive() {
  const [open,setOpen] = useState(false);
  const {data,error,reload}=useArchive<Dataset[]>(open ? '/datasets' : undefined,true);
  const states:Record<string,string>={READY:'覆盖达标',PARTIAL:'部分覆盖',RETRY:'等待重试',SYNCING:'同步中',PENDING:'待同步'};
  return <details className="quant-readout paper-glass" onToggle={e=>setOpen(e.currentTarget.open)}>
    <summary>研究行情档案</summary>
    {open && <><p>按股票维护五分钟行情。完整交易日含 48 根 K 线，缺少真实成交量时暂停该根撮合。</p>
      <button className="quant-record-link" onClick={reload}>刷新覆盖</button>
      {error && <p role="alert">{error}</p>}
      {data?.length ? <div className="paper-coverage-scroll"><table className="paper-position-table">
        <thead><tr><th>股票</th><th>状态</th><th>完整日</th><th>真实量 / 总根数</th><th>行情截至</th></tr></thead>
        <tbody>{data.map(d=><tr key={d.code}><th>{d.name}</th><td>{states[d.status] ?? d.status}</td><td>{d.complete}</td>
          <td>{d.measured} / {d.bars}</td><td>{d.lastDay ?? '—'}</td></tr>)}</tbody></table></div>
        : !error && <p>{data ? '等待股票池同步。' : '正在读取覆盖…'}</p>}</>}
  </details>;
}

export function FactorAssets({onUse}:{onUse:(asset:string,name:string)=>void}) {
  const list=useArchive<Asset[]>('/factors',true);
  const [id,select]=useState('');
  const detail=useArchive<Factor>(id ? '/factors/'+id : undefined);
  const f=detail.data?.id===id ? detail.data : undefined;
  return <section className="quant-readout paper-glass"><header><h4>独立因子库</h4>
    <small>{list.data?.length ?? '—'} 个冻结版本</small></header>
    {list.error && <button className="quant-record-link" onClick={list.reload}>{list.error} · 重试</button>}
    {list.data?.length === 0 && <p>AI 完成首个独立因子评价后，这里会保留源码和开发证据，包括结束研究的候选。</p>}
    <div className="paper-asset-picker">{list.data?.map(a=><button className="quant-action" type="button" key={a.id}
      aria-pressed={id===a.id} onClick={()=>select(a.id===id ? '' : a.id)}>{a.name} · v{a.version}<small>　{a.studies} 次评价</small></button>)}</div>
    {detail.error && <button className="quant-record-link" onClick={detail.reload}>{detail.error} · 重试</button>}
    {f && <article className="paper-factor-study"><header><strong>{f.name} · v{f.version}</strong>
      <button className="quant-action" disabled={!f.studies.some(s => s.knowledgeThrough)} title={!f.studies.some(s => s.knowledgeThrough) ? '旧记录缺少信息截止日期，只提供历史查阅' : undefined} onClick={()=>onUse(f.id,f.name)}>引用此版本创建实验</button></header>
      <p>{f.description}</p><details><summary>因子实现源码</summary><pre>{f.source}</pre></details>
      {f.studies.map(s=><details key={s.job+'-'+s.candidate}><summary>{s.strategy} · 任务 #{s.job} / 候选 {s.candidate}</summary>
        <small>研究信息截至 {s.knowledgeThrough?.slice(0,16).replace('T',' ') ?? '旧记录未标注'}</small>
        <FactorStudy name={f.name} {...s.report}/></details>)}
      {!!f.references.length && <p>引用记录 · {f.references.map(r=>r.strategy+'（任务 #'+r.job+'，候选 '+r.candidate+'）').join('、')}</p>}
    </article>}
  </section>;
}
