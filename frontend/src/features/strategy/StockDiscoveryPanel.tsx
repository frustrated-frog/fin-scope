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
import { NextSessionForecast, NextSessionOutcomeHistory } from './NextSessionForecast';

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
  return <article className="discovery-candidate" data-health={evidence.health_status} data-tier={tier}>
    <div className="discovery-rank"><span>#{String(evidence.relative_rank ?? evidence.final_rank ?? '—').padStart(2, '0')}</span><i /></div>
    <div className="discovery-candidate-main">
      <header><div><small>{candidate ? `${candidate.code}.${candidate.market}` : evidence.code}</small><h4>{candidate?.name ?? evidence.code}{held ? <em className="discovery-held-badge">真实持有</em> : null}</h4></div><div className="discovery-verdicts"><em data-tier={tier}>{researchTiers[tier]}</em><b>{conclusions[evidence.conclusion] ?? evidence.conclusion}</b>{evidence.backtest_audit_status && <span data-status={evidence.backtest_audit_status}>{evidence.backtest_audit_status === 'PASS' ? '双引擎一致' : evidence.backtest_audit_status === 'WARNING' ? '账本有差异' : '影子待复核'}</span>}</div></header>
      <NextSessionForecast prediction={evidence.forecast_report?.nextSession} compact />
      <div className="discovery-probability"><div><span>未来 5 日上涨概率</span><strong>{pct(evidence.calibrated_probability)}</strong></div><i aria-hidden="true"><b style={{ width: pct(evidence.calibrated_probability) }} /></i><small>原有交易周期研究 · 保守下界 {pct(evidence.probability_lower_bound)}</small></div>
      <dl>
        <div><dt>锁定样本准确率</dt><dd>{pct(evidence.locked_accuracy)}</dd></div>
        <div><dt>Brier 技能分</dt><dd>{evidence.brier_skill_score.toFixed(3)}</dd></div>
        <div><dt>风险调整收益</dt><dd>{evidence.risk_adjusted_return.toFixed(2)}</dd></div>
        <div><dt>参数稳定性</dt><dd>{pct(evidence.stability_score)}</dd></div>
        <div><dt>最大回撤</dt><dd>{pct(evidence.max_drawdown)}</dd></div>
        <div><dt>一手资金</dt><dd>{money(candidate?.lot_cost)}</dd></div>
      </dl>
      <footer><div>{(candidate?.sector_names ?? []).map(name => <span key={name}>{name}</span>)}</div><div className="discovery-candidate-actions"><small>现价 {candidate ? `¥${candidate.price.toFixed(2)}` : '—'}</small><button type="button" onClick={() => onOpenResearch(evidence.code)}>进入单股完整研究</button></div></footer>
      {(evidence.evidence.length > 0 || evidence.risks.length > 0) && <details><summary>查看排序证据与风险边界</summary><div className="discovery-evidence"><section><b>为什么相对领先</b>{evidence.evidence.map(item => <p key={item}>{item}</p>)}</section><section><b>必须同时看到</b>{evidence.risks.map(item => <p key={item}>{item}</p>)}</section></div></details>}
    </div>
  </article>;
}

export function StockDiscoveryPanel({ addToast, setMessage, onOpenResearch, marketContext }: {
  addToast: Toast;
  setMessage: (message: string) => void;
  onOpenResearch?: (code: string) => void;
  marketContext?: StockDiscoveryMarketContext;
}) {
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
    if (report?.relative_candidates?.length) return report.relative_candidates;
    return report?.final_candidates ?? [];
  }, [report]);

  if (!report) {
    const businessFailed = statusDetail?.businessStatus === 'FAILED';
    const delivered = statusDetail?.deliveryStatus === 'DELIVERED';
    return <section className="stock-discovery discovery-empty-state" data-failed={businessFailed || undefined}>
      <span>AUTOMATED MARKET SCAN</span>
      <h3>{failed ? '暂时无法读取发现结果' : businessFailed ? (delivered ? '任务已送达，业务计算失败' : '任务等待重新投递') : '第一份收盘研究正在路上'}</h3>
      <p>{businessFailed ? statusDetail?.errorMessage ?? '股票发现业务计算暂未完成' : '系统会在交易日收盘后自动读取热门板块、校验一手资金约束、量化全部候选，并只保留通过严格门禁的前五名。你不需要点击运行。'}</p>
      <div><i data-status={runningStatus} /><b>{runningStatus === 'RUNNING' ? '后台正在深度预测' : businessFailed && statusDetail?.retryPending ? '系统会自动重试；热点雷达不受本次失败影响' : '每天 15:30 自动执行，启动时自动补跑'}</b></div>
      {businessFailed && statusDetail?.nextScheduledAt ? <small>下次自动调度：{statusDetail.nextScheduledAt}</small> : null}
    </section>;
  }

  return <section className="stock-discovery">
    <header className="discovery-head">
      <div><p>STOCK DISCOVERY / AUTOPILOT</p><h3>市场已经替你跑完一遍</h3><span>不是按股价挑便宜股票。系统先量化所有买得起一手的热门板块候选，再从深度预测中留下真正更有优势的结果。</span></div>
      <aside><i data-status={runningStatus} /><small>{runningStatus === 'RUNNING' ? '新批次计算中' : 'LATEST VERIFIED CLOSE'}</small><strong>{report.as_of_date}</strong><span>{report.source_family} · {report.quality_status === 'FRESH_PRIMARY' ? '主数据源新鲜' : '备用源结果'}</span></aside>
    </header>

    {marketContext && <section className="discovery-market-context" data-risk={marketContext.riskPosture}
      aria-label="来自市场转折雷达的研究上下文">
      <header><span>来自市场转折雷达</span><strong>{marketContext.transitionLabel}</strong>
        <p>{marketContext.summary}</p><time>{marketContext.businessDate ?? '当前交易日'}</time></header>
      <dl>
        <div><dt>风险姿态</dt><dd>{{ OFFENSIVE: '进攻观察', BALANCED: '均衡试错', DEFENSIVE: '防守优先' }[marketContext.riskPosture]}</dd></div>
        <div><dt>优先研究</dt><dd>{marketContext.preferredSectors.join(' · ') || '等待行业确认'}</dd></div>
        <div><dt>谨慎方向</dt><dd>{marketContext.avoidSectors.join(' · ') || '当前无强制回避'}</dd></div>
        <div><dt>参与纪律</dt><dd>{{ CONFIRMATION_ALLOWED: '确认后参与', PULLBACK_ONLY: '只等回撤确认', NO_CHASING: '不追高' }[marketContext.chasePolicy]}</dd></div>
      </dl>
    </section>}

    <DiscoveryFunnel funnel={report.funnel} />
    <NextSessionOutcomeHistory />

    <section className="discovery-provenance" aria-label="股票发现数据来源与交易范围">
      <article><span>RANKING AUTHORITY</span><strong>同花顺唯一热榜</strong><small><b>净流入降序</b> · 行业板块</small></article>
      <article><span>CONSTITUENT EVIDENCE</span><strong>{(report.constituent_source_families ?? []).map(constituentSource).join(' + ') || '来源待确认'}</strong><small>{constituentQuality(report.constituent_quality_status)}</small></article>
      <article><span>ACCOUNT SCOPE</span><strong>权限范围剔除 {report.funnel.scope_excluded_count ?? 0} 只</strong><small>科创板 {report.funnel.star_market_excluded_count ?? 0} · 北交所 {report.funnel.beijing_market_excluded_count ?? 0}</small></article>
    </section>

    {accuracy
      ? <StockDiscoveryAccuracyPanel report={accuracy} />
      : <section className="discovery-accuracy-unavailable" data-failed={accuracyFailed || undefined}><span>FORWARD OUTCOME</span><strong>{accuracyFailed ? '真实结果评测暂时不可用' : '正在读取真实预测结果'}</strong><p>{accuracyFailed ? '当天选股结果不受影响；系统会继续独立结算到期样本，下次轮询自动恢复。' : '这里会展示冻结预测到期后的命中率、概率校准和模型赛马。'}</p></section>}

    <div className="discovery-analysis-grid">
      <RiskReturnMap evidence={report.deep_evidence} candidates={report.candidates} finalCodes={finalCodes} />
      <CandidateFactorMatrix evidence={researchCandidates} candidates={report.candidates} />
    </div>

    <PanelCoverageMatrix evidence={report.deep_evidence} candidates={report.candidates} />

    <div className="discovery-layout">
      <main>
        <div className="discovery-section-title"><div><span>RELATIVE RESEARCH SHORTLIST</span><h4>相对优势 Top 5</h4></div><p>从全部深度候选中排序，不把相对领先包装成买入结论</p></div>
        <div className="discovery-action-gate" data-empty={!report.final_candidates.length || undefined}><div><span>STRICT ACTION GATE</span><strong>严格可行动 {report.final_candidates.length}</strong></div><p>{report.final_candidates.length ? `其中 ${report.final_candidates.map(item => candidates.get(item.code)?.name ?? item.code).join('、')} 通过全部概率、回测、稳定性门禁。` : '本轮无人通过绝对门禁；下方仍展示最值得继续研究的五只，但全部不是买入信号。'}</p></div>
        <div className="discovery-final-list">{researchCandidates.length
          ? researchCandidates.map(item => <CandidateCard key={item.code} evidence={item} candidate={candidates.get(item.code)} held={heldCodes.has(item.code)} onOpenResearch={code => onOpenResearch?.(code)} />)
          : <div className="discovery-no-edge"><strong>深度样本暂不可用</strong><p>本批次没有形成可比较的深度证据，系统不会输出空洞排序。</p></div>}</div>
      </main>
      <aside className="discovery-context">
        <section><header><span>HOT SECTORS</span><b>同花顺 · 净流入降序</b></header>{report.sectors.map(sector => <div className="discovery-sector" key={`${sector.category}-${sector.code}`}><i>{String(sector.source_rank).padStart(2, '0')}</i><p><strong>{sector.name}</strong><small>{constituentSource(sector.constituent_source_family)} · {constituentQuality(sector.constituent_quality_status)}</small><em>成分覆盖 {sector.resolved_constituent_count ?? '—'} / {sector.expected_constituent_count ?? '—'}</em></p><div><b>{money(sector.main_net_inflow)}</b>{sector.change_pct != null && <small>{sector.change_pct > 0 ? '+' : ''}{sector.change_pct.toFixed(2)}%</small>}</div></div>)}</section>
        <section className="discovery-run-note"><span>RUN DISCIPLINE</span><dl><div><dt>预算上限</dt><dd>¥{report.budget.toLocaleString()}</dd></div><div><dt>深度预测耗时</dt><dd>{(report.duration_ms / 1000).toFixed(1)}s</dd></div><div><dt>候选基准</dt><dd>同股买入持有</dd></div><div><dt>批次编号</dt><dd>#{run?.id ?? '—'}</dd></div></dl><p>排名用于研究优先级，不构成交易建议。概率会在未来到期后持续以真实结果校准。</p></section>
        {report.warnings.length > 0 && <section className="discovery-warnings"><span>DATA NOTES</span>{report.warnings.slice(0, 5).map(item => <p key={item}>{item}</p>)}</section>}
      </aside>
    </div>
  </section>;
}
