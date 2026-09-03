import { FormEvent, useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import { HoldingAnalysisDrawer, type HoldingAnalysis } from './HoldingAnalysisDrawer';
import './RealHoldingsLab.css';

type LabTab = 'overview' | 'ledger' | 'diagnosis' | 'shadow' | 'validation';
type TransactionType = 'OPENING_BALANCE' | 'BUY' | 'SELL' | 'CASH_DIVIDEND' | 'BONUS_SHARE' | 'CASH_DEPOSIT' | 'CASH_WITHDRAWAL' | 'REVERSAL';

type Position = {
  instrumentCode: string; instrumentName?: string; quantity: number; totalCost: number;
  averageCost: number; lastPrice?: number; quoteDate?: string; quoteQuality?: string;
  marketValue?: number; realizedProfit: number; unrealizedProfit?: number;
  dividendIncome: number; totalProfit?: number; weight: number; openedOn?: string;
  openingBalance?: boolean;
};

type StockAccount = {
  cash: number; marketValue: number; totalEquity: number; realizedProfit: number;
  unrealizedProfit: number; dividendIncome: number; totalProfit: number;
  concentration: number; cashTracked?: boolean; calculatedAt?: string; positions: Position[];
};

type StockTransaction = {
  id: number; clientRequestId: string; instrumentCode?: string; instrumentName?: string;
  type: TransactionType; tradeDate: string; quantity?: number; price?: number;
  totalFees: number; cashAmount?: number; reversalOfId?: number; note?: string; createdAt: string;
};

type HoldingDecision = {
  id?: number; instrumentCode: string; instrumentName?: string; decisionDate: string;
  forecastRunId?: number; horizonDays: number; modelVersion: string; dataFingerprint: string;
  action: 'HOLD' | 'ALLOW_ADD' | 'REDUCE_CONCENTRATION' | 'EXIT_TRIGGERED' | 'ABSTAIN';
  suggestedQuantity: number; expectedEdgeAfterCost: number; p10RiskAmount: number;
  p90UpsideAmount: number; currentMarketValue: number; projectedWeight: number;
  evidence: string[]; blockers: string[]; explanation: string; benchmark: string;
  policyVersion: string; validationStatus: string; maturityDate?: string;
  strategyReturn?: number; holdReturn?: number; incrementalReturn?: number; createdAt?: string;
};

const emptyAccount: StockAccount = {
  cash: 0, marketValue: 0, totalEquity: 0, realizedProfit: 0, unrealizedProfit: 0,
  dividendIncome: 0, totalProfit: 0, concentration: 0, cashTracked: false, positions: []
};

const transactionLabels: Record<TransactionType, string> = {
  OPENING_BALANCE: '期初持仓', BUY: '买入', SELL: '卖出', CASH_DIVIDEND: '现金分红',
  BONUS_SHARE: '送转股', CASH_DEPOSIT: '资金转入', CASH_WITHDRAWAL: '资金转出', REVERSAL: '冲正'
};

const actionLabels: Record<HoldingDecision['action'], string> = {
  HOLD: '保持持有', ALLOW_ADD: '允许加仓', REDUCE_CONCENTRATION: '降低集中度',
  EXIT_TRIGGERED: '退出条件触发', ABSTAIN: '证据不足'
};

const initialForm = (type: TransactionType = 'BUY') => ({
  type, tradeDate: new Date().toISOString().slice(0, 10), code: '',
  quantity: '', price: '', commission: '', stampDuty: '', transferFee: '', otherFee: '',
  cashAmount: '', note: ''
});

export function RealHoldingsLab({ addToast }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void }) {
  const [tab, setTab] = useState<LabTab>('overview');
  const [account, setAccount] = useState<StockAccount>(emptyAccount);
  const [transactions, setTransactions] = useState<StockTransaction[]>([]);
  const [decisions, setDecisions] = useState<HoldingDecision[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState(initialForm());
  const [analysisTarget, setAnalysisTarget] = useState<Position>();
  const [analysis, setAnalysis] = useState<HoldingAnalysis>();
  const [analysisLoading, setAnalysisLoading] = useState(false);

  async function load(autoEvaluate = true) {
    try {
      const [nextAccount, nextTransactions, frozen] = await Promise.all([
        api<StockAccount>('/api/strategy/stock-account'),
        api<StockTransaction[]>('/api/strategy/stock-transactions'),
        api<HoldingDecision[]>('/api/strategy/holding-decisions')
      ]);
      setAccount(nextAccount); setTransactions(nextTransactions); setDecisions(frozen);
      if (autoEvaluate && nextAccount.positions.length > 0) {
        setRefreshing(true);
        try {
          const evaluated = await api<HoldingDecision[]>('/api/strategy/holding-decisions/refresh', { method: 'POST' });
          setDecisions(mergeDecisions(evaluated, frozen));
        } catch (error) {
          addToast(error instanceof Error ? error.message : '影子策略暂不可用', 'info');
        } finally {
          setRefreshing(false);
        }
      }
    } catch (error) {
      addToast(error instanceof Error ? error.message : '真实持仓加载失败', 'error');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  const latestByCode = useMemo(() => {
    const values = new Map<string, HoldingDecision>();
    decisions.forEach(item => { if (!values.has(item.instrumentCode)) values.set(item.instrumentCode, item); });
    return values;
  }, [decisions]);
  const availableCashRate = account.totalEquity > 0 ? account.cash / account.totalEquity : 0;
  const profitRate = account.marketValue > 0 ? account.totalProfit / Math.max(1, account.marketValue - account.unrealizedProfit) : 0;

  async function submit(event: FormEvent) {
    event.preventDefault();
    const cashEvent = form.type === 'CASH_DEPOSIT' || form.type === 'CASH_WITHDRAWAL';
    const payload = compact({
      clientRequestId: uniqueRequestId(), type: form.type, tradeDate: form.tradeDate,
      code: cashEvent ? undefined : form.code.trim(), quantity: numberOrUndefined(form.quantity),
      price: numberOrUndefined(form.price), commission: numberOrUndefined(form.commission),
      stampDuty: numberOrUndefined(form.stampDuty), transferFee: numberOrUndefined(form.transferFee),
      otherFee: numberOrUndefined(form.otherFee), cashAmount: numberOrUndefined(form.cashAmount),
      note: form.note.trim() || undefined
    });
    try {
      await api('/api/strategy/stock-transactions', { method: 'POST', body: JSON.stringify(payload) });
      setFormOpen(false); setForm(initialForm()); await load(false);
      addToast('交易已写入不可变账本', 'success');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '交易记录失败', 'error');
    }
  }

  async function reverse(item: StockTransaction) {
    try {
      await api(`/api/strategy/stock-transactions/${item.id}/reverse`, {
        method: 'POST', body: JSON.stringify({ clientRequestId: uniqueRequestId(), tradeDate: new Date().toISOString().slice(0, 10), note: `冲正 #${item.id}` })
      });
      await load(false); addToast('已用冲正事件纠正原记录', 'success');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '冲正失败', 'error');
    }
  }

  function openForm(type: TransactionType) {
    setForm(initialForm(type));
    setFormOpen(true);
  }

  async function reclassifyOpening(item: StockTransaction) {
    try {
      await api(`/api/strategy/stock-transactions/${item.id}/reclassify-opening`, {
        method: 'POST', body: JSON.stringify({
          clientRequestId: uniqueRequestId(), tradeDate: new Date().toISOString().slice(0, 10)
        })
      });
      await load(false);
      addToast('已追加冲正并更正为期初持仓', 'success');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '期初持仓更正失败', 'error');
    }
  }

  async function openAnalysis(position: Position) {
    setAnalysisTarget(position);
    setAnalysis(undefined);
    setAnalysisLoading(true);
    try {
      setAnalysis(await api<HoldingAnalysis>(`/api/strategy/stock-positions/${encodeURIComponent(position.instrumentCode)}/analysis`));
    } catch (error) {
      addToast(error instanceof Error ? error.message : '持仓分析暂不可用', 'error');
      setAnalysisTarget(undefined);
    } finally {
      setAnalysisLoading(false);
    }
  }

  return <section className="holdings-lab">
    <header className="holdings-lab-hero">
      <div>
        <p>LIVE CAPITAL · EVIDENCE FIRST</p>
        <h3>真实持仓策略实验室</h3>
        <span>人工记账、真实行情估值、独立仓位建议。系统只给可验证的动作许可，不替你下单。</span>
      </div>
      <div className="holdings-hero-actions">
        <button type="button" className="secondary" onClick={() => openForm('BUY')}>记录新交易</button>
        <button type="button" onClick={() => openForm('OPENING_BALANCE')}>补录已有持仓</button>
      </div>
    </header>

    <nav className="holdings-lab-tabs" aria-label="真实持仓视图">
      {([['overview', '实盘总览'], ['ledger', '交易流水'], ['diagnosis', '持仓诊断'], ['shadow', '影子策略'], ['validation', '验证账本']] as Array<[LabTab, string]>).map(([id, label]) =>
        <button type="button" key={id} className={tab === id ? 'active' : ''} onClick={() => setTab(id)}>{label}</button>)}
    </nav>

    {loading ? <div className="holdings-lab-empty">正在重放交易账本并获取原始行情…</div> : null}
    {!loading && tab === 'overview' ? <>
      <div className="holdings-metric-grid">
        <Metric label={account.cashTracked ? '账户净值' : '股票资产'} value={money(account.totalEquity)} note={account.cashTracked ? `现金占比 ${percent(availableCashRate)}` : '现金未登记，仅统计股票资产'} tone="primary" />
        <Metric label="持仓市值" value={money(account.marketValue)} note={`${account.positions.length} 只真实持仓`} />
        <Metric label="累计盈亏" value={signedMoney(account.totalProfit)} note={`估算收益率 ${percent(profitRate)}`} tone={account.totalProfit >= 0 ? 'positive' : 'negative'} />
        <Metric label="已实现 + 分红" value={signedMoney(account.realizedProfit + account.dividendIncome)} note={`浮动盈亏 ${signedMoney(account.unrealizedProfit)}`} />
      </div>
      <div className="holdings-overview-grid">
        <section className="holdings-panel holdings-position-panel">
          <PanelHead eyebrow="POSITION BOOK" title="当前真实持仓" meta={account.calculatedAt ? `核算于 ${dateTime(account.calculatedAt)}` : '等待核算'} />
          {account.positions.length === 0 ? <Empty title="还没有真实持仓" text="先记录资金转入与第一笔买入，系统会从不可变流水重建成本和现金。" /> :
            <div className="holding-position-list">{account.positions.map(position => {
              const decision = latestByCode.get(position.instrumentCode);
              return <article key={position.instrumentCode} className="holding-position-row">
                <div className="holding-position-name"><i /><div><b>{position.instrumentName || position.instrumentCode}</b><span>{position.instrumentCode} · {position.quantity} 股</span></div></div>
                <div><small>市值 / 成本</small><b>{money(position.marketValue)} <em>/ {money(position.totalCost)}</em></b></div>
                <div><small>现价 / 均价</small><b>¥{fixed(position.lastPrice)} <em>/ ¥{fixed(position.averageCost)}</em></b></div>
                <div><small>持仓盈亏</small><b className={(position.unrealizedProfit ?? 0) >= 0 ? 'up' : 'down'}>{signedMoney(position.unrealizedProfit ?? 0)}</b></div>
                <div className="holding-position-action" data-action={decision?.action ?? 'ABSTAIN'}><small>影子动作</small><b>{decision ? actionLabels[decision.action] : '等待预测记录'}</b></div>
                <button type="button" className="holding-position-open" aria-label={`查看${position.instrumentName || position.instrumentCode}持仓量化分析`} onClick={() => openAnalysis(position)}>展开分析 <span>↗</span></button>
              </article>;
            })}</div>}
        </section>
        <aside className="holdings-panel holdings-risk-rail">
          <PanelHead eyebrow="RISK RAIL" title="组合风险护栏" meta="不参与涨跌模型" />
          <Gauge label="单股集中度" value={account.concentration} warn={account.concentration > .65} />
          {account.cashTracked ? <Gauge label="现金缓冲" value={availableCashRate} warn={availableCashRate < .1} inverse /> : <div className="holding-cash-untracked"><span>现金缓冲</span><b>尚未登记</b><p>期初持仓不会虚构历史现金。需要组合仓位建议时，可补录当前可用现金。</p></div>}
          <div className="holdings-boundary"><b>边界声明</b><p>成本价与浮亏只用于解释真实账户，不作为预测因子；加仓至少满足一手、费用后优势和集中度约束。</p></div>
        </aside>
      </div>
    </> : null}

    {!loading && tab === 'ledger' ? <section className="holdings-panel holdings-ledger">
      <PanelHead eyebrow="IMMUTABLE LEDGER" title="交易流水" meta={`${transactions.length} 条事件`} />
      {transactions.length === 0 ? <Empty title="账本仍为空" text="记录不会被直接修改；录错时系统会追加一条冲正事件。" /> : <div className="holdings-table-wrap"><table><thead><tr><th>日期</th><th>事件</th><th>标的</th><th>数量</th><th>成交价</th><th>现金 / 费用</th><th>备注</th><th /></tr></thead><tbody>{transactions.map(item => <tr key={item.id}>
        <td>{item.tradeDate}</td><td><span className="transaction-pill" data-type={item.type}>{transactionLabels[item.type]}</span></td>
        <td><b>{item.instrumentName || '账户资金'}</b><small>{item.instrumentCode || 'CASH'}</small></td><td>{item.quantity ?? '—'}</td><td>{item.price == null ? '—' : `¥${fixed(item.price)}`}</td>
        <td>{item.cashAmount ? money(item.cashAmount) : `费用 ${money(item.totalFees)}`}</td><td>{item.note || '—'}<small>录入 {dateTime(item.createdAt)}</small></td>
        <td>{item.type !== 'REVERSAL' && !transactions.some(candidate => candidate.reversalOfId === item.id) ? <div className="holding-ledger-actions">{item.type === 'BUY' ? <button type="button" className="holding-link primary" onClick={() => reclassifyOpening(item)}>改为期初</button> : null}<button type="button" className="holding-link" onClick={() => reverse(item)}>冲正</button></div> : <span>已冻结</span>}</td>
      </tr>)}</tbody></table></div>}
    </section> : null}

    {!loading && tab === 'diagnosis' ? <section className="holdings-diagnosis-grid">
      {account.positions.length === 0 ? <Empty title="暂无诊断对象" text="真实持仓建立后，这里会把成本、风险暴露与最新量化证据并排展示。" /> : account.positions.map(position => {
        const decision = latestByCode.get(position.instrumentCode);
        const returnRate = position.averageCost > 0 && position.lastPrice != null ? position.lastPrice / position.averageCost - 1 : 0;
        return <article className="holdings-panel diagnosis-card" key={position.instrumentCode}>
          <header><div><span>{position.instrumentCode}</span><h4>{position.instrumentName || '未命名股票'}</h4></div><b className={returnRate >= 0 ? 'up' : 'down'}>{percent(returnRate)}</b></header>
          <dl><div><dt>真实成本</dt><dd>¥{fixed(position.averageCost)}</dd></div><div><dt>原始现价</dt><dd>¥{fixed(position.lastPrice)}</dd></div><div><dt>行情日</dt><dd>{position.quoteDate || '不可用'}</dd></div><div><dt>组合权重</dt><dd>{percent(position.weight)}</dd></div></dl>
          <div className="diagnosis-decision"><span>当前诊断</span><b>{decision ? actionLabels[decision.action] : '缺少最新预测'}</b><p>{decision?.explanation || '请先在单股预测页生成一次可复用的量化研究记录。'}</p></div>
        </article>;
      })}
    </section> : null}

    {!loading && tab === 'shadow' ? <section className="holdings-shadow-list">
      <div className="holdings-shadow-intro"><div><span>SHADOW DECISIONS</span><h4>独立仓位策略</h4></div><p>{refreshing ? '正在读取最新冻结预测…' : '动作由概率、收益分布、交易成本和风险预算共同决定；不会自动交易。'}</p></div>
      {decisions.length === 0 ? <Empty title="尚无冻结建议" text="先建立持仓，并在单股预测页留下研究记录；进入本页后系统会自动评估。" /> : decisions.map(item => <article className="holdings-panel shadow-decision" key={item.id ?? `${item.instrumentCode}-${item.decisionDate}`} data-action={item.action}>
        <header><div><span>{item.decisionDate} · {item.horizonDays || '—'} 日证据窗</span><h4>{item.instrumentName || item.instrumentCode}</h4><small>{item.instrumentCode}</small></div><div><em>{item.id ? '已冻结' : '未入验证账本'}</em><b>{actionLabels[item.action]}</b></div></header>
        <div className="shadow-numbers"><div><small>费用后中位优势</small><strong>{percent(item.expectedEdgeAfterCost)}</strong></div><div><small>P10 风险金额</small><strong>{signedMoney(item.p10RiskAmount)}</strong></div><div><small>P90 上行金额</small><strong>{signedMoney(item.p90UpsideAmount)}</strong></div><div><small>建议数量</small><strong>{item.suggestedQuantity} 股</strong></div></div>
        <p className="shadow-explanation">{item.explanation}</p>
        <div className="shadow-evidence"><div><b>支持证据</b>{item.evidence.length ? <ul>{item.evidence.map(value => <li key={value}>{value}</li>)}</ul> : <span>暂无有效量化证据</span>}</div><div className="blockers"><b>门禁 / 阻断</b>{item.blockers.length ? <ul>{item.blockers.map(value => <li key={value}>{value}</li>)}</ul> : <span>当前无额外阻断项</span>}</div></div>
        <footer><span>基准：{item.benchmark}</span><code>{item.modelVersion} · {shortFingerprint(item.dataFingerprint)}</code></footer>
      </article>)}
    </section> : null}

    {!loading && tab === 'validation' ? <section className="holdings-panel validation-ledger">
      <PanelHead eyebrow="OUTCOME AUDIT" title="建议验证账本" meta="策略动作 vs 同股持有" />
      {decisions.filter(item => item.id).length === 0 ? <Empty title="还没有可验证记录" text="只有证据完整并冻结成功的建议才进入这里；临时的证据不足不会冒充历史样本。" /> : <div className="validation-list">{decisions.filter(item => item.id).map(item => <article key={item.id}>
        <div><span>{item.decisionDate}</span><b>{item.instrumentName || item.instrumentCode}</b><small>{actionLabels[item.action]} · 到期 {item.maturityDate || '待确定'}</small></div>
        <div className="validation-status" data-status={item.validationStatus}><i />{item.validationStatus === 'PENDING' ? '等待到期' : item.validationStatus}</div>
        <div><small>策略收益</small><b>{item.strategyReturn == null ? '—' : percent(item.strategyReturn)}</b></div><div><small>持有收益</small><b>{item.holdReturn == null ? '—' : percent(item.holdReturn)}</b></div><div><small>增量价值</small><b>{item.incrementalReturn == null ? '—' : percent(item.incrementalReturn)}</b></div>
      </article>)}</div>}
      <div className="validation-note"><b>为什么现在大多显示“等待到期”？</b><p>建议必须等预测周期结束后，才用冻结当日证据和同一结算口径比较。样本不足时不显示虚假的胜率或准确率。</p></div>
    </section> : null}

    {formOpen ? <div className="modal-overlay" onMouseDown={event => { if (event.target === event.currentTarget) setFormOpen(false); }}><form className="modal holding-transaction-form" onSubmit={submit}>
      <header><div><p>LEDGER EVENT</p><h4>记录真实交易</h4><span>写入后不可编辑；如有错误，请在流水页追加冲正。</span></div><button type="button" aria-label="关闭交易表单" onClick={() => setFormOpen(false)}>×</button></header>
      <div className="holding-form-grid"><label>事件类型<select aria-label="事件类型" value={form.type} onChange={event => setForm({ ...form, type: event.target.value as TransactionType })}>{Object.entries(transactionLabels).filter(([key]) => key !== 'REVERSAL').map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><label>交易日期<input type="date" value={form.tradeDate} onChange={event => setForm({ ...form, tradeDate: event.target.value })} required /></label></div>
      {!isCashEvent(form.type) ? <label>股票代码<input aria-label="股票代码" value={form.code} onChange={event => setForm({ ...form, code: event.target.value })} placeholder="例如 600570" required /></label> : null}
      {needsQuantity(form.type) ? <div className="holding-form-grid"><label>数量（股）<input aria-label="数量" type="number" min="0" step="any" value={form.quantity} onChange={event => setForm({ ...form, quantity: event.target.value })} required /></label>{needsPrice(form.type) ? <label>成交价<input aria-label="成交价" type="number" min="0" step="0.001" value={form.price} onChange={event => setForm({ ...form, price: event.target.value })} required /></label> : null}</div> : null}
      {needsCash(form.type) ? <label>现金金额<input aria-label="现金金额" type="number" min="0" step="0.01" value={form.cashAmount} onChange={event => setForm({ ...form, cashAmount: event.target.value })} required /></label> : null}
      {needsPrice(form.type) ? <details><summary>交易费用（可选）</summary><div className="holding-form-grid fees"><label>佣金<input type="number" min="0" step="0.01" value={form.commission} onChange={event => setForm({ ...form, commission: event.target.value })} /></label><label>印花税<input type="number" min="0" step="0.01" value={form.stampDuty} onChange={event => setForm({ ...form, stampDuty: event.target.value })} /></label><label>过户费<input type="number" min="0" step="0.01" value={form.transferFee} onChange={event => setForm({ ...form, transferFee: event.target.value })} /></label><label>其他费用<input type="number" min="0" step="0.01" value={form.otherFee} onChange={event => setForm({ ...form, otherFee: event.target.value })} /></label></div></details> : null}
      <label>备注<textarea value={form.note} onChange={event => setForm({ ...form, note: event.target.value })} placeholder="记录交易原因或核对信息" /></label>
      <div className="holding-form-boundary">{form.type === 'OPENING_BALANCE' ? '用于补录系统启用前已持有的股票：只建立持仓数量和成本，不扣减账户现金。' : 'A 股买入必须为 100 股整数倍；卖出允许按真实余量记录。'}</div>
      <footer><button type="button" className="ghost-button" onClick={() => setFormOpen(false)}>取消</button><button type="submit">写入不可变账本</button></footer>
    </form></div> : null}
    {analysisTarget ? <HoldingAnalysisDrawer analysis={analysis} loading={analysisLoading} targetName={analysisTarget.instrumentName || analysisTarget.instrumentCode} targetCode={analysisTarget.instrumentCode} onClose={() => { setAnalysisTarget(undefined); setAnalysis(undefined); }} /> : null}
  </section>;
}

function Metric({ label, value, note, tone }: { label: string; value: string; note: string; tone?: string }) {
  return <article className="holdings-metric" data-tone={tone}><span>{label}</span><strong>{value}</strong><small>{note}</small></article>;
}

function PanelHead({ eyebrow, title, meta }: { eyebrow: string; title: string; meta: string }) {
  return <header className="holdings-panel-head"><div><span>{eyebrow}</span><h4>{title}</h4></div><small>{meta}</small></header>;
}

function Gauge({ label, value, warn, inverse }: { label: string; value: number; warn: boolean; inverse?: boolean }) {
  const normalized = Math.max(0, Math.min(1, value));
  return <div className="holding-gauge" data-warn={warn}><div><span>{label}</span><b>{percent(value)}</b></div><div className="holding-gauge-track"><i style={{ width: `${normalized * 100}%` }} /></div><small>{warn ? (inverse ? '低于 10% 现金缓冲' : '超过 65% 风险上限') : '位于预设风险带内'}</small></div>;
}

function Empty({ title, text }: { title: string; text: string }) {
  return <div className="holdings-lab-empty"><i>◇</i><h4>{title}</h4><p>{text}</p></div>;
}

function mergeDecisions(current: HoldingDecision[], history: HoldingDecision[]) {
  const merged = [...current];
  history.forEach(item => { if (!merged.some(value => value.id != null && value.id === item.id)) merged.push(item); });
  return merged;
}

function isCashEvent(type: TransactionType) { return type === 'CASH_DEPOSIT' || type === 'CASH_WITHDRAWAL'; }
function needsQuantity(type: TransactionType) { return ['OPENING_BALANCE', 'BUY', 'SELL', 'BONUS_SHARE'].includes(type); }
function needsPrice(type: TransactionType) { return ['OPENING_BALANCE', 'BUY', 'SELL'].includes(type); }
function needsCash(type: TransactionType) { return ['CASH_DIVIDEND', 'CASH_DEPOSIT', 'CASH_WITHDRAWAL'].includes(type); }
function numberOrUndefined(value: string) { return value === '' ? undefined : Number(value); }
function compact<T extends Record<string, unknown>>(value: T) { return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== undefined)); }
function uniqueRequestId() { return typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `holding-${Date.now()}-${Math.random().toString(16).slice(2)}`; }
function money(value?: number) { return `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`; }
function signedMoney(value: number) { return `${value >= 0 ? '+' : '-'}${money(Math.abs(value))}`; }
function percent(value: number) { return `${value >= 0 ? '' : '-'}${(Math.abs(value) * 100).toFixed(2)}%`; }
function fixed(value?: number) { return value == null ? '—' : value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 3 }); }
function dateTime(value: string) { return value.replace('T', ' ').slice(0, 16); }
function shortFingerprint(value: string) { return value === 'UNAVAILABLE' ? '无数据指纹' : value.replace('sha256:', '').slice(0, 10); }
