import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import { MarketChart } from './MarketChart';
import { stamp, type Instrument } from './markets';

type Data = {source:string; window:string; points:Record<string,number>};

export function IntradayChart({asset,revision}:{asset:Instrument; revision:number}) {
  const [data,setData]=useState<Data>();
  const [error,setError]=useState('');
  useEffect(()=>{
    const controller=new AbortController();
    let timer:number;
    async function update() {
      try {
        const result=await api<Data>('/api/global-markets/'+asset.id+'/intraday',{signal:controller.signal});
        if (!controller.signal.aborted) {
          setData(result);
          setError('');
        }
      } catch (e) {
        if (!controller.signal.aborted) {
          setError(e instanceof Error ? e.message : '分时刷新失败');
        }
      } finally {
        if (!controller.signal.aborted) {
          timer=window.setTimeout(update,30000);
        }
      }
    }
    void update();
    return ()=>{controller.abort();window.clearTimeout(timer);};
  },[asset.id,revision]);
  const points=Object.entries(data?.points ?? {}).sort(([a],[b])=>a.localeCompare(b)).map(([day,value])=>({day,value}));
  return <>
    {points.length>0 && data ? <>
      <MarketChart lines={[{asset,points}]} percent={false} intraday/>
      <footer className="gm-foot">{data.source} · {data.window} · 最新数据 {stamp(points[points.length-1].day)} 北京时间 · 每 30 秒刷新</footer>
    </> : <p className="gm-nochart">{error || '正在读取分时行情…'}</p>}
    {points.length>0 && error && <p role="status" className="gm-note">刷新失败，显示上次分时数据</p>}
  </>;
}
