import { useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import { StockDiscoveryCandidate, StockDiscoveryEvidence, StockDiscoveryLatest, StockDiscoveryStatus } from './quantTypes';
import './BacktestAuditPanel.css';
import {
  CandidateFactorMatrix,
  DiscoveryFunnel,
  PanelCoverageMatrix,
  RiskReturnMap
} from './StockDiscoveryVisuals';

type Toast = (message: string, type?: 'success' | 'error' | 'info') => void;

const conclusions: Record<string, string> = {
  ROBUST: '稳健通过', CONDITIONALLY_EFFECTIVE: '条件有效',
  NO_CLEAR_ADVANTAGE: '无明显优势', INSUFFICIENT_DATA: '数据不足'
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

function CandidateCard({ evidence, candidate, onOpenResearch }: {
  evidence: StockDiscoveryEvidence;
  candidate?: StockDiscoveryCandidate;
  onOpenResearch: (code: string) => void;
}) {
  return <article className="discovery-candidate" data-health={evidence.health_status}>
    <div className="discovery-rank"><span>#{String(evidence.final_rank ?? '—').padStart(2, '0')}</span><i /></div>
    <div className="discovery-candidate-main">
      <header><div><small>{candidate ? `${candidate.code}.${candidate.market}` : evidence.code}</small><h4>{candidate?.name ?? evidence.code}</h4></div><div className="discovery-verdicts"><b>{conclusions[evidence.conclusion] ?? evidence.conclusion}</b>{evidence.backtest_audit_status && <span data-status={evidence.backtest_audit_status}>{evidence.backtest_audit_status === 'PASS' ? '双引擎一致' : evidence.backtest_audit_status === 'WARNING' ? '账本有差异' : '影子待复核'}</span>}</div></header>
      <div className="discovery-probability"><div><span>未来 5 日上涨概率</span><strong>{pct(evidence.calibrated_probability)}</strong></div><i aria-hidden="true"><b style={{ width: pct(evidence.calibrated_probability) }} /></i><small>保守下界 {pct(evidence.probability_lower_bound)} · 不是确定性收益承诺</small></div>
      <dl>
        <div><dt>锁定样本准确率</dt><dd>{pct(evidence.locked_accuracy)}</dd></div>
        <div><dt>Brier 技能分</dt><dd>{evidence.brier_skill_score.toFixed(3)}</dd></div>
        <div><dt>风险调整收益</dt><dd>{evidence.risk_adjusted_return.toFixed(2)}</dd></div>
        <div><dt>参数稳定性</dt><dd>{pct(evidence.stability_score)}</dd></div>
        <div><dt>最大回撤</dt><dd>{pct(evidence.max_drawdown)}</dd></div>
        <div><dt>一手资金</dt><dd>{money(candidate?.lot_cost)}</dd></div>
      </dl>
      <footer><div>{(candidate?.sector_names ?? []).map(name => <span key={name}>{name}</span>)}</div><div className="discovery-candidate-actions"><small>现价 {candidate ? `¥${candidate.price.toFixed(2)}` : '—'}</small><button type="button" onClick={() => onOpenResearch(evidence.code)}>进入单股完整研究</button></div></footer>
      {(evidence.evidence.length > 0 || evidence.risks.length > 0) && <details><summary>查看入选证据与风险边界</summary><div className="discovery-evidence"><section><b>为什么进入前五</b>{evidence.evidence.map(item => <p key={item}>{item}</p>)}</section><section><b>必须同时看到</b>{evidence.risks.map(item => <p key={item}>{item}</p>)}</section></div></details>}
    </div>
  </article>;
}

export function StockDiscoveryPanel({ addToast, setMessage, onOpenResearch }: {
  addToast: Toast;
  setMessage: (message: string) => void;
  onOpenResearch?: (code: string) => void;
}) {
  const [latest, setLatest] = useState<StockDiscoveryLatest>();
  const [runningStatus, setRunningStatus] = useState('EMPTY');
  const [statusDetail, setStatusDetail] = useState<StockDiscoveryStatus>();
  const [failed, setFailed] = useState(false);

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

    <DiscoveryFunnel funnel={report.funnel} />

    <section className="discovery-provenance" aria-label="股票发现数据来源与交易范围">
      <article><span>RANKING AUTHORITY</span><strong>同花顺唯一热榜</strong><small><b>净流入降序</b> · 行业板块</small></article>
      <article><span>CONSTITUENT EVIDENCE</span><strong>{(report.constituent_source_families ?? []).map(constituentSource).join(' + ') || '来源待确认'}</strong><small>{constituentQuality(report.constituent_quality_status)}</small></article>
      <article><span>ACCOUNT SCOPE</span><strong>权限范围剔除 {report.funnel.scope_excluded_count ?? 0} 只</strong><small>科创板 {report.funnel.star_market_excluded_count ?? 0} · 北交所 {report.funnel.beijing_market_excluded_count ?? 0}</small></article>
    </section>

    <div className="discovery-analysis-grid">
      <RiskReturnMap evidence={report.deep_evidence} candidates={report.candidates} finalCodes={finalCodes} />
      <CandidateFactorMatrix evidence={report.final_candidates} candidates={report.candidates} />
    </div>

    <PanelCoverageMatrix evidence={report.deep_evidence} candidates={report.candidates} />

    <div className="discovery-layout">
      <main>
        <div className="discovery-section-title"><div><span>FINAL SHORTLIST</span><h4>有优势才出现，最多五只</h4></div><p>{report.final_candidates.length ? `本轮 ${report.final_candidates.length} 只通过最终门禁` : '本轮没有股票通过最终门禁，这也是有效结论'}</p></div>
        <div className="discovery-final-list">{report.final_candidates.length
          ? report.final_candidates.map(item => <CandidateCard key={item.code} evidence={item} candidate={candidates.get(item.code)} onOpenResearch={code => onOpenResearch?.(code)} />)
          : <div className="discovery-no-edge"><strong>宁可空缺，也不凑满前五</strong><p>当前候选在概率质量、样本外证据或稳定性上未形成足够优势。</p></div>}</div>
      </main>
      <aside className="discovery-context">
        <section><header><span>HOT SECTORS</span><b>同花顺 · 净流入降序</b></header>{report.sectors.map(sector => <div className="discovery-sector" key={`${sector.category}-${sector.code}`}><i>{String(sector.source_rank).padStart(2, '0')}</i><p><strong>{sector.name}</strong><small>{constituentSource(sector.constituent_source_family)} · {constituentQuality(sector.constituent_quality_status)}</small><em>成分覆盖 {sector.resolved_constituent_count ?? '—'} / {sector.expected_constituent_count ?? '—'}</em></p><div><b>{money(sector.main_net_inflow)}</b>{sector.change_pct != null && <small>{sector.change_pct > 0 ? '+' : ''}{sector.change_pct.toFixed(2)}%</small>}</div></div>)}</section>
        <section className="discovery-run-note"><span>RUN DISCIPLINE</span><dl><div><dt>预算上限</dt><dd>¥{report.budget.toLocaleString()}</dd></div><div><dt>深度预测耗时</dt><dd>{(report.duration_ms / 1000).toFixed(1)}s</dd></div><div><dt>候选基准</dt><dd>同股买入持有</dd></div><div><dt>批次编号</dt><dd>#{run?.id ?? '—'}</dd></div></dl><p>排名用于研究优先级，不构成交易建议。概率会在未来到期后持续以真实结果校准。</p></section>
        {report.warnings.length > 0 && <section className="discovery-warnings"><span>DATA NOTES</span>{report.warnings.slice(0, 5).map(item => <p key={item}>{item}</p>)}</section>}
      </aside>
    </div>
  </section>;
}
