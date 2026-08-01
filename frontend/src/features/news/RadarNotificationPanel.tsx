import { useState } from 'react';
import { api } from '../../shared/api/client';
import type { RadarNotificationCenter } from './researchRadarTypes';

export function RadarNotificationPanel({ hint = 0, onOpenEvent }: { hint?: number; onOpenEvent: (eventId: number) => void }) {
  const [open,setOpen]=useState(false);const [center,setCenter]=useState<RadarNotificationCenter>();const [loading,setLoading]=useState(false);
  async function toggle(){const next=!open;setOpen(next);if(next&&!center){setLoading(true);try{setCenter(await api<RadarNotificationCenter>('/api/research-radar/notifications?limit=30'));}finally{setLoading(false);}}}
  async function readAll(){await api('/api/research-radar/notifications/read-all',{method:'POST'});setCenter((value)=>value?{...value,unreadCount:0,items:value.items.map((item)=>({...item,read:true}))}:value);}
  async function openItem(id:number,eventId?:number){await api(`/api/research-radar/notifications/${id}/read`,{method:'POST'});setCenter((value)=>value?{...value,unreadCount:Math.max(0,value.unreadCount-1),items:value.items.map((item)=>item.id===id?{...item,read:true}:item)}:value);if(eventId)onOpenEvent(eventId);}
  return <div className="radar-notification-shell"><button type="button" className="ghost-button radar-notification-trigger" aria-expanded={open} onClick={()=>void toggle()}>关注提醒 <span>{center?.unreadCount??hint}</span></button>
    {open?<div className="radar-notification-panel"><header><div><strong>变化提醒</strong><small>今日 {center?.todayCount??0} 条</small></div>{center?.unreadCount?<button type="button" onClick={()=>void readAll()}>全部已读</button>:null}</header>
      {loading?<p>正在读取提醒…</p>:center?.items.length?<ul>{center.items.map((item)=><li key={item.id} className={item.read?'':'is-unread'}><button type="button" onClick={()=>void openItem(item.id,item.eventId)}><strong>{item.title}</strong><span>{item.message}</span></button></li>)}</ul>:<p>关注事件出现新变化后会显示在这里。</p>}
    </div>:null}</div>;
}
