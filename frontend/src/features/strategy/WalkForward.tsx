import { useEffect, useRef, useState } from 'react';
import type { CSSProperties, FormEvent } from 'react';
import { api } from '../../shared/api/client';
import type { ClassicRecipe } from './ClassicStrategies';
import './WalkForward.css';

const labels = { window: '信号窗口（K 线根数）', holdings: '持股数量', exposure: '总仓位比例', maxWeight: '单股仓位上限', stopLoss: '止损比例' };
type Parameter = keyof typeof labels;
type Metrics = { net: number; fees: number; drawdown: number; fills: number };
interface Fold {
  trainStart: string; trainEnd: string; testStart: string; testEnd: string; selected: number; spread: number;
  candidates: (Metrics & {value: number})[];
  validation: Metrics; baseline: Metrics;
}
interface Study {
  id: number; created: string; state: string; latest: string | null;
  plan: { parameter: Parameter; trainDays: number; testDays: number; values: number[] };
  result: null | { baseline: number; capital: number; wins: number; meanSpread: number; folds: Fold[];
    coverage: {closeDays: number; tailDays: number; spans: number} };
}
type Lesson = {observation: string; calculation: string; limits: string; hypothesis: string; model: string; nextPlan?: Study['plan']};
const pct = (v: number) => (v > 0 ? '+' : '') + (100 * v).toFixed(2) + '%';
const message = (e: unknown) => e instanceof Error ? e.message : '读取失败，请重试';
const days = (first: string, last: string) => first.slice(5) + ' — ' + last.slice(5);
const states: Record<string, string> = {QUEUED:'排队中', BACKTEST:'逐窗回测中', DONE:'已完成', FAILED:'失败'};

function defaults(key: Parameter, recipe: ClassicRecipe) {
  const base = recipe[key] ?? 0;
  const [min, max, step] = key === 'window' ? [3, recipe.period === '15m' ? 12 : 20, 2]
    : key === 'holdings' ? [1, recipe.poolSize, 1] : key === 'stopLoss' ? [0, .99, .05] : [.01, 1, .1];
  return [...new Set([base-step, base, base+step].map(n => +Math.max(min, Math.min(max, n)).toFixed(2)))].join(', ');
}

function Evidence({ study, endpoint, onApply }: { study: Study; endpoint: string; onApply: (p:Study['plan'])=>void }) {
  const [foldIndex, select] = useState(0);
  const [lesson, setLesson] = useState<Lesson>();
  const [teaching, setTeaching] = useState(false);
  const [error, setError] = useState('');
  const r = study.result!;
  const fold = r.folds[foldIndex];
  const plan = study.plan;
  const scale = Math.max(.000001, ...r.folds.flatMap(f => f.candidates.map(c => Math.abs(c.net))));
  async function teach() {
    setTeaching(true);
    setError('');
    try {
      setLesson(await api<Lesson>(endpoint + '/' + study.id + '/teach', {method:'POST'}));
    } catch (e) {
      setError(message(e));
    } finally {
      setTeaching(false);
    }
  }
  return <div className="wf-evidence">
    <div className="wf-kpis">
      <div><span>完整验证窗口</span><strong>{r.folds.length}<small>轮</small></strong></div>
      <div><span>跑赢固定原参</span><strong>{r.wins}<small>/ {r.folds.length} 轮</small></strong></div>
      <div><span>平均样本外收益差</span><strong>{(r.meanSpread*100).toFixed(2)}<small>个百分点</small></strong></div>
    </div>
    <div className="wf-pair">
      <section className="att-card wf-heat"><header><h4>训练期参数热力图</h4><small>扣费收益</small></header>
        <p>每列是一段训练期。◆ 标出该轮选中的参数，点击列标题可查看随后表现。</p>
        <div className="wf-scroll"><table><thead><tr><th>{labels[plan.parameter]}</th>{r.folds.map((f,i)=>
          <th key={f.testStart}><button type="button" aria-pressed={i===foldIndex} onClick={()=>select(i)}>第 {i+1} 轮</button></th>)}</tr></thead>
          <tbody>{plan.values.map(value=><tr key={value}><th>{value}{value===r.baseline && <small> 原参</small>}</th>{r.folds.map(f=>{
            const c=f.candidates.find(item=>item.value===value)!;
            const picked=f.selected===value;
            return <td key={f.trainStart} data-polarity={Math.sign(c.net)} data-picked={picked}
              style={{'--intensity':.12+.40*Math.abs(c.net)/scale} as CSSProperties}>
              <span>{pct(c.net)}</span>{picked && <small aria-label="训练期选中">◆</small>}</td>;
          })}</tr>)}</tbody></table></div>
      </section>
      <section className="att-card wf-detail"><header><h4>第 {foldIndex+1} 轮 · 样本外</h4><small>{days(fold.testStart,fold.testEnd)}</small></header>
        <p>训练期 {days(fold.trainStart,fold.trainEnd)}，选中参数 {fold.selected}。两组分别从 ¥ {r.capital.toLocaleString('zh-CN')} 空仓起步。</p>
        <table><thead><tr><th>比较项</th><th>所选 {fold.selected}</th><th>原参 {r.baseline}</th></tr></thead>
          <tbody>{[['扣费收益',pct(fold.validation.net),pct(fold.baseline.net)],['最大收盘回撤',pct(-fold.validation.drawdown),pct(-fold.baseline.drawdown)],
            ['费用（元）',fold.validation.fees.toFixed(2),fold.baseline.fees.toFixed(2)],['成交笔数',fold.validation.fills,fold.baseline.fills]].map(([k,a,b])=>
            <tr key={k}><th>{k}</th><td>{a}</td><td>{b}</td></tr>)}</tbody></table>
      </section>
    </div>
    <section className="att-card wf-outcomes"><header><h4>逐轮验证收益</h4><small>每轮独立核算</small></header>
      <div className="wf-scroll"><table><thead><tr><th>验证日期</th><th>所选参数</th><th>所选收益</th><th>原参收益</th><th>差值（百分点）</th></tr></thead>
        <tbody>{r.folds.map((f,i)=><tr key={f.testStart} data-active={i===foldIndex}><th><button onClick={()=>select(i)}>{f.testStart} — {f.testEnd.slice(5)}</button></th>
          <td>{f.selected}</td><td>{pct(f.validation.net)}</td><td>{pct(f.baseline.net)}</td><td>{(f.spread*100).toFixed(2)}</td></tr>)}</tbody></table></div>
      <p>{r.coverage.closeDays} 个收盘观测 · {r.coverage.spans} 段连续交易日 · 尾部 {r.coverage.tailDays} 日不足一轮。平均差按各轮等权计算。</p>
    </section>
    <section className="att-card wf-coach"><header><h4>AI 研究助教</h4><button disabled={teaching} onClick={teach}>{teaching?'正在解读…':lesson?'重新解读':'解读本次验证'}</button></header>
      {error && <p role="alert">{error}</p>}{lesson && <div>{([['观察',lesson.observation],['计算',lesson.calculation],['证据边界',lesson.limits],['下一次实验',lesson.hypothesis]]).map(([k,v])=>
        <p key={k}><b>{k}</b> {v}</p>)}<small>{lesson.model}</small> {lesson.nextPlan && <button onClick={()=>onApply(lesson.nextPlan!)}>填入 {lesson.nextPlan.trainDays} 日训练方案</button>}</div>}</section>
  </div>;
}

export function WalkForward({version, recipe}: {version: number; recipe: ClassicRecipe}) {
  const endpoint='/api/quant/paper/versions/walk-forward/'+version;
  const [parameter, setParameter] = useState<Parameter>('window');
  const [values, setValues] = useState(()=>defaults('window',recipe));
  const [train, setTrain] = useState(15), [test, setTest] = useState(5);
  const [studies, setStudies] = useState<Study[]>(), [selected, setSelected]=useState<number>();
  const [revision, refresh] = useState(0), [busy, setBusy]=useState(false);
  const [readError, setReadError] = useState(''), [submitError, setSubmitError]=useState('');
  const settings=useRef<HTMLDetailsElement>(null);
  const token=useRef<{body:string; id:string}>();
  const study=studies?.find(s=>s.id===selected) ?? studies?.[0];
  useEffect(()=>{
    const ctrl=new AbortController();
    let timer:number;
    async function read() {
      try {
        const items=await api<Study[]>(endpoint,{signal:ctrl.signal});
        if (ctrl.signal.aborted) { return; }
        setStudies(items);
        setReadError('');
        if (items.some(s=>!['DONE','FAILED'].includes(s.state))) { timer=window.setTimeout(read,3500); }
      } catch (e) { if (!ctrl.signal.aborted) { setReadError(message(e)); } }
    }
    void read();
    return ()=>{ctrl.abort();window.clearTimeout(timer);};
  },[endpoint,revision]);
  async function submit(e:FormEvent) {
    e.preventDefault();
    const parts=values.trim().split(/[,，、\s]+/);
    const candidates=parts.map(Number);
    if (parts.includes('') || candidates.length<2 || candidates.length>8 || new Set(candidates).size!==candidates.length || !candidates.every(Number.isFinite)) {
      setSubmitError('请输入 2 至 8 个不同的数值，用逗号分隔。');return;
    }
    const body=JSON.stringify({parameter,values:candidates,trainDays:train,testDays:test});
    if (token.current?.body!==body) { token.current={body,id:crypto.randomUUID()}; }
    setBusy(true);setSubmitError('');
    try {
      const job=await api<{jobId:number}>(endpoint,{method:'POST',body,headers:{'Idempotency-Key':token.current.id}});
      setSelected(job.jobId);refresh(v=>v+1);
    } catch (e) { setSubmitError(message(e)); } finally { setBusy(false); }
  }
  return <div className="att-terminal wf-terminal">
    <header className="att-intro"><div><div className="att-context"><i/>实验 #{version} · 冻结行情</div><h3>滚动验证</h3>
      <p>先用过去的交易日选择参数，再用随后行情与固定原参对照。每轮重新选参、重新撮合。</p></div></header>
    <details ref={settings} className="wf-config att-card" open={!studies?.length || undefined}><summary>设置验证方案</summary>
      <form onSubmit={submit}><label>研究参数<select value={parameter} onChange={e=>{const k=e.target.value as Parameter;setParameter(k);setValues(defaults(k,recipe));}}>
        {(Object.keys(labels) as Parameter[]).map(k=><option key={k} value={k}>{labels[k]}</option>)}</select></label>
        <label>候选值<input required value={values} onChange={e=>setValues(e.target.value)}/></label>
        <label>训练天数<input required type="number" min={5} max={120} value={train} onChange={e=>setTrain(Number(e.target.value))}/></label>
        <label>验证天数<input required type="number" min={3} max={40} value={test} onChange={e=>setTest(Number(e.target.value))}/></label>
        <button disabled={busy}>{busy?'正在提交…':'运行滚动验证'}</button></form>
      <p>每次向前移动一个验证窗口。训练期按扣费收益排序，并列时优先接近原参，再取较小值。</p>
      {submitError && <p role="alert">{submitError}</p>}
    </details>
    {readError && <p role="alert">{readError} <button onClick={()=>refresh(v=>v+1)}>重试</button></p>}
    {study ? <><div className="wf-run"><label>验证记录 <select value={study.id} onChange={e=>setSelected(Number(e.target.value))}>
      {studies?.map(s=><option key={s.id} value={s.id}>#{s.id} · {s.created.slice(5,16).replace('T',' ')} · {states[s.state]??s.state}</option>)}</select></label>
      <span>{labels[study.plan.parameter]} · 训练 {study.plan.trainDays} 日 / 验证 {study.plan.testDays} 日</span></div>
      {study.result ? <Evidence key={study.id} study={study} endpoint={endpoint} onApply={p=>{
        setParameter(p.parameter);setValues(p.values.join(', '));setTrain(p.trainDays);setTest(p.testDays);setSubmitError('');
        if (settings.current) {
          settings.current.open=true;
          settings.current.scrollIntoView?.({block:'center'});
          settings.current.querySelector('input')?.focus({preventScroll:true});
        }
      }}/> : <p role={study.state==='FAILED'?'alert':'status'}>{study.latest || '等待执行器读取冻结行情…'}</p>}</>
    : !readError && <p role="status">{studies?'还没有验证记录。设置候选参数后即可运行第一轮研究。':'正在读取验证记录…'}</p>}
  </div>;
}
