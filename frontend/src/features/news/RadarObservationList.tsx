import { useState } from 'react';
import { api } from '../../shared/api/client';
import type { RadarObservation } from './researchRadarTypes';

export function RadarObservationList({ eventId, items = [], onChange }: { eventId: number; items?: RadarObservation[]; onChange: (items: RadarObservation[]) => void }) {
  const [content,setContent]=useState(''); const [busy,setBusy]=useState(false);
  async function add(){if(!content.trim()||busy)return;setBusy(true);try{const item=await api<RadarObservation>(`/api/research-radar/events/${eventId}/observations`,{method:'POST',body:JSON.stringify({content:content.trim()})});onChange([...items,item]);setContent('');}finally{setBusy(false);}}
  async function toggle(item:RadarObservation){const next=await api<RadarObservation>(`/api/research-radar/events/${eventId}/observations/${item.id}`,{method:'PATCH',body:JSON.stringify({status:item.status==='OPEN'?'DONE':'OPEN'})});onChange(items.map((value)=>value.id===item.id?next:value));}
  async function remove(item:RadarObservation){await api(`/api/research-radar/events/${eventId}/observations/${item.id}`,{method:'DELETE'});onChange(items.filter((value)=>value.id!==item.id));}
  return <div className="radar-observations">
    <div className="radar-observation-compose"><input aria-label="新增观察项" value={content} maxLength={300} placeholder="补充一个需要验证的信号" onChange={(event)=>setContent(event.target.value)} onKeyDown={(event)=>{if(event.key==='Enter')void add();}}/><button type="button" disabled={busy||!content.trim()} onClick={()=>void add()}>添加</button></div>
    {items.length?<ul>{items.map((item)=><li key={item.id} className={item.status==='DONE'?'is-done':''}><button type="button" aria-label={item.status==='OPEN'?'完成观察项':'重新打开观察项'} onClick={()=>void toggle(item)}>{item.status==='OPEN'?'○':'✓'}</button><span>{item.content}<small>{item.source==='SYSTEM'?'系统建议':'自定义'}</small></span>{item.source==='USER'?<button type="button" className="radar-observation-delete" aria-label="删除观察项" onClick={()=>void remove(item)}>×</button>:null}</li>)}</ul>:<p className="radar-empty-copy">还没有观察项。</p>}
  </div>;
}
