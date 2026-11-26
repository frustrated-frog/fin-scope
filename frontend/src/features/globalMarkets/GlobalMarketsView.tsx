import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { api } from '../../shared/api/client';
import { IntradayChart } from './IntradayChart';
import { MarketChart } from './MarketChart';
import { StockPicker } from './StockPicker';
import { chartLines, core, price, signed, stamp, stock, type Instrument, type Snapshot } from './markets';
import './globalMarkets.css';

const key='finscope.global-markets.watchlist.v1';
function loadSaved(): Instrument[] {
  try {
    const rows:unknown=JSON.parse(localStorage.getItem(key) ?? '[]');
    if (!Array.isArray(rows)) { return []; }
    return rows.filter(r=>r && typeof r.name==='string' && typeof r.ticker==='string' && /^[A-Z]{1,6}(-[A-Z])?$/.test(r.ticker))
      .map(r=>stock(r.ticker,r.name));
  } catch { return []; }
}
function Spark({values}:{values:number[]}) {
  if(values.length<2) { return null; }
  const min=Math.min(...values), range=Math.max(...values)-min || 1;
  return <svg className="gm-spark" viewBox="0 0 68 25" aria-hidden="true"><polyline points={values.map((v,i)=>`${i*68/(values.length-1)},${23-(v-min)*21/range}`).join(' ')} fill="none" stroke="currentColor" strokeWidth="1.5"/></svg>;
}
export function GlobalMarketsView({refreshRevision=0}:{refreshRevision?:number}) {
  const [saved,setSaved]=useState(loadSaved);
  const assets=useMemo(()=>[...core,...saved],[saved]);
  const [selected,setSelected]=useState('SPX');
  const [mode,setMode]=useState<'day'|'compare'|'intraday'>('day');
  const compare=mode==='compare';
  const [basket,setBasket]=useState(['SPX','XAU','BTC']);
  const [days,setDays]=useState(30);
  const [quotes,setQuotes]=useState<Record<string,Snapshot>>({});
  const [errors,setErrors]=useState<Record<string,string>>({});
  const [revision,refresh]=useState(0);
  const [loading,setLoading]=useState(false);
  const [saveError,setSaveError]=useState('');
  useEffect(()=>{
    const controller=new AbortController();
    setLoading(true);
    Promise.all(assets.map(async a=>{
      try {
        const quote=await api<Snapshot>('/api/global-markets/'+a.id,{signal:controller.signal});
        if (!controller.signal.aborted) {
          setQuotes(prev=>({...prev,[a.id]:quote}));
          setErrors(prev=>({...prev,[a.id]:''}));
        }
      } catch(e) {
        if (!controller.signal.aborted) { setErrors(prev=>({...prev,[a.id]:e instanceof Error?e.message:'读取失败'})); }
      }
    })).finally(()=>{if (!controller.signal.aborted) { setLoading(false); }});
    const timer=window.setTimeout(()=>refresh(r=>r+1),120000);
    return ()=>{controller.abort();window.clearTimeout(timer);};
  },[assets,revision,refreshRevision]);
  const asset=assets.find(a=>a.id===selected) ?? core[0];
  const q=quotes[asset.id], series=q?.series;
  const pct=series ? (series.price/series.reference-1)*100 : undefined;
  const plotted=compare?assets.filter(a=>basket.includes(a.id)):[asset];
  const lines=useMemo(()=>chartLines(compare?assets.filter(a=>basket.includes(a.id)):[asset],quotes,days,compare),[assets,basket,asset,quotes,days,compare]);
  function persist(next:Instrument[]) {
    try {localStorage.setItem(key,JSON.stringify(next));setSaveError('');}
    catch {setSaveError('浏览器无法保存自选，本次会话仍可使用。');}
    setSaved(next);
  }
  async function add(a:Instrument) {
    if (!saved.some(item=>item.id===a.id)) {
      const quote=await api<Snapshot>('/api/global-markets/'+a.id);
      setQuotes(prev=>({...prev,[a.id]:quote}));
      persist([...saved,a]);
    }
    setSelected(a.id);if (compare) {setMode('day');}
  }
  return <section className="gm" aria-label="全球行情" style={{'--gm-accent':asset.color} as CSSProperties}>
    <aside className="gm-rail"><header><h3>市场一览</h3><button className="gm-refresh" onClick={()=>refresh(r=>r+1)} disabled={loading} aria-label="刷新全球行情">{loading?'更新中':'刷新'} <span aria-hidden="true">↻</span></button></header>
      {Array.from(new Set(assets.map(a=>a.group))).map(group=><section className="gm-group" key={group}><h4>{group}</h4>
        {assets.filter(a=>a.group===group).map(a=>{
          const s=quotes[a.id]?.series, change=s ? (s.price/s.reference-1)*100 : undefined;
          return <button key={a.id} aria-pressed={a.id===asset.id} className="gm-item" style={{'--gm-accent':a.color} as CSSProperties} onClick={()=>{setSelected(a.id);if (compare) {setMode('day');}}}>
            <span><b>{a.name}</b><small>{a.ticker}{(quotes[a.id]?.fallback || errors[a.id] && quotes[a.id]) && ' · 缓存'}</small></span>
            {s && <Spark values={Object.values(s.closes).slice(-20)}/>}
            <span className="gm-item-quote"><b>{s?price(s.price):'—'}</b><small className={change===undefined?'':change>=0?'gm-up':'gm-down'}>{change!==undefined?signed(change)+'%':errors[a.id]?'暂不可用':'读取中'}</small></span>
          </button>;
        })}</section>)}
      <StockPicker onAdd={add}/>{saveError && <small role="alert">{saveError}</small>}
    </aside>
    <article className="gm-main">
      <header className="gm-summary"><div className="gm-ticker"><i/>{asset.ticker}</div><h2>{asset.name}</h2>
        <div className="gm-price">{series?price(series.price):'—'} <small>{asset.unit}</small></div>
        <div className="gm-delta">{series && <strong className={series.price>=series.reference?'gm-up':'gm-down'}>{signed(series.price-series.reference)}　{signed(pct!)}%</strong>}
          <span>{asset.group==='加密货币'?'近 24 小时':asset.id==='XAU'?'较报价源参考价':'较前收盘'}{series && ' · '+price(series.reference)}</span></div>
        <div className="gm-provenance">{series && <><b>{series.marketStatus ?? '最近报价'}</b>
          <span>{series.source}</span><time>报价 {stamp(series.asOf)} 北京时间</time></>}
          {(q?.fallback || (errors[asset.id] && q)) && <em>刷新失败，显示上次快照</em>}
          {!series && errors[asset.id] && <span role="alert">{errors[asset.id]}</span>}
        </div>
        {asset.group==='美股自选' && <button className="gm-remove" onClick={()=>{persist(saved.filter(a=>a.id!==asset.id));setSelected('SPX');setBasket(b=>b.filter(id=>id!==asset.id));}}>移出自选</button>}
      </header>
      <div className="gm-chart-head"><div className="gm-modes" aria-label="图表模式"><button aria-pressed={mode==='intraday'} onClick={()=>setMode('intraday')}>分时</button>
        <button aria-pressed={mode==='day'} onClick={()=>setMode('day')}>日线走势</button>
        <button aria-pressed={compare} onClick={()=>{if(!compare){setBasket([...new Set([asset.id,'SPX','XAU','BTC'])]);}setMode('compare');}}>跨市场对比</button></div>
        {mode!=='intraday' && <div className="gm-periods" aria-label="显示区间">{[7,30,90,180].map(d=><button aria-pressed={days===d} key={d} onClick={()=>setDays(d)}>{d} 天</button>)}</div>}</div>
      {compare && <div className="gm-basket">{assets.map((a,i)=><button key={a.id} aria-pressed={basket.includes(a.id)} disabled={basket.length===1 && basket[0]===a.id}
        onClick={()=>setBasket(b=>b.includes(a.id)?b.filter(id=>id!==a.id):[...b,a.id])}><i style={{background:a.color}}/>{a.group==='美股指数'?['标普','道指','纳指'][i]:a.name}</button>)}</div>}
      {compare && plotted.some(a=>!quotes[a.id]) && <p className="gm-note" role="status">等待行情 · {plotted.filter(a=>!quotes[a.id]).map(a=>a.name).join('、')}{plotted.some(a=>errors[a.id])?'（部分数据暂不可用）':''}</p>}
      {mode==='intraday' ? <IntradayChart key={asset.id} asset={asset} revision={revision+refreshRevision}/> : <>
      {lines.length ? <MarketChart key={asset.id+'-'+compare+'-'+days} lines={lines} percent={compare}/> :
        <p className="gm-nochart">{loading?'正在读取历史日线…':'当前区间暂无可用日线'}</p>}
      <footer className="gm-foot">{compare?'从首个共同日期的收盘价起算。美股按纽约交易日、加密货币按 UTC 日线对齐。':asset.id==='XAU'?'日线取自伦敦金历史收盘序列，顶部涨跌使用报价源随行情提供的参考价。':asset.group==='加密货币'?'日线按 UTC 零点切分，顶部涨跌使用滚动 24 小时参考价。':'折线连接相邻交易日的收盘价。'}</footer></>}
    </article>
  </section>;
}
