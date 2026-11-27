import {Fragment, useEffect, useState} from 'react';
import {api} from '../../shared/api/client';
import type {PaperVersion} from './PaperLab';
import './ReturnAttribution.css';

interface Ledger {
  id:number; name:string; capital:number; first:string; last:string; net:number; benchmark:number;
  frequency:string|null; fees:number|null; fills:number|null; slippage:number|null; commission:number|null; tax:number|null;
}
interface Bridge {net:number; holdings:number; fees:number; slippage:number|null; points:number}
interface Stock {net:number; shares:number; value:number; fees:number; dividends:number; fills:number}
interface Contribution {code:string; name:string|null; current:Stock|null; base:Stock|null; gap:number|null}
interface Day {day:string; at:string; net:number; base:number|null; gap:number|null}
interface Report {current:Ledger; base:Ledger|null; paired:boolean; bridge:Bridge|null; days:Day[]; stocks:Contribution[]; stockStatus:string; residual:number|null}
interface Lesson {model:string; observation:string; theory:string; calculation:string; limits:string; hypothesis:string; criterion:string}

const frequency:Record<string,string>={DAILY:'日频',WEEKLY:'周频',MONTHLY:'月频'};
const number = new Intl.NumberFormat('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2});
const amt=(n:number|null|undefined,sign=false)=>n==null?'—':(sign&&n>0?'+':'')+number.format(n);
const label=(leg:Ledger)=> (frequency[leg.frequency??''] ?? leg.name)+' #'+leg.id;
const errorMessage=(error:unknown)=>error instanceof Error ? error.message : '暂时无法读取，请重试';

function ReturnBridge({report}:{report:Report}) {
  const [unit,setUnit]=useState<'元'|'百分点'>('元');
  const bridge=report.bridge;
  if (!bridge) {
    return null;
  }
  const components: [string,number][] = [[bridge.slippage===null?'含滑点的持仓损益差':'成本前持仓损益差',bridge.holdings],
    ['佣金与印花税差',bridge.fees]];
  if (bridge.slippage!==null) {
    components.push(['模拟滑点差',bridge.slippage]);
  }
  let running=0;
  const bars=components.map(([name,value])=>{
    const from=running;
    running+=value;
    return {name,value,from,to:running,total:false};
  });
  bars.push({name:'最终净差',value:bridge.net,from:0,to:bridge.net,total:true});
  const max=Math.max(0,...bars.map(bar=>bar.to)), min=Math.min(0,...bars.map(bar=>bar.to));
  const y=(value:number)=>12+(max-value)/(max-min||1)*76;
  const converted=(value:number)=>amt(unit==='元'?value:value*100/report.current.capital,true);
  return <section className="att-card att-bridge">
    <header><h4>收益差从哪里来</h4><div className="att-switch" aria-label="贡献单位">{(['元','百分点'] as const).map(u=>
      <button key={u} aria-pressed={unit===u} onClick={()=>setUnit(u)}>{u}</button>)}</div></header>
    <p>沿虚线累加各项贡献，得到 {label(report.current)} 相对 {label(report.base!)} 的净差。</p>
    <div className="att-waterfall" role="img" aria-label={bars.map(b=>b.name+' '+converted(b.value)+unit).join('，')}>
      <div className="att-zero" style={{top:y(0)+'%'}}><span>0</span></div>
      {bars.map(b=><div key={b.name} className="att-column" data-tone={b.total?'total':b.value<0?'down':'up'}>
        <i className="att-block" style={{top:Math.min(y(b.from),y(b.to))+'%',height:'max(2px,'+Math.abs(y(b.from)-y(b.to))+'%)'}}/>
        {!b.total && <i className="att-connector" style={{top:y(b.to)+'%'}}/>}
        <b style={{[b.value<0?'top':'bottom']:(b.value<0 ? y(b.to)+2 : 102-y(b.to))+'%'}}>{converted(b.value)}</b>
      </div>)}</div>
    <div className="att-axis">{bars.map(b=><span key={b.name}>{b.name}</span>)}</div>
    <small>本金 ¥ {amt(report.current.capital)} · {bridge.slippage===null?'原账本缺少独立滑点金额，滑点已计入持仓损益。':'滑点已包含在成交价中，图中单独列出一次。'}</small>
  </section>;
}

function StudyLedger({r}:{r:Report}) {
  const cols = r.paired && r.base ? [r.base,r.current] : [r.current];
  return <section className="att-card att-ledger"><header><h4>账本核对</h4><span>历史回测</span></header>
    <table><thead><tr><th>相同口径</th>{cols.map(l=><th key={l.id}>{label(l)}</th>)}</tr></thead><tbody>
      {([['净收益',(l:Ledger)=>amt(100*l.net/l.capital,true)+'%'],['股票池等权基准',(l:Ledger)=>amt(100*l.benchmark/l.capital,true)+'%'],
         ['实际成交',(l:Ledger)=>l.fills==null?'—':l.fills+' 笔'],['佣金与印花税',(l:Ledger)=>amt(l.fees)],
         ['模拟滑点',(l:Ledger)=>amt(l.slippage)]] as const).map(([name,format])=><tr key={name}>
           <th>{name}</th>{cols.map(l=><td key={l.id}>{format(l)}</td>)}</tr>)}
    </tbody></table><small>费用与滑点单位为元。基准沿用实验冻结时的股票池。</small>
  </section>;
}

function ContributionTable({r}:{r:Report}) {
  const [view,setView]=useState<'股票'|'交易日'>('股票'),[all,setAll]=useState(false);
  const [expanded,setExpanded]=useState<string>();
  const days=[...r.days].sort((a,b)=>Math.abs(b.gap??b.net)-Math.abs(a.gap??a.net));
  const total=view==='股票'?r.stocks.length:days.length;
  const visible=all?total:5;
  return <section className="att-card att-sources"><header><h4>损益贡献明细</h4><div className="att-switch">{(['股票','交易日'] as const).map(v=>
    <button key={v} aria-pressed={v===view} onClick={()=>{setView(v);setAll(false);}}>{v}</button>)}</div></header>
    <p>{view==='股票'?'按成交、期末持仓市值和已入账权益计算。':'按前后两个观测日末的净值变化计算，首日从本金起算。'}
      按{r.paired?'两组差额':'净损益'}绝对值排序，金额单位为元。</p>
    {view==='股票' && r.stockStatus ? <p role="status">{r.stockStatus}</p> : total===0 ? <p>本实验没有可展示的记录。</p> :
    <div className="att-scroll"><table><thead><tr><th>{view==='股票'?'股票名称':'观测日期'}</th>
      {r.paired && r.base && <th>{label(r.base)}</th>}<th>{label(r.current)}</th>{r.paired && <th>净差额</th>}
      {view==='股票'&&<th>期末持股{r.paired?'（原 → 本次）':''}</th>}</tr></thead><tbody>
      {view==='股票'?r.stocks.slice(0,visible).map(s=><Fragment key={s.code}><tr><th><button className="att-stock" aria-expanded={expanded===s.code}
          onClick={()=>setExpanded(expanded===s.code?undefined:s.code)}>{s.name||'待补名称'}<i aria-hidden="true">›</i></button></th>
        {r.paired && <td>{amt(s.base?.net??0,true)}</td>}<td>{amt(s.current?.net??0,true)}</td>
        {r.paired&&<td data-value={Math.sign(s.gap??0)}>{amt(s.gap,true)}</td>}
        <td>{r.paired?(s.base?.shares??0)+' → ':''}{s.current?.shares??0} 股</td></tr>
        {expanded===s.code && <tr><td colSpan={r.paired?5:3}><div className="att-stock-detail">
          {(r.base && r.paired ? [[r.base,s.base],[r.current,s.current]] as const : [[r.current,s.current]] as const).map(([leg,stock])=>
            <div key={leg.id}><strong>{label(leg)}</strong>{stock?<><span>成交 {stock.fills} 笔</span><span>费用 ¥ {amt(stock.fees)}</span>
              <span>期末市值 ¥ {amt(stock.value)}</span><span>应收股息 ¥ {amt(stock.dividends)}</span></>:<span>本组无成交</span>}</div>)}
        </div></td></tr>}</Fragment>) :
      days.slice(0,visible).map(d=><tr key={d.day}><th>{d.day}<small>{d.at.slice(11,16)==='15:00'?'收盘':'截至 '+d.at.slice(11,16)}</small></th>
        {r.paired&&<td>{amt(d.base,true)}</td>}<td>{amt(d.net,true)}</td>
        {r.paired&&<td data-value={Math.sign(d.gap??0)}>{amt(d.gap,true)}</td>}</tr>)}
    </tbody></table></div>}
    <footer>{view==='股票' && r.residual!=null && <small>逐股净损益已含交易费用 · 核对尾差 ¥ {amt(r.residual)}</small>}
      {total>5 && <button className="att-link" onClick={()=>setAll(v=>!v)}>{all?'收起明细':'查看全部 '+total+' 项'}</button>}</footer>
  </section>;
}

function Tutor({report,onCompare}:{report:Report;onCompare:(question?:string)=>void}) {
  const [lesson,setLesson]=useState<Lesson>(),[busy,setBusy]=useState(false),[error,setError]=useState('');
  async function teach(){
    setBusy(true);setError('');
    try {
      setLesson(await api<Lesson>('/api/quant/paper/versions/attribution/'+report.current.id+'/teach',{method:'POST'}));
    } catch(e){
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return <section className="att-card att-tutor"><header><h4>AI 研究助教</h4>{lesson&&<span>{lesson.model}</span>}</header>
    {lesson ? <><dl>{([['观察',lesson.observation],['原理',lesson.theory],['算一遍',lesson.calculation],['证据边界',lesson.limits]]).map(([k,v])=><div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl>
      <div className="att-proposal"><b>下一组实验</b><p>{lesson.hypothesis}</p><small>验证标准 · {lesson.criterion}</small>
        <button onClick={()=>onCompare(lesson.hypothesis+'\n验证标准：'+lesson.criterion)}>用这个问题设计对照 →</button></div></>
      : <p>让助教结合这份账本讲清换手率、交易成本和持仓收益，再提出一个可以检验的问题。</p>}
    <button className="att-teach" onClick={teach} disabled={busy}>{busy?'正在结合账本讲解…':lesson?'重新讲解':'用这组数据教我'}</button>
    {error&&<p role="alert">{error}</p>}</section>;
}

export function ReturnAttribution({version,onCompare}:{version:PaperVersion;onCompare:(version:PaperVersion,question?:string)=>void}) {
  const [result,setResult]=useState<Report>(),[error,setError]=useState(''),[retry,setRetry]=useState(0);
  useEffect(()=>{
    const controller=new AbortController();
    setResult(undefined);setError('');
    api<Report>('/api/quant/paper/versions/attribution/'+version.id,{signal:controller.signal})
      .then(data=>{
        if (!data?.current || data.current.id!==version.id) {
          throw new Error('没有读到该版本的归因账本');
        }
        if (!controller.signal.aborted) {
          setResult(data);
        }
      }).catch(e=>{
        if (!controller.signal.aborted) {
          setError(errorMessage(e));
        }
      });
    return ()=>controller.abort();
  },[version.id,retry]);
  if (!result || result.current.id!==version.id) {
    return <section className="att-terminal"><p role={error?'alert':'status'}>{error||'正在核对冻结成交与行情…'}</p>
      {error&&<button onClick={()=>setRetry(v=>v+1)}>重新读取</button>}</section>;
  }
  const r=result, b=r.bridge;
  const savings=b && b.slippage!=null ? b.fees+b.slippage : null;
  const insight=b && savings!=null && savings>0 && b.holdings<0 && b.net>0
    ? '节省的交易成本覆盖了持仓损益的落差'+(r.base && r.current.net<0 && r.base.net<0 ? '，两组在这段回测里均亏损。' : '。')
    : r.paired ? '相同本金、行情快照、股票池和撮合引擎。' : r.base ? '两次实验口径有差异，仅展示本版本账本。' : '从净值、逐笔成交与行情核对收益来源。';
  return <div className="att-terminal"><header className="att-intro"><div>
    <div className="att-context"><i/>{r.current.first.slice(0,10)} — {r.current.last.slice(0,10)} · 冻结回测</div>
    <h3>{r.paired && r.base ? label(r.current)+' 与 '+label(r.base) : r.current.name}</h3>
    <p>{insight}</p></div>
    <div className="att-head-result" data-loss={(b?b.net:r.current.net)<0}><span>{b?'扣费收益差':'扣费收益'}</span><strong>{amt(b?b.points:100*r.current.net/r.current.capital,true)}
      <small>{b?'个百分点':'%'}</small></strong></div></header>
    <div className="att-evidence-grid">{b&&<ReturnBridge report={r}/>}<StudyLedger r={r}/></div>
    <div className="att-detail-grid"><ContributionTable r={r}/><Tutor key={version.id} report={r} onCompare={q=>onCompare(version,q)}/></div>
  </div>;
}
