import { useEffect, useId, useMemo, useRef, useState } from 'react';
import type { CSSProperties } from 'react';

export interface EquityObservation {
  at:string;
  equity:number;
  benchmark:number;
  control?:number;
}
export interface ComparisonCurve {
  curve:EquityObservation[];
  capital:number;
  name:string;
}

const colors = ['#75d6e8', '#b9a5ed', '#a3b2c8'];
const fmt = (v:number) => (v>0?'+':'')+v.toFixed(2)+'%';

function chartSeries(points:EquityObservation[], capital:number, name:string, base?:ComparisonCurve, controlLabel?:string) {
  if (!(capital>0)) { return []; }
  const values = (key:'equity'|'benchmark'|'control') => points.map(p=>
    p[key]!=null && Number.isFinite(p[key]) ? (p[key]!/capital-1)*100 : null);
  const series = [{label:name, color:colors[0], values:values('equity'), dashed:false}];
  if (base && base.capital>0) {
    const aligned = new Map(base.curve.map(p=>[Date.parse(p.at),p.equity]));
    series.push({label:base.name, color:colors[1], dashed:false, values:points.map(p=>{
      const equity=aligned.get(Date.parse(p.at));
      return equity!=null && Number.isFinite(equity) ? (equity/base.capital-1)*100 : null;
    })});
  } else if (points.some(p=>p.control!=null)) {
    series.push({label:controlLabel ?? '同期对照',color:colors[1],values:values('control'),dashed:false});
  }
  series.push({label:'股票池等权基准',color:colors[2],values:values('benchmark'),dashed:true});
  return series;
}

export function ExperimentReturnChart({points,capital,name,base,controlLabel}: {
  points:EquityObservation[]; capital:number; name:string; base?:ComparisonCurve; controlLabel?:string;
}) {
  const svg=useRef<SVGSVGElement>(null);
  const gradient=useId();
  const [width,setWidth]=useState(640);
  const [cursor,inspect]=useState<number>();
  useEffect(()=>{
    const node=svg.current;
    if (!node || typeof ResizeObserver==='undefined') { return; }
    const observer=new ResizeObserver(([entry])=>setWidth(Math.max(200,entry.contentRect.width)));
    observer.observe(node);
    return ()=>observer.disconnect();
  },[points.length]);
  const lines=useMemo(()=>chartSeries(points,capital,name,base,controlLabel),[points,capital,name,base,controlLabel]);
  const last=points.length-1;
  const left=42, right=width-61, top=14, bottom=205;
  const plot=useMemo(()=>{
    let low=0, high=0;
    for (const line of lines) {
      for (const v of line.values) {
        if (v!=null) {low=Math.min(low,v);high=Math.max(high,v);}
      }
    }
    const ideal=Math.max(high-low,0.04)/4;
    const magnitude=10**Math.floor(Math.log10(ideal));
    const step=([1,2,2.5,5,10].find(n=>n*magnitude>=ideal) ?? 10)*magnitude;
    const min=Math.floor(low/step)*step, max=Math.max(min+step,Math.ceil(high/step)*step);
    const y=(v:number)=>bottom-(v-min)/(max-min)*(bottom-top);
    const x=(i:number)=>left+(right-left)*i/Math.max(1,last);
    const paths=lines.map(line=>{
      let move=true;
      return line.values.map((v,i)=>{
        if (v==null) {move=true;return '';}
        const part=(move?'M':'L')+x(i).toFixed(2)+' '+y(v).toFixed(2);
        move=false;return part;
      }).join(' ');
    });
    const ticks=Array.from({length:Math.round((max-min)/step)+1},(_,i)=>min+i*step);
    const ends=lines.flatMap((line,lineIndex)=>{
      const v=line.values[last];
      return v!=null ? [{lineIndex,v,at:y(v)}] : [];
    }).sort((a,b)=>a.at-b.at);
    for (let i=1;i<ends.length;i++) {ends[i].at=Math.max(ends[i].at,ends[i-1].at+16);}
    for (let i=ends.length-1;i>=0;i--) {ends[i].at=Math.min(ends[i].at,i===ends.length-1 ? bottom : ends[i+1].at-16);}
    return {x,y,paths,ticks,ends};
  },[lines,last,right]);
  if (!points.length || !(capital>0)) {return <p>暂无可绘制的净值记录。</p>;}
  const index=Math.min(cursor ?? last,last);
  const stamp=points[index].at.slice(0,16).replace('T',' ');
  function inspectPointer(event:React.PointerEvent<SVGSVGElement>) {
    const rect=event.currentTarget.getBoundingClientRect();
    const position=(event.clientX-rect.left)*width/rect.width;
    inspect(Math.max(0,Math.min(last,Math.round((position-left)/(right-left)*last))));
  }
  return <figure className="return-plot"><figcaption><b>累计收益率</b><time>{stamp}</time></figcaption>
    <div className="return-key">{lines.map((line,i)=><span key={i} style={{'--series':line.color} as CSSProperties} data-dashed={line.dashed}>
      <i/>{line.label}<b>{line.values[index]!=null ? fmt(line.values[index]!) : '—'}</b></span>)}</div>
    <svg ref={svg} viewBox={'0 0 '+width+' 238'} aria-label="各时点策略与基准累计收益率" role="img"
      onPointerMove={inspectPointer} onPointerDown={inspectPointer} onPointerLeave={()=>inspect(undefined)}>
      <defs><linearGradient id={gradient} x1="0" x2="0" y1="0" y2="1"><stop stopColor={colors[0]} stopOpacity=".17"/><stop offset="1" stopColor={colors[0]} stopOpacity=".01"/></linearGradient></defs>
      {plot.ticks.map(t=><g key={t}><line x1={left} x2={right} y1={plot.y(t)} y2={plot.y(t)} stroke={t===0?'#68818d':'#304355'} strokeDasharray={t===0?undefined:'2 4'}/>
        <text x={left-5} y={plot.y(t)+4} textAnchor="end">{Number(t.toFixed(4))}%</text></g>)}
      {lines[0].values.every(v=>v!=null) && <path d={plot.paths[0]+' L'+plot.x(last)+' '+bottom+' H'+left+' Z'} fill={'url(#'+gradient+')'}/>}
      {lines.map((line,i)=><path key={i} d={plot.paths[i]} stroke={line.color} strokeWidth={i===0?2:1.6} strokeDasharray={line.dashed?'5 4':undefined} fill="none"/>)}
      {plot.ends.map(({lineIndex,v,at})=><g key={lineIndex}><path d={'M'+plot.x(last)+' '+plot.y(v)+' l5 0 L'+(right+9)+' '+at} fill="none" stroke={lines[lineIndex].color}/>
        <text x={right+11} y={at+4} style={{fill:lines[lineIndex].color}}>{fmt(v)}</text></g>)}
      {cursor!=null && <g><line x1={plot.x(index)} x2={plot.x(index)} y1={top} y2={bottom} stroke="#b0c6d4" strokeDasharray="3 4"/>
        {lines.map((l,i)=>l.values[index]!=null && <circle key={i} cx={plot.x(index)} cy={plot.y(l.values[index]!)} r="3.5" fill={l.color}/>)}</g>}
      {[...new Set([0,Math.round(last/2),last])].map(i=><text key={i} x={plot.x(i)} y="230" textAnchor="middle">{points[i].at.slice(5,10)}</text>)}
    </svg>
    <input className="return-cursor" aria-label="查看历史时点收益" type="range" min="0" max={last} value={index}
      aria-valuetext={stamp+' '+lines.map(l=>l.label+' '+(l.values[index]!=null?fmt(l.values[index]!):'缺数')).join('，')}
      onChange={e=>inspect(Number(e.target.value))}/>
  </figure>;
}
