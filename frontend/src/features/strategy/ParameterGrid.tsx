import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { allocations, frequencies, type ClassicRecipe } from './ClassicStrategies';
import { GridCharts } from './GridCharts';
import './ParameterGrid.css';

type Cycle = ClassicRecipe['rebalance'];
type Allocation = ClassicRecipe['allocation'];
export interface GridRow {
  analytics?: {drawdown:number | null; turnover:number | null; feeRate:number; closes:{day:string;net:number}[]} | null;
  id: number;
  version: number | null;
  state: string;
  latest: string | null;
  rebalance: Cycle;
  allocation: Allocation;
  holdings: number;
  net: number | null;
  benchmark: number | null;
  fees: number | null;
  fills: number | null;
}
interface Group {
  batch: string;
  created: string;
  rows: GridRow[];
}
const stateNames: Record<string, string> = {
  QUEUED: '排队中',
  DATA: '准备行情',
  BACKTEST: '回测中',
  DONE: '已完成',
  FAILED: '失败',
};
const percent = (v: number | null) => (v == null ? '—' : (100 * v).toFixed(2) + '%');
const msg = (e: unknown) => (e instanceof Error ? e.message : '无法读取参数实验，请重试');

function Axis<T extends string | number>({
  label,
  values,
  selected,
  name,
  onChange,
}: {
  label: string;
  values: T[];
  selected: T[];
  name: (v: T) => string;
  onChange: (values: T[]) => void;
}) {
  return (
    <fieldset className="grid-axis">
      <legend>{label}</legend>
      {values.map((v) => (
        <button
          type="button"
          key={v}
          aria-pressed={selected.includes(v)}
          onClick={() =>
            onChange(
              selected.includes(v)
                ? selected.filter((x) => x !== v)
                : values.filter((x) => x === v || selected.includes(x)),
            )
          }
        >
          {name(v)}
        </button>
      ))}
    </fieldset>
  );
}

export function ParameterGrid({
  version,
  recipe,
  capital,
  onResult,
}: {
  version: number;
  recipe: ClassicRecipe;
  capital: number;
  onResult: (id: number) => void;
}) {
  const [cycles, setCycles] = useState<Cycle[]>(
    recipe.rebalance === 'DAILY' ? ['DAILY', 'WEEKLY'] : ['DAILY', recipe.rebalance],
  );
  const [methods, setMethods] = useState<Allocation[]>([recipe.allocation]),
    [sizes, setSizes] = useState([recipe.holdings]);
  const [groups, setGroups] = useState<Group[]>(),
    [shown, selectGroup] = useState(''),
    [readError, setReadError] = useState('');
  const [submitting, setSubmitting] = useState(false),
    [submitError, setSubmitError] = useState(''),
    [revision, reload] = useState(0);
  const token = useRef<{ signature: string; id: string }>();
  const alive = useRef(true);
  const endpoint = '/api/quant/paper/versions/grids/' + version;
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  useEffect(() => {
    const ctrl = new AbortController();
    let timer: number;
    async function refresh() {
      try {
        const data = await api<Group[]>(endpoint, { signal: ctrl.signal });
        if (ctrl.signal.aborted) {
          return;
        }
        setGroups(data);
        setReadError('');
        if (data.some((g) => g.rows.some((r) => !['DONE', 'FAILED'].includes(r.state)))) {
          timer = window.setTimeout(refresh, 5000);
        }
      } catch (e) {
        if (!ctrl.signal.aborted) {
          setReadError(msg(e));
        }
      }
    }
    void refresh();
    return () => {
      ctrl.abort();
      window.clearTimeout(timer);
    };
  }, [endpoint, revision]);
  async function submit() {
    const body = JSON.stringify({ frequencies: cycles, allocations: methods, holdings: sizes });
    if (token.current?.signature !== body) {
      token.current = { signature: body, id: crypto.randomUUID() };
    }
    setSubmitting(true);
    setSubmitError('');
    try {
      const data = await api<{ batch: string }>(endpoint, {
        method: 'POST',
        headers: { 'Idempotency-Key': token.current.id },
        body,
      });
      if (alive.current) {
        selectGroup(data.batch);
        reload((v) => v + 1);
      }
    } catch (e) {
      if (alive.current) {
        setSubmitError(msg(e));
      }
    } finally {
      if (alive.current) {
        setSubmitting(false);
      }
    }
  }
  const picks = cycles.flatMap((c) => methods.flatMap((m) => sizes.map((n) => ({ c, m, n }))));
  const valid = picks.length >= 2 && picks.length <= 24;
  const group = groups?.find((g) => g.batch === shown) ?? groups?.[0];
  const completed = group?.rows.filter((r) => r.state === 'DONE').length ?? 0;
  return (
    <div className="att-terminal pg-terminal">
      <header className="att-intro">
        <div>
          <div className="att-context">
            <i />
            基线 #{version} · 冻结行情
          </div>
          <h3>批量参数实验</h3>
          <p>在同一股票池和区间内比较调仓周期、仓位分配和持股数量。</p>
        </div>
        <div className="att-head-result">
          <span>每组初始本金</span>
          <strong>
            <small>¥</small> {capital.toLocaleString('zh-CN')}
          </strong>
        </div>
      </header>
      <div className="pg-design">
        <section className="att-card">
          <h4>选择研究变量</h4>
          <Axis
            label="调仓周期"
            values={Object.keys(frequencies) as Cycle[]}
            selected={cycles}
            name={(v) => frequencies[v]}
            onChange={setCycles}
          />
          <Axis
            label="仓位分配"
            values={Object.keys(allocations) as Allocation[]}
            selected={methods}
            name={(v) => allocations[v]}
            onChange={setMethods}
          />
          <Axis
            label="最多持股"
            values={Array.from({ length: Math.min(recipe.poolSize, 10) }, (_, i) => i + 1)}
            selected={sizes}
            name={(v) => v + ' 只'}
            onChange={setSizes}
          />
        </section>
        <section className="att-card pg-preview">
          <header>
            <h4>待运行组合</h4>
            <b>{picks.length} 组</b>
          </header>
          <ul>
            {picks.slice(0, 6).map((p) => (
              <li key={[p.c, p.m, p.n].join('-')}>
                <span>{frequencies[p.c]}</span>
                <span>{allocations[p.m]}</span>
                <b>{p.n} 只</b>
              </li>
            ))}
          </ul>
          {picks.length > 6 && <small>另有 {picks.length - 6} 种组合</small>}
          <p>{valid ? '沿用基线的信号规则和撮合费用，依次运行独立历史回测。' : '每批选择 2 至 24 种组合。'}</p>
          <button className="att-teach" disabled={!valid || submitting} onClick={submit}>
            {submitting ? '正在提交…' : '提交 ' + picks.length + ' 组回测'}
          </button>
          {submitError && <p role="alert">{submitError}</p>}
        </section>
      </div>
      <section className="att-card pg-results">
        <header>
          <h4>实验结果矩阵</h4>
          {group && (
            <label>
              批次{' '}
              <select value={group.batch} onChange={(e) => selectGroup(e.target.value)}>
                {groups?.map((g) => (
                  <option key={g.batch} value={g.batch}>
                    {g.created.slice(0, 19).replace('T', ' ')} · {g.rows.length} 组
                  </option>
                ))}
              </select>
            </label>
          )}
        </header>
        {readError && (
          <p role="alert">
            {readError} <button onClick={() => reload((v) => v + 1)}>重试</button>
          </p>
        )}
        {group ? (
          <>
            <p role="status">
              已完成 {completed} / {group.rows.length} 组
            </p>
            <GridCharts key={group.batch} rows={group.rows}/>
            <div className="att-scroll">
              <table>
                <thead>
                  <tr>
                    {[
                      '调仓周期',
                      '仓位分配',
                      '持股上限',
                      '扣费收益',
                      '相对基准',
                      '费用（元）',
                      '成交',
                      '状态',
                    ].map((t) => (
                      <th key={t}>{t}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {group.rows.map((r) => (
                    <tr key={r.id}>
                      <th>{frequencies[r.rebalance]}</th>
                      <td>{allocations[r.allocation]}</td>
                      <td>{r.holdings} 只</td>
                      <td data-value={Math.sign(r.net ?? 0)}>{percent(r.net)}</td>
                      <td>
                        {r.net != null && r.benchmark != null
                          ? ((r.net - r.benchmark) * 100).toFixed(2) + ' 个百分点'
                          : '—'}
                      </td>
                      <td>{r.fees?.toFixed(2) ?? '—'}</td>
                      <td>{r.fills != null ? r.fills + ' 笔' : '—'}</td>
                      <td>
                        {r.version != null ? (
                          <button className="att-link" onClick={() => onResult(r.version!)}>
                            分析 #{r.version} ↗
                          </button>
                        ) : r.state === 'FAILED' ? (
                          <details>
                            <summary>失败</summary>
                            <small>{r.latest}</small>
                          </details>
                        ) : (
                          (stateNames[r.state] ?? r.state)
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        ) : (
          !readError && <p>{groups === undefined || shown ? '正在读取实验批次…' : '此基线尚无批量回测记录。'}</p>
        )}
      </section>
    </div>
  );
}
