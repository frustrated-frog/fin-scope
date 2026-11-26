import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { CompanySearchResult } from '../financials/financialTypes';
import { stock, type Instrument } from './markets';

const suggestions=[stock('NVDA','英伟达'),stock('AAPL','苹果'),stock('MSFT','微软'),stock('TSLA','特斯拉'),stock('GOOGL','谷歌'),stock('AMZN','亚马逊'),stock('META','Meta')];
export function StockPicker({onAdd}:{onAdd:(asset:Instrument)=>Promise<void>}) {
  const [query,setQuery]=useState('');
  const [remote,setRemote]=useState<Instrument[]>([]);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const term=query.trim();
  useEffect(()=>{
    let active=true;
    setRemote([]);
    if (term.length<2) { return; }
    const timer=window.setTimeout(()=>{
      api<CompanySearchResult[]>('/api/companies/search?q='+encodeURIComponent(term)+'&limit=8').then(rows=>{
        if (!active) { return; }
        setRemote(rows.flatMap(c=>c.securities.filter(s=>s.market==='US' || /^(NASDAQ|NYSE|XNAS|XNYS|AMEX)$/i.test(s.exchange))
          .map(s=>stock(s.symbol.replace('.','-').toUpperCase(),c.displayName))));
      }).catch(()=>{ /* Local aliases and exact ticker lookup remain available. */ });
    },350);
    return ()=>{ active=false;window.clearTimeout(timer); };
  },[term]);
  const matches=suggestions.filter(a=>(a.name+' '+a.ticker).toLowerCase().includes(term.toLowerCase()));
  const exact=term.toUpperCase().replace('.','-');
  const choices=[...matches,...remote];
  if (/^[A-Z]{1,6}(-[A-Z])?$/.test(exact)) { choices.push(stock(exact,exact)); }
  async function add(asset:Instrument) {
    setBusy(true);setError('');
    try { await onAdd(asset);setQuery(''); }
    catch (e) { setError(e instanceof Error?e.message:'暂时无法添加'); }
    finally { setBusy(false); }
  }
  return <div className="gm-picker">
    <label><span>美股自选</span><input value={query} placeholder="公司名称或美股代码" aria-label="搜索美股" onChange={e=>{setQuery(e.target.value);setError('');}}/></label>
    {term && <div className="gm-search-results">{choices.filter((a,i)=>choices.findIndex(b=>a.id===b.id)===i).slice(0,8).map(a=>
      <button key={a.id} disabled={busy} onClick={()=>void add(a)}>{a.name} <small>{a.ticker}</small></button>)}
      {!choices.length && <small>可输入完整美股代码查询</small>}</div>}
    {busy && <small role="status">正在核对行情…</small>}{error && <small role="alert">{error}</small>}
  </div>;
}
