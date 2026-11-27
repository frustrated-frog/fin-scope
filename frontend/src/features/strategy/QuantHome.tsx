import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import { lifecycleLabel, PaperTrace, type PaperVersion } from './PaperLab';
import { classicExperimentType, type ClassicRecord } from './ClassicStrategies';
import './QuantHome.css';

interface Snapshot {
  history: ClassicRecord & {curve:{at:string; equity:number; benchmark:number; control?:number}[]; model?:unknown};
}
const percent = (value:number) => (value > 0 ? '+' : '')+(value*100).toFixed(2)+'%';
const amount = (value:number) => value.toLocaleString('zh-CN',{minimumFractionDigits:2, maximumFractionDigits:2});

export function QuantHome({ catalog, dailyCount, dailyStrategies, onBuild, onFactors, onAI, onAccount, onVersion, onLab }: {
  catalog:{rows:PaperVersion[]; loading:boolean; error:string; refresh:()=>void}; dailyCount:number; dailyStrategies:number;
  onBuild:()=>void; onFactors:()=>void; onAI:()=>void; onAccount:(id:number)=>void; onVersion:(id:number)=>void; onLab:()=>void;
}) {
  const [selected, select] = useState<number>();
  const [resource,setResource] = useState<{id:number; data?:Snapshot; error?:string}>();
  const [retry,setRetry] = useState(0);
  const version = catalog.rows.find(v=>v.id===selected) ?? catalog.rows[0];
  const accounts = catalog.rows.filter(v=>v.started && lifecycleLabel(v)==='模拟中');
  useEffect(()=>{
    if (!version) { return; }
    let alive=true;
    const id=version.id;
    api<Snapshot>('/api/quant/paper/versions/'+id).then(data=>{
      if (alive) { setResource({id,data}); }
    }).catch(()=>{
      if (alive) { setResource({id,error:'回测结果读取失败'}); }
    });
    return ()=>{alive=false;};
  },[version?.id,retry]);
  const result=resource?.id===version?.id ? resource : undefined;
  const h=result?.data?.history;
  const performance=h?.performance;
  const comparison=h?.comparison;
  const recipe=version?.spec.rule.kind==='python' && version.spec.rule.recipe;
  return <main className="qh-home">
    <header className="qh-title"><div><small>FINSCOPE / QUANT</small><h3>量化研究总览</h3></div>
      <dl>{[['策略版本',catalog.rows.length+dailyStrategies],['历史实验',catalog.rows.length+dailyCount],['运行账户',accounts.length]].map(([label,count])=>
        <div key={label}><dt>{label}</dt><dd>{catalog.loading || catalog.error ? '—' : count}</dd></div>)}</dl></header>
    {catalog.error && <p role="alert">{catalog.error} <button onClick={catalog.refresh}>重新读取</button></p>}
    <div className="qh-layout"><section className="qh-focus">
      <header><h4>实验表现</h4>{version && <select aria-label="切换回测实验" value={version.id} onChange={e=>select(Number(e.target.value))}>
        {catalog.rows.map(v=><option key={v.id} value={v.id}>#{v.id} · {v.spec.rule.name}</option>)}</select>}</header>
      {version ? <><h5>{version.spec.rule.name}</h5><small>{recipe ? classicExperimentType(recipe) : '盘中策略'} · 实验 #{version.id}
        {h?.curve?.length ? ' · '+h.curve[0].at.slice(0,10)+' 至 '+h.curve[h.curve.length-1].at.slice(0,10) : ''}</small>
        {h ? <><div className="qh-returns"><div><small>扣费收益</small><strong>{performance ? percent(performance.netReturn) : h.validationReturn!=null ? percent(h.validationReturn) : '—'}</strong></div>
          <div><small>日末最大回撤</small><b>{performance ? percent(-performance.maxDrawdown) : '—'}</b></div>
          <div><small>累计费用</small><b>¥ {amount(h.fees)}</b></div></div>
          <PaperTrace points={h.curve} capital={version.spec.capital} controlLabel={h.model ? '因子等权对照' : undefined}/>
          {comparison && performance && <div className="qh-compare"><b>同数据对照</b><span>相较 #{comparison.version}，收益差 {((performance.netReturn-comparison.netReturn)*100).toFixed(2)} 个百分点</span></div>}</>
          : result?.error ? <button onClick={()=>{setResource(undefined);setRetry(n=>n+1);}}>读取失败，重试</button> : <p role="status">正在读取净值…</p>}
        <button className="qh-link" onClick={()=>onVersion(version.id)}>打开回测分析 →</button></> :
        <p>{catalog.loading ? '正在读取实验…' : '创建一次研究后，在这里查看回测曲线。'}</p>}
    </section><aside><section className="qh-tools"><h4>发起研究</h4>
      <button onClick={onBuild}><b>构建策略</b><span>股票池、交易信号与仓位</span><i>↗</i></button>
      <button onClick={onFactors}><b>研究因子</b><span>查看公式与历史诊断</span><i>↗</i></button>
      <button onClick={onAI}><b>让 AI 写策略</b><span>从一个想法开始回测</span><i>↗</i></button></section>
      <section className="qh-accounts"><h4>运行中的组合</h4>{accounts.map(v=><button key={v.id} onClick={()=>onAccount(v.id)}>
        <span><b>{v.spec.rule.name}</b><small>#{v.id} · 模拟累计收益</small></span>
        <strong>{v.book && v.spec.capital>0 ? percent(v.book.equity/v.spec.capital-1) : '—'}</strong></button>)}
        {!accounts.length && <p>{catalog.loading ? '正在读取账户…' : catalog.error ? '账户数据暂不可用' : '暂无运行账户'}</p>}</section></aside></div>
    <footer><span>研究记录与行情档案</span><button className="qh-link" onClick={onLab}>进入实验室 →</button></footer>
  </main>;
}
