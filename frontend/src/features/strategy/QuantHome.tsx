import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import { lifecycleLabel, type PaperVersion } from './PaperLab';
import { classicExperimentType, type ClassicRecord } from './ClassicStrategies';
import { ExperimentReturnChart, type ComparisonCurve, type EquityObservation } from './ExperimentReturnChart';
import './QuantHome.css';
import './ReturnAttribution.css';

interface Snapshot {
  history: ClassicRecord & {curve:EquityObservation[]; model?:unknown};
  spec:{capital:number};
}
const percent = (value:number) => (value > 0 ? '+' : '')+(value*100).toFixed(2)+'%';
const amount = (value:number) => value.toLocaleString('zh-CN',{minimumFractionDigits:2, maximumFractionDigits:2});

export function QuantHome({ catalog, dailyCount, dailyStrategies, onBuild, onFactors, onAI, onAccount, onVersion, onLab, onAttribution, onLearning }: {
  catalog:{rows:PaperVersion[]; loading:boolean; error:string; refresh:()=>void}; dailyCount:number; dailyStrategies:number;
  onBuild:()=>void; onFactors:()=>void; onAI:()=>void; onAccount:(id:number,day?:string)=>void; onAttribution:(id:number)=>void; onLearning:()=>void; onVersion:(id:number)=>void; onLab:()=>void;
}) {
  const [selected, select] = useState<number>();
  const [resource,setResource] = useState<{id:number; data?:Snapshot; error?:string}>();
  const [retry,setRetry] = useState(0);
  const version = catalog.rows.find(v=>v.id===selected) ?? catalog.rows.find(v=>v.comparisonId) ?? catalog.rows[0];
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
  const frequencyNames={DAILY:'日频',WEEKLY:'周频',MONTHLY:'月频'};
  const baseId=comparison?.version;
  const baseFrequency=comparison?.frequency;
  const baseLabel=baseFrequency ? frequencyNames[baseFrequency]+' #'+baseId : '原实验 #'+baseId;
  const currentLabel=recipe ? frequencyNames[recipe.rebalance]+' #'+version.id : '策略 #'+version?.id;
  const [comparisonResult,setComparisonResult]=useState<{id:number; curve?:ComparisonCurve; error?:boolean}>();
  const [baseRetry,retryBase]=useState(0);
  useEffect(()=>{
    if (baseId==null) { return; }
    const controller=new AbortController();
    api<Snapshot>('/api/quant/paper/versions/'+baseId,{signal:controller.signal}).then(data=>{
      if (controller.signal.aborted) { return; }
      if (!data.history.curve.length || !(data.spec.capital>0)) { throw new Error('原实验缺少曲线'); }
      setComparisonResult({id:baseId,curve:{curve:data.history.curve,capital:data.spec.capital,name:baseLabel}});
    }).catch(()=>{if (!controller.signal.aborted) { setComparisonResult({id:baseId,error:true}); }});
    return ()=>controller.abort();
  },[baseId,baseLabel,baseRetry]);
  const base=comparisonResult?.id===baseId ? comparisonResult : undefined;
  const net=performance?.netReturn ?? h?.validationReturn;
  const benchmark=h?.validationBenchmark;
  const gap=net!=null && comparison ? (net-comparison.netReturn)*100 : undefined;
  const feeDelta=h && comparison ? comparison.fees-h.fees : undefined;
  return <main className="qh-home">
    <header className="qh-title"><div><small>FINSCOPE / QUANT</small><h3>量化研究总览</h3></div>
      <dl>{[['策略版本',catalog.rows.length+dailyStrategies],['历史实验',catalog.rows.length+dailyCount],['运行账户',accounts.length]].map(([label,count])=>
        <div key={label}><dt>{label}</dt><dd>{catalog.loading || catalog.error ? '—' : count}</dd></div>)}</dl></header>
    {catalog.error && <p role="alert">{catalog.error} <button onClick={catalog.refresh}>重新读取</button></p>}
    <div className="qh-layout"><section className="qh-focus">
      <header><div><small>{comparison ? '本期研究焦点 · 同数据对照' : '实验表现'}</small><h4>{comparison ? '两组策略的收益差在哪里？' : version?.spec.rule.name ?? '回测实验'}</h4></div>
        {version && <label className="qh-switch">切换实验 ▾<select aria-label="切换回测实验" value={version.id} onChange={e=>select(Number(e.target.value))}>
          {catalog.rows.map(v=><option key={v.id} value={v.id}>#{v.id} · {v.spec.rule.name}</option>)}</select></label>}</header>
      {version ? <><small>{version.spec.rule.name} · {recipe ? classicExperimentType(recipe) : '盘中策略'} · 实验 #{version.id}
        {h?.curve?.length ? ' · '+h.curve[0].at.slice(0,10)+' 至 '+h.curve[h.curve.length-1].at.slice(0,10) : ''}</small>
        {h ? <>{comparison && gap!=null && feeDelta!=null && <p className="qh-verdict">
          {currentLabel} 较 {baseLabel} {feeDelta===0 ? '交易费用相同' : <>{feeDelta>0?'少付':'多付'} <b>¥ {amount(Math.abs(feeDelta))}</b> 交易费用</>}，
          {gap===0 ? '扣费收益相同' : <>扣费收益{gap>0?'高':'低'} <b>{Math.abs(gap).toFixed(2)} 个百分点</b></>}。
          {benchmark!=null && net!=null && net<benchmark && comparison.netReturn<benchmark && '两组均落后于基准。'}
        </p>}
          <button className="att-action" onClick={()=>onAttribution(version.id)}>{comparison ? '拆解两组收益差' : '查看收益归因'} →</button>
          <ExperimentReturnChart key={version.id} points={h.curve} capital={version.spec.capital} name={currentLabel} base={base?.curve}
            controlLabel={h.model ? '因子等权对照' : undefined}/>
          {baseId!=null && !base?.curve && <small role="status">{base?.error ?
            <button className="qh-link" onClick={()=>{setComparisonResult(undefined);retryBase(n=>n+1);}}>原实验曲线未载入，重试 ↻</button> : '正在读取原实验曲线…'}</small>}
          <dl className="qh-stats"><div><dt>扣费收益</dt><dd data-sign={net!=null ? Math.sign(net) : 0}>{net!=null ? percent(net) : '—'}</dd></div>
            <div><dt>日末最大回撤</dt><dd>{performance?.maxDrawdown!=null ? percent(-performance.maxDrawdown) : '—'}</dd></div>
            <div><dt>累计交易费用</dt><dd>¥ {amount(h.fees)}</dd></div></dl></>
          : result?.error ? <button onClick={()=>{setResource(undefined);setRetry(n=>n+1);}}>读取失败，重试</button> : <p role="status">正在读取净值…</p>}
        <button className="qh-link" onClick={()=>onVersion(version.id)}>打开回测分析 →</button></> :
        <p>{catalog.loading ? '正在读取实验…' : '创建一次研究后，在这里查看回测曲线。'}</p>}
    </section><aside><section className="qh-tools"><h4>发起研究</h4>
      <button onClick={onLearning}><b>量化学习台</b><span>每日检查、周期复盘与 AI 学习记录</span><i>↗</i></button>
      <button onClick={onBuild}><b>构建策略</b><span>股票池、交易信号与仓位</span><i>↗</i></button>
      <button onClick={onFactors}><b>研究因子</b><span>查看公式与历史诊断</span><i>↗</i></button>
      <button onClick={onAI}><b>让 AI 写策略</b><span>从一个想法开始回测</span><i>↗</i></button></section>
      <section className="qh-accounts"><h4>运行中的组合</h4>{accounts.map(v=><button key={v.id} onClick={()=>onAccount(v.id)}>
        <span><b>{v.spec.rule.name}</b><small>#{v.id} · 模拟累计收益</small></span>
        <strong data-sign={v.book ? Math.sign(v.book.equity-v.spec.capital) : 0}>{v.book && v.spec.capital>0 ? percent(v.book.equity/v.spec.capital-1) : '—'}</strong></button>)}
        {!accounts.length && <p>{catalog.loading ? '正在读取账户…' : catalog.error ? '账户数据暂不可用' : '暂无运行账户'}</p>}</section></aside></div>
    <footer><span>研究记录与行情档案</span><button className="qh-link" onClick={onLab}>进入实验室 →</button></footer>
  </main>;
}
