import { useId, useState } from 'react';
import { price, signed, stamp, type PlotLine } from './markets';

const W=880, H=308, left=65, right=18, top=20, bottom=32;
export function MarketChart({lines,percent,intraday=false}:{lines:PlotLine[];percent:boolean;intraday?:boolean}) {
  const fill=useId().replace(/:/g,'');
  const [hover,setHover]=useState<string>();
  const days=Array.from(new Set(lines.flatMap(l=>l.points.map(p=>p.day)))).sort();
  if (!days.length) { return <div className="gm-nochart">当前区间没有可对齐的日线</div>; }
  const first=Date.parse(days[0]), end=Date.parse(days[days.length-1]);
  const values=lines.flatMap(l=>l.points.map(p=>p.value));
  const low=Math.min(...values), high=Math.max(...values);
  const pad=(high-low)*0.1 || Math.max(Math.abs(low)*0.002,0.01);
  const min=low-pad, max=high+pad;
  const x=(d:string)=>left+(W-left-right)*(Date.parse(d)-first)/Math.max(1,end-first);
  const y=(v:number)=>top+(H-top-bottom)*(max-v)/(max-min);
  const current=hover && days.includes(hover) ? hover : days[days.length-1];
  const fmt=(v:number)=>percent ? signed(v)+'%' : price(v);
  const labels=Array.from(new Set([0,1,2,3,4].map(i=>days[Math.round((days.length-1)*i/4)])));
  return <div className="gm-plot">
    <svg viewBox={`0 0 ${W} ${H}`} role="img" tabIndex={0} aria-label={intraday?"分时走势，使用左右方向键查看报价":"历史日线，使用左右方向键查看每日数值"}
      onPointerMove={e=>{
        const svg=e.currentTarget, matrix=svg.getScreenCTM();
        if (!matrix) { return; }
        const pos=new DOMPoint(e.clientX,e.clientY).matrixTransform(matrix.inverse()).x;
        setHover(days.reduce((a,b)=>Math.abs(x(b)-pos)<Math.abs(x(a)-pos)?b:a));
      }} onPointerLeave={()=>setHover(undefined)}
      onKeyDown={e=>{
        if (e.key==='ArrowLeft' || e.key==='ArrowRight') {
          e.preventDefault();
          setHover(days[Math.max(0,Math.min(days.length-1,days.indexOf(current)+(e.key==='ArrowLeft'?-1:1)))]);
        }
      }}>
      <defs><linearGradient id={fill} x2="0" y2="1"><stop stopColor={lines[0].asset.color} stopOpacity=".24"/><stop offset="1" stopColor={lines[0].asset.color} stopOpacity=".015"/></linearGradient></defs>
      {[0,1,2,3,4].map(i=>{ const v=max-(max-min)*i/4;
        return <g key={i}><line className="gm-gridline" x1={left} x2={W-right} y1={y(v)} y2={y(v)}/><text x={left-8} textAnchor="end" y={y(v)+4}>{fmt(v)}</text></g>;
      })}
      {labels.map((day,i)=><text key={day} x={x(day)} y={H-8} textAnchor={i===labels.length-1?'end':i===0?'start':'middle'}>{intraday?stamp(day).slice(-5):day.slice(5)}</text>)}
      {lines.map(({asset,points})=>{
        const firstPoint=points[0], lastPoint=points[points.length-1];
        if (points.length===1) {
          return <circle key={asset.id} cx={x(firstPoint.day)} cy={y(firstPoint.value)} r="2" fill={asset.color}/>;
        }
        const path=points.map((p,i)=>(i?'L':'M')+x(p.day).toFixed(2)+','+y(p.value).toFixed(2)).join(' ');
        return <g key={asset.id}>
          {!percent && <path d={path+` L${x(lastPoint.day)},${H-bottom} L${x(firstPoint.day)},${H-bottom} Z`} fill={`url(#${fill})`}/>}
          <path d={path} stroke={asset.color} fill="none" strokeWidth={percent?2:2.7} strokeLinejoin="round" strokeLinecap="round"/>
        </g>;
      })}
      <line className="gm-cursor" x1={x(current)} x2={x(current)} y1={top} y2={H-bottom}/>
      {lines.map(l=>{const p=l.points.find(p=>p.day===current);
        return p && <circle key={l.asset.id} cx={x(current)} cy={y(p.value)} r="4" fill={l.asset.color}/>;
      })}
    </svg>
    <div className="gm-readout"><time>{intraday?stamp(current):current}</time>{lines.map(l=>{const p=l.points.find(p=>p.day===current);
      return <span key={l.asset.id}><i style={{background:l.asset.color}}/>{l.asset.name} <b>{p?fmt(p.value):'无日线'}</b></span>;
    })}</div>
  </div>;
}
