export type Instrument = { id: string; name: string; ticker: string; group: string; color: string; unit: string };
export type Snapshot = {
  series: { symbol: string; source: string; marketStatus?: string; asOf: string; price: number; reference: number; closes: Record<string,number> };
  fallback: boolean;
  retrievedAt: string;
};
export const core: Instrument[] = [
  {id:'SPX',name:'标普 500',ticker:'S&P 500',group:'美股指数',color:'#42c8bb',unit:'点'},
  {id:'DJI',name:'道琼斯工业平均指数',ticker:'DJIA',group:'美股指数',color:'#7eabff',unit:'点'},
  {id:'IXIC',name:'纳斯达克综合指数',ticker:'NASDAQ COMP',group:'美股指数',color:'#ab93ef',unit:'点'},
  {id:'XAU',name:'现货黄金',ticker:'XAU / USD',group:'贵金属',color:'#e5b65e',unit:'美元 / 金衡盎司'},
  {id:'BTC',name:'比特币',ticker:'BTC / USD',group:'加密货币',color:'#e89461',unit:'美元'},
  {id:'ETH',name:'以太坊',ticker:'ETH / USD',group:'加密货币',color:'#8e9ee9',unit:'美元'}
];
export const price = (v: number) => v.toLocaleString('zh-CN', {minimumFractionDigits:2,maximumFractionDigits:2});
export const signed = (v: number) => (v > 0 ? '+' : '') + v.toFixed(2);
export const stamp = (v: string) => new Intl.DateTimeFormat('zh-CN', {month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23',timeZone:'Asia/Shanghai'}).format(new Date(v));
export function stock(ticker: string, name: string): Instrument {
  return {id:'US-'+ticker,name,ticker,group:'美股自选',color:'#55b7cc',unit:'美元'};
}

export type PlotLine = {asset: Instrument; points: {day:string; value:number}[]};
export function chartLines(assets: Instrument[], quotes: Record<string,Snapshot>, days: number, compare: boolean): PlotLine[] {
  const rows = assets.filter(a => quotes[a.id]).map(asset => ({
    asset, closes:quotes[asset.id].series.closes
  }));
  if (!rows.length) { return []; }
  const last = rows.reduce((end,r) => Math.max(end, ...Object.keys(r.closes).map(Date.parse)), 0);
  const from = new Date(last - (days-1)*86400000).toISOString().slice(0,10);
  const start = compare ? Object.keys(rows[0].closes).sort().find(d => d >= from && rows.every(r => r.closes[d] > 0)) : from;
  if (!start) { return []; }
  return rows.map(({asset,closes}) => ({
    asset, points:Object.keys(closes).sort().filter(d => d >= start).map(day => ({
      day, value:compare ? 100 * (closes[day] / closes[start] - 1) : closes[day]
    }))
  })).filter(line => line.points.length);
}
