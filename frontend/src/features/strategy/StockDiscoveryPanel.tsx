import { StockDiscoveryMarketContextPanel } from './StockDiscoveryMarketContextPanel';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import type { StockDiscoveryMarketContext } from '../../shared/types/marketContext';
import { StockDiscoveryAccuracyReport, StockDiscoveryCandidate, StockDiscoveryEvidence, StockDiscoveryLatest, StockDiscoveryStatus } from './quantTypes';
import './BacktestAuditPanel.css';
import {
  CandidateFactorMatrix,
  DiscoveryFunnel,
  PanelCoverageMatrix,
  RiskReturnMap
} from './StockDiscoveryVisuals';
import { StockDiscoveryAccuracyPanel } from './StockDiscoveryAccuracyPanel';
import './StockDiscoveryMarketContext.css';
import { DirectionEvidence, NextSessionForecast, NextSessionOutcomeHistory } from './NextSessionForecast';
import { DirectionResearchComparison } from './DirectionResearchComparison';
import { StrengthDiscoveryPanel } from './StrengthDiscoveryPanel';
import './StockDiscoveryWorkspace.css';

type Toast = (message: string, type?: 'success' | 'error' | 'info') => void;

const conclusions: Record<string, string> = {
  ROBUST: '稳健通过', CONDITIONALLY_EFFECTIVE: '条件有效',
  NO_CLEAR_ADVANTAGE: '无明显优势', INSUFFICIENT_DATA: '数据不足'
};

const researchTiers: Record<string, string> = {
  ACTIONABLE: '严格通过', CONDITIONAL: '条件研究', WATCH: '观察名单'
};

function pct(value?: number, digits = 1) {
  return value == null ? '—' : `${(value * 100).toFixed(digits)}%`;
}

function money(value?: number) {
  if (value == null) return '—';
  if (Math.abs(value) >= 100000000) return `${(value / 100000000).toFixed(1)} 亿`;
  if (Math.abs(value) >= 10000) return `${(value / 10000).toFixed(0)} 万`;
  return `¥${value.toFixed(0)}`;
}

function constituentSource(value?: string) {
  if (value === 'TONGHUASHUN') return '同花顺成分';
  if (value === 'EASTMONEY') return '东方财富补全';
  if (value === 'LOCAL_SNAPSHOT') return '完整缓存';
  return value ?? '来源待确认';
}

function constituentQuality(value?: string) {
  if (value === 'COMPLETE') return '完整直取';
  if (value === 'CACHED_COMPLETE') return '完整缓存';
  if (value === 'SUPPLEMENTED_COMPLETE' || value === 'MIXED_COMPLETE') return '完整补全';
  if (value === 'PARTIAL') return '部分板块已跳过';
  return '质量待确认';
}

function CandidateCard({ evidence, candidate, held, onOpenResearch }: {
  evidence: StockDiscoveryEvidence;
  candidate?: StockDiscoveryCandidate;
  held?: boolean;
  onOpenResearch: (code: string) => void;
}) {
  const tier = evidence.research_tier ?? (evidence.qualified ? 'ACTIONABLE' : 'WATCH');
  return <article className="discovery-stock-row">
    <div className="discovery-stock-summary">
      <div className="discovery-stock-name"><small>#{String(evidence.relative_rank ?? evidence.final_rank ?? '—').padStart(2, '0')} · {candidate ? `${candidate.code}.${candidate.market}` : evidence.code}</small><h4>{candidate?.name ?? evidence.code}{held && <em className="discovery-held-badge">真实持有</em>}</h4><span>{candidate?.sector_names.join(' / ') || '行业待确认'}</span></div>
      <div className="discovery-stock-number"><span>未来 5 日上涨概率</span><strong>{pct(evidence.calibrated_probability)}</strong><small>保守下界 {pct(evidence.probability_lower_bound)}</small></div>
      <div className="discovery-stock-number"><span>一手资金</span><strong>{money(candidate?.lot_cost)}</strong><small>现价 {candidate ? `¥${candidate.price.toFixed(2)}` : '—'}</small></div>
      <div className="discovery-stock-status"><b>{researchTiers[tier] ?? tier}</b><span>{conclusions[evidence.conclusion] ?? evidence.conclusion}</span>{evidence.backtest_audit_status && <small>{evidence.backtest_audit_status === 'PASS' ? '双引擎一致' : evidence.backtest_audit_status === 'WARNING' ? '账本有差异' : '影子待复核'}</small>}</div>
      <button type="button" onClick={() => onOpenResearch(evidence.code)}>进入单股完整研究</button>
    </div>
    <details className="discovery-stock-details"><summary>查看预测、排序证据与风险</summary>
      <NextSessionForecast prediction={evidence.forecast_report?.nextSession} compact />
      <dl className="discovery-stock-metrics">
        <div><dt>锁定样本准确率</dt><dd>{pct(evidence.locked_accuracy)}</dd></div>
        <div><dt>Brier 技能分</dt><dd>{evidence.brier_skill_score.toFixed(3)}</dd></div>
        <div><dt>风险调整收益</dt><dd>{evidence.risk_adjusted_return.toFixed(2)}</dd></div>
        <div><dt>参数稳定性</dt><dd>{pct(evidence.stability_score)}</dd></div>
        <div><dt>最大回撤</dt><dd>{pct(evidence.max_drawdown)}</dd></div>
      </dl>
      <div className="discovery-stock-evidence"><section><b>排序依据</b>{evidence.evidence.map(item => <p key={item}>{item}</p>)}</section><section><b>风险边界</b>{evidence.risks.map(item => <p key={item}>{item}</p>)}</section></div>
    </details>
  </article>;
}

export function StockDiscoveryPanel({ addToast, setMessage, onOpenResearch, marketContext }: {
  addToast: Toast;
  setMessage: (message: string) => void;
  onOpenResearch?: (code: string) => void;
  marketContext?: StockDiscoveryMarketContext;
}) {
  const [view, setView] = useState<'candidates' | 'review' | 'diagnostics'>('candidates');
  const [pool, setPool] = useState<'strength' | 'trend'>('strength');
  const [reviewHorizon, setReviewHorizon] = useState<'next' | 'five'>('next');
  const [latest, setLatest] = useState<StockDiscoveryLatest>();
  const [runningStatus, setRunningStatus] = useState('EMPTY');
  const [statusDetail, setStatusDetail] = useState<StockDiscoveryStatus>();
  const [failed, setFailed] = useState(false);
  const [accuracy, setAccuracy] = useState<StockDiscoveryAccuracyReport>();
  const [accuracyFailed, setAccuracyFailed] = useState(false);
  const [heldCodes, setHeldCodes] = useState<Set<string>>(new Set());

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const [value, status] = await Promise.all([
          api<StockDiscoveryLatest>('/api/quant/stock-discoveries/latest'),
          api<StockDiscoveryStatus>('/api/quant/stock-discoveries/status')
        ]);
        if (!cancelled) {
          setLatest(value); setRunningStatus(status.status); setStatusDetail(status); setFailed(false);
          setMessage(value && 'report' in value ? `股票发现已同步至 ${value.report.as_of_date}` : '等待首份自动股票发现结果');
        }
        try {
          const accuracyValue = await api<StockDiscoveryAccuracyReport>('/api/quant/stock-discoveries/accuracy');
          if (!cancelled) { setAccuracy(accuracyValue); setAccuracyFailed(false); }
        } catch {
          if (!cancelled) { setAccuracyFailed(true); }
        }
        try {
          const account = await api<{ positions: Array<{ instrumentCode: string }> }>('/api/strategy/stock-account');
          if (!cancelled) setHeldCodes(new Set(account.positions.map(item => item.instrumentCode.slice(0, 6))));
        } catch {
          if (!cancelled) setHeldCodes(new Set());
        }
      } catch (error) {
        if (!cancelled) { setFailed(true); addToast(error instanceof Error ? error.message : '股票发现结果加载失败', 'error'); }
      }
    };
    void load();
    const timer = window.setInterval(load, 30000);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, []);

  const report = latest && 'report' in latest ? latest.report : undefined;
  const run = latest && 'run' in latest ? latest.run : undefined;
  const candidates = useMemo(() => new Map(report?.candidates.map(item => [item.code, item]) ?? []), [report]);
  const finalCodes = useMemo(() => new Set(report?.final_candidates.map(item => item.code) ?? []), [report]);
  const researchCandidates = useMemo(() => {
    if (report?.stable_candidates !== undefined) {
      return report.stable_candidates;
    }
    if (report?.relative_candidates?.length) {
      return report.relative_candidates;
    }
    return report?.final_candidates ?? [];
  }, [report]);

  const navigation = <nav className="discovery-view-nav" aria-label="股票发现视图">
    <button type="button" aria-pressed={view === 'candidates'} onClick={() => setView('candidates')}>候选股票<span>先看名单与研究依据</span></button>
    <button type="button" aria-pressed={view === 'review'} onClick={() => setView('review')}>预测复盘<span>对照冻结预测与真实结果</span></button>
    <button type="button" aria-pressed={view === 'diagnostics'} onClick={() => setView('diagnostics')}>研究诊断<span>数据覆盖、因子与模型实验</span></button>
  </nav>;
  const review = <section className="discovery-review-view" aria-label="预测复盘内容">
    <div className="discovery-view-intro"><h4>分清预测周期，再比较真实结果</h4><p>次日收盘方向与 5 日持有期独立核验；两种命中率不可混用。</p></div>
    <div className="discovery-switch" role="group" aria-label="复盘周期">
      <button type="button" aria-pressed={reviewHorizon === 'next'} onClick={() => setReviewHorizon('next')}>次日收盘</button>
      <button type="button" aria-pressed={reviewHorizon === 'five'} onClick={() => setReviewHorizon('five')}>5 日持有期</button>
    </div>
    {reviewHorizon === 'next' ? <NextSessionOutcomeHistory /> : accuracy
      ? <StockDiscoveryAccuracyPanel report={accuracy} />
      : <section className="discovery-accuracy-unavailable" data-failed={accuracyFailed || undefined}><span>FORWARD OUTCOME</span><strong>{accuracyFailed ? '真实结果评测暂时不可用' : '正在读取真实预测结果'}</strong><p>{accuracyFailed ? '当天选股结果不受影响；系统会继续独立结算到期样本，下次轮询自动恢复。' : '这里会展示冻结预测到期后的命中率、概率校准和模型赛马。'}</p></section>}
  </section>;

  const heading = <header className="discovery-workspace-heading"><div><h3>股票发现</h3><p>收盘研究名单用于后续观察；尾盘买入请使用“尾盘与盘后”策略。</p></div><div className="discovery-batch-date"><span>{runningStatus === 'RUNNING' ? '新批次计算中 · 当前展示' : '研究日期'}</span><strong>{report?.as_of_date ?? '等待首份结果'}</strong><small>{report ? report.quality_status === 'FRESH_PRIMARY' ? '主数据源新鲜' : '备用源结果，请核对覆盖' : '收盘后自动研究'}</small></div></header>;
  if (!report) {
    const businessFailed = statusDetail?.businessStatus === 'FAILED';
    const delivered = statusDetail?.deliveryStatus === 'DELIVERED';
    return <section className="stock-discovery discovery-workspace">
      {heading}{navigation}
      {view === 'review' ? review : <section className="discovery-empty-state" data-failed={businessFailed || undefined}>
        <h3>{failed ? '暂时无法读取发现结果' : businessFailed ? (delivered ? '任务已送达，业务计算失败' : '任务等待重新投递') : '第一份收盘研究正在路上'}</h3>
        <p>{businessFailed ? statusDetail?.errorMessage ?? '股票发现业务计算暂未完成' : '系统在交易日收盘后扫描热门行业和强势事件，分别展示研究名单与严格合格结果。'}</p>
        <div><i data-status={runningStatus} /><b>{runningStatus === 'RUNNING' ? '后台正在深度预测' : businessFailed && statusDetail?.retryPending ? '系统会自动重试；热点雷达不受本次失败影响' : '每天 15:30 自动执行，启动时自动补跑'}</b></div>
        {businessFailed && statusDetail?.nextScheduledAt ? <small>下次自动调度：{statusDetail.nextScheduledAt}</small> : null}
      </section>}
      {view === 'candidates' && marketContext && <details className="discovery-context-disclosure"><summary>市场背景 · {marketContext.transitionLabel}</summary><StockDiscoveryMarketContextPanel context={marketContext} /></details>}
    </section>;
  }

  const activePool = report.strength_watchlist === undefined ? 'trend' : pool;
  return <section className="stock-discovery discovery-workspace">
    {heading}
    <div className="discovery-freshness" aria-label="研究结果日期">
      <span>当前展示 {report.as_of_date} 的已完成研究 · 生成于 {report.retrieved_at}。</span>
      {failed && <strong role="status">最新结果读取失败，当前保留上次成功读取的名单。</strong>}
      {statusDetail?.businessDate && (statusDetail.businessDate > report.as_of_date || runningStatus === 'RUNNING') &&
        <strong>最新任务日期 {statusDetail.businessDate}，{runningStatus === 'RUNNING' ? '正在计算' : '尚未产生新的成功结果'}；当前名单仍属于上述日期。</strong>}
    </div>
    {navigation}
    {view === 'candidates' && <section className="discovery-candidates-view" aria-label="候选股票内容">
      <div className="discovery-candidate-overview">
        <div><span>强势事件观察</span><strong>{report.strength_watchlist?.length ?? '—'}<small>只</small></strong></div>
        <div><span>趋势研究名单</span><strong>{researchCandidates.length}<small>只</small></strong></div>
        <div><span>通过原交易周期门禁</span><strong>{report.final_candidates.length}<small>只</small></strong></div>
        <p>{report.final_candidates.length ? '通过门禁仅对应原交易周期，不代表尾盘策略已验证。' : '本轮暂无通过原交易周期门禁的股票。观察名单仅供研究，不代表已有预测优势。'}</p>
      </div>
      <div className="discovery-context-notes">
        {marketContext && <details className="discovery-context-disclosure"><summary>市场背景 · {marketContext.transitionLabel}</summary><StockDiscoveryMarketContextPanel context={marketContext} /></details>}
        {!!report.warnings.length && <details className="discovery-context-disclosure"><summary>数据覆盖与提示 · {report.warnings.length} 条</summary>{report.warnings.map(warning => <p key={warning}>{warning}</p>)}</details>}
      </div>
      <div className="discovery-pool-heading"><div className="discovery-switch" role="group" aria-label="候选名单类型">
        <button type="button" disabled={report.strength_watchlist === undefined} aria-pressed={activePool === 'strength'} onClick={() => setPool('strength')}>短期强势</button>
        <button type="button" aria-pressed={activePool === 'trend'} onClick={() => setPool('trend')}>稳健趋势</button>
      </div><p>{activePool === 'strength' ? '事件发生后的观察池 · 历史频率不等于明日获利概率' : '原交易周期研究 · 默认仅展示核心指标'}</p></div>
      {report.strength_watchlist === undefined && <p className="discovery-muted-note">这是旧版批次，尚未包含独立强势股扫描和漏选审计。</p>}
      {activePool === 'strength' ? <StrengthDiscoveryPanel report={report} onOpenResearch={onOpenResearch} /> : <section className="discovery-trend-list" aria-label="稳健趋势名单">
        <div className="discovery-view-intro"><h4>{report.stable_candidates !== undefined ? '稳健趋势研究' : '相对优势 Top 5'}</h4><p>从深度候选中排序；展开单只股票可查看次日预测和完整风险依据。</p></div>
        <div className="discovery-trend-gate"><strong>严格可行动 {report.final_candidates.length}</strong><span>{report.final_candidates.length ? `${report.final_candidates.map(item => candidates.get(item.code)?.name ?? item.code).join('、')}通过原交易周期门禁。` : '本轮无人通过绝对门禁，以下为观察名单。'}</span></div>
        {researchCandidates.length ? researchCandidates.map(item => <CandidateCard key={item.code} evidence={item} candidate={candidates.get(item.code)} held={heldCodes.has(item.code)} onOpenResearch={code => onOpenResearch?.(code)} />)
          : <div className="discovery-no-edge"><strong>深度样本暂不可用</strong><p>本批次没有形成可比较的深度证据，系统不会输出空洞排序。</p></div>}
      </section>}
    </section>}
    {view === 'review' && review}
    {view === 'diagnostics' && <section className="discovery-diagnostics-view" aria-label="研究诊断内容">
      <div className="discovery-view-intro"><h4>检查数据与模型，不混入候选结论</h4><p>历史实验、训练覆盖和因子图表仅用于研究诊断，不代表下一交易日的预测表现。</p></div>
    <DiscoveryFunnel funnel={report.funnel} />
    <DirectionResearchComparison />
    {report.joint_training && <section className="next-session-forecast" aria-label="联合训练与预测目标">
        <strong>宽池学习 · 候选范围保持独立</strong>
        <p>训练池 {report.joint_training.trainingUniverseCount ?? report.joint_training.universeCount} 只 · 展示候选 {report.joint_training.displayUniverseCount} 只 · {report.joint_training.featureCount} 个因子 · 训练期行业覆盖 {pct(report.joint_training.industryCoverage)}</p>
        <p>选股学习{report.joint_training.rankingTarget === 'MARKET_RESIDUAL' ? '扣除市场影响、按历史波动率调整的个股强弱' : '绝对收盘涨跌'}；单股页面继续预测实际收盘涨跌。</p>
        <p>历史联合 Brier {report.joint_training.pooledBrierScore.toFixed(4)} / 基线 {report.joint_training.baselineBrierScore.toFixed(4)} · 展示池 Rank IC {report.joint_training.rankIc.toFixed(3)}</p>
        {report.joint_training.evidenceKind === 'RETROSPECTIVE' && <p>当前为历史回归对照，尚不作为新方法的全新样本外证据；下一交易日有效预测仍需完整收盘行情。</p>}
        {report.joint_training.directionEvaluation && <details><summary>查看历史方向与校准诊断</summary><DirectionEvidence audit={report.joint_training.directionEvaluation} /></details>}
      </section>}

    <section className="discovery-provenance" aria-label="股票发现数据来源与交易范围">
      <article><span>RANKING AUTHORITY</span><strong>{report.source_family === 'EASTMONEY_EVENTS' ? '仅事件池，行业榜不可用' : report.strength_watchlist ? '同花顺行业榜 + 独立事件池' : '同花顺行业榜'}</strong><small><b>净流入降序</b> · 行业板块</small></article>
      <article><span>CONSTITUENT EVIDENCE</span><strong>{(report.constituent_source_families ?? []).map(constituentSource).join(' + ') || '来源待确认'}</strong><small>{constituentQuality(report.constituent_quality_status)}</small></article>
      <article><span>ACCOUNT SCOPE</span><strong>权限范围剔除 {report.funnel.scope_excluded_count ?? 0} 只</strong><small>科创板 {report.funnel.star_market_excluded_count ?? 0} · 北交所 {report.funnel.beijing_market_excluded_count ?? 0}</small></article>
    </section>
    <div className="discovery-analysis-grid">
      <RiskReturnMap evidence={report.deep_evidence} candidates={report.candidates} finalCodes={finalCodes} />
      <CandidateFactorMatrix evidence={researchCandidates} candidates={report.candidates} />
    </div>

    <PanelCoverageMatrix evidence={report.deep_evidence} candidates={report.candidates} />
      <div className="discovery-diagnostic-context">
        <section><header><span>HOT SECTORS</span><b>同花顺 · 净流入降序</b></header>{report.sectors.map(sector => <div className="discovery-sector" key={`${sector.category}-${sector.code}`}><i>{String(sector.source_rank).padStart(2, '0')}</i><p><strong>{sector.name}</strong><small>{constituentSource(sector.constituent_source_family)} · {constituentQuality(sector.constituent_quality_status)}</small><em>成分覆盖 {sector.resolved_constituent_count ?? '—'} / {sector.expected_constituent_count ?? '—'}</em></p><div><b>{money(sector.main_net_inflow)}</b>{sector.change_pct != null && <small>{sector.change_pct > 0 ? '+' : ''}{sector.change_pct.toFixed(2)}%</small>}</div></div>)}</section>
        <section className="discovery-run-note"><span>RUN DISCIPLINE</span><dl><div><dt>预算上限</dt><dd>¥{report.budget.toLocaleString()}</dd></div><div><dt>深度预测耗时</dt><dd>{(report.duration_ms / 1000).toFixed(1)}s</dd></div><div><dt>候选基准</dt><dd>同股买入持有</dd></div><div><dt>批次编号</dt><dd>#{run?.id ?? '—'}</dd></div></dl><p>排名用于研究优先级，不构成交易建议。概率会在未来到期后持续以真实结果校准。</p></section>
        {report.warnings.length > 0 && <section className="discovery-warnings"><span>DATA NOTES</span>{report.warnings.slice(0, 5).map(item => <p key={item}>{item}</p>)}</section>}
      </div>
    </section>}
  </section>;
}
