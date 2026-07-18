import { useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import {
  BrokerResearchAnalysis,
  BrokerResearchCandidate,
  BrokerResearchReport,
  BrokerResearchReportView,
  BrokerResearchSyncResult
} from './financialTypes';

export function ResearchReportAnalysisPanel({
  instrumentId,
  financialReportId
}: {
  instrumentId: number;
  financialReportId?: number;
}) {
  const [reports, setReports] = useState<BrokerResearchReport[]>([]);
  const [detail, setDetail] = useState<BrokerResearchReportView>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [syncResult, setSyncResult] = useState<BrokerResearchSyncResult>();
  const [syncing, setSyncing] = useState(false);
  const [syncError, setSyncError] = useState('');
  const [importingId, setImportingId] = useState('');
  const [file, setFile] = useState<File>();
  const [title, setTitle] = useState('');
  const [institution, setInstitution] = useState('');
  const [analyst, setAnalyst] = useState('');
  const [publishedDate, setPublishedDate] = useState('');
  const [rating, setRating] = useState('');
  const [targetPrice, setTargetPrice] = useState('');
  const sequence = useRef(0);

  useEffect(() => {
    const current = ++sequence.current;
    setSyncResult(undefined);
    void initialize(current);
  }, [instrumentId, financialReportId]);

  async function initialize(current = ++sequence.current) {
    setBusy(true);
    setSyncing(true);
    setError('');
    setSyncError('');
    let synced: BrokerResearchSyncResult | undefined;
    try {
      const linkQuery = financialReportId ? `?financialReportId=${financialReportId}` : '';
      synced = await api<BrokerResearchSyncResult>(
        `/api/financials/instruments/${instrumentId}/research-reports/sync${linkQuery}`,
        { method: 'POST' }
      );
      if (current === sequence.current) setSyncResult(synced);
    } catch (reason) {
      if (current === sequence.current) {
        setSyncError(messageOf(reason, '公开研报自动同步失败，可继续使用已有研报或上传 PDF'));
      }
    } finally {
      if (current === sequence.current) setSyncing(false);
    }

    try {
      const items = await api<BrokerResearchReport[]>(
        `/api/financials/instruments/${instrumentId}/research-reports`
      );
      if (current !== sequence.current) return;
      setReports(items);
      if (!items.length) {
        setDetail(undefined);
        return;
      }
      const imported = synced?.importedReports?.[0]?.id;
      const preferred = items.find((item) => item.id === imported)?.id ?? items[0].id;
      await loadDetail(preferred, current);
    } catch (reason) {
      if (current === sequence.current) setError(messageOf(reason, '研报列表加载失败'));
    } finally {
      if (current === sequence.current) setBusy(false);
    }
  }

  async function importCandidate(candidate: BrokerResearchCandidate) {
    const current = ++sequence.current;
    setImportingId(candidate.externalId);
    setError('');
    try {
      const result = await api<BrokerResearchReportView>(
        `/api/financials/instruments/${instrumentId}/research-reports/import`,
        {
          method: 'POST',
          body: JSON.stringify({
            sourceCode: candidate.sourceCode,
            externalId: candidate.externalId,
            financialReportId
          })
        }
      );
      if (current !== sequence.current) return;
      setDetail(result);
      setReports((items) => [result.report, ...items.filter((item) => item.id !== result.report.id)]);
      setSyncResult((previous) => previous ? {
        ...previous,
        importedCount: previous.importedCount + 1,
        candidates: previous.candidates.map((item) => item.externalId === candidate.externalId
          ? { ...item, availability: 'IMPORTED', importedReportId: result.report.id }
          : item)
      } : previous);
    } catch (reason) {
      if (current === sequence.current) {
        setError(messageOf(reason, '公开研报导入或详细解读失败'));
      }
    } finally {
      if (current === sequence.current) setImportingId('');
    }
  }

  async function loadDetail(id: number, current = ++sequence.current) {
    setBusy(true);
    setError('');
    try {
      const result = await api<BrokerResearchReportView>(detailPath(id, financialReportId));
      if (current === sequence.current) setDetail(result);
    } catch (reason) {
      if (current === sequence.current) setError(messageOf(reason, '研报详细解读加载失败'));
    } finally {
      if (current === sequence.current) setBusy(false);
    }
  }

  async function upload() {
    if (!file) return;
    const current = ++sequence.current;
    setBusy(true);
    setError('');
    try {
      const form = new FormData();
      form.append('instrumentId', String(instrumentId));
      if (financialReportId) form.append('financialReportId', String(financialReportId));
      append(form, 'title', title);
      append(form, 'institution', institution);
      append(form, 'analyst', analyst);
      append(form, 'publishedDate', publishedDate);
      append(form, 'rating', rating);
      append(form, 'targetPrice', targetPrice);
      form.append('reportType', 'DEEP_DIVE');
      form.append('file', file);
      const result = await api<BrokerResearchReportView>('/api/financials/research-reports/upload', {
        method: 'POST',
        body: form
      });
      if (current !== sequence.current) return;
      setDetail(result);
      setReports((items) => [result.report, ...items.filter((item) => item.id !== result.report.id)]);
      setFile(undefined);
    } catch (reason) {
      if (current === sequence.current) setError(messageOf(reason, '研报上传或详细解读失败'));
    } finally {
      if (current === sequence.current) setBusy(false);
    }
  }

  async function reanalyze() {
    if (!detail) return;
    const current = ++sequence.current;
    setBusy(true);
    setError('');
    try {
      const query = financialReportId ? `?financialReportId=${financialReportId}` : '';
      const result = await api<BrokerResearchReportView>(
        `/api/financials/research-reports/${detail.report.id}/reanalyze${query}`,
        { method: 'POST' }
      );
      if (current === sequence.current) setDetail(result);
    } catch (reason) {
      if (current === sequence.current) setError(messageOf(reason, '研报重新解析失败'));
    } finally {
      if (current === sequence.current) setBusy(false);
    }
  }

  return (
    <section className="broker-research-panel">
      <header className="broker-research-header">
        <div>
          <p className="financials-section-kicker">Research report learning desk</p>
          <h4>研报观点—财报事实验证台</h4>
          <p>详细阅读分析师的论证、预测与假设，并用当前三张表事实逐项核对。</p>
        </div>
        {reports.length > 0 && (
          <div className="broker-research-actions">
            <label>
              <span>当前研报</span>
              <select
                aria-label="当前研报"
                value={detail?.report.id ?? reports[0]?.id ?? ''}
                onChange={(event) => loadDetail(Number(event.target.value))}
              >
                {reports.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.institution ? `${item.institution} · ` : ''}{item.title}
                  </option>
                ))}
              </select>
            </label>
            <button
              className={`broker-research-button broker-research-button--reanalyze${busy ? ' is-loading' : ''}`}
              type="button"
              onClick={reanalyze}
              disabled={busy || !detail}
            >
              <span className="broker-research-button-icon" aria-hidden="true">↻</span>
              <span>{busy ? '解析中…' : '重新详细解析'}</span>
            </button>
          </div>
        )}
      </header>

      <ResearchSync
        result={syncResult}
        syncing={syncing}
        error={syncError}
        importingId={importingId}
        onRefresh={() => initialize()}
        onImport={importCandidate}
        onOpen={(id) => loadDetail(id)}
      />

      {error && <div className="broker-research-error" role="alert">{error}</div>}

      <ResearchUpload
        file={file}
        title={title}
        institution={institution}
        analyst={analyst}
        publishedDate={publishedDate}
        rating={rating}
        targetPrice={targetPrice}
        busy={busy}
        compact={reports.length > 0 || Boolean(syncResult?.candidates.length)}
        onFile={setFile}
        onTitle={setTitle}
        onInstitution={setInstitution}
        onAnalyst={setAnalyst}
        onPublishedDate={setPublishedDate}
        onRating={setRating}
        onTargetPrice={setTargetPrice}
        onUpload={upload}
      />

      {!busy && !reports.length && !detail && !syncResult?.candidates.length && (
        <div className="broker-research-empty">
          <strong>暂未自动获取到公开研报</strong>
          <span>可以稍后重新同步，或上传合法取得的 PDF 开始详细学习。</span>
        </div>
      )}

      {detail && <ResearchDetail detail={detail} />}
    </section>
  );
}

function ResearchSync(props: {
  result?: BrokerResearchSyncResult;
  syncing: boolean;
  error: string;
  importingId: string;
  onRefresh: () => void;
  onImport: (candidate: BrokerResearchCandidate) => void;
  onOpen: (id: number) => void;
}) {
  const candidates = props.result?.candidates ?? [];
  return (
    <section className="broker-research-sync">
      <header>
        <div>
          <p className="financials-section-kicker">Public research auto sync</p>
          <h4>自动获取公开研报</h4>
          <p>
            {props.syncing
              ? '正在同步公开目录，并详细解读最新一篇…'
              : props.result
                ? `已自动导入 ${props.result.importedCount} 篇 · 发现 ${candidates.length} 篇公开研报`
                : '进入页面后自动同步；手动上传仅作为补充。'}
          </p>
        </div>
        <button
          className={`broker-research-button broker-research-button--sync${props.syncing ? ' is-loading' : ''}`}
          type="button"
          onClick={props.onRefresh}
          disabled={props.syncing || Boolean(props.importingId)}
        >
          <span className="broker-research-button-icon" aria-hidden="true">↻</span>
          <span>{props.syncing ? '同步中…' : '同步最新研报'}</span>
        </button>
      </header>

      {props.error && <div className="broker-research-error" role="alert">{props.error}</div>}
      {props.result?.errors?.length ? (
        <div className="broker-research-sync-warnings">
          {props.result.errors.map((item) => <span key={item}>{item}</span>)}
        </div>
      ) : null}

      {candidates.length > 0 && (
        <div className="broker-research-candidates">
          {candidates.map((candidate) => (
            <article key={candidate.sourceCode + '-' + candidate.externalId}>
              <div>
                <span>{candidate.institution || sourceLabel(candidate.sourceCode)}</span>
                <h5>{candidate.title}</h5>
                <p>{[candidate.institution, candidate.analyst, candidate.publishedDate]
                  .filter(Boolean).join(' · ')}</p>
                <small>
                  {[candidate.rating, candidate.pageCount ? candidate.pageCount + ' 页' : undefined]
                    .filter(Boolean).join(' · ')}
                </small>
              </div>
              {candidate.importedReportId ? (
                <button
                  className="broker-research-button broker-research-button--read"
                  type="button"
                  onClick={() => props.onOpen(candidate.importedReportId!)}
                >
                  <span>阅读详细解读</span>
                  <span className="broker-research-button-icon" aria-hidden="true">→</span>
                </button>
              ) : (
                <button
                  className={`broker-research-button broker-research-button--import${props.importingId === candidate.externalId ? ' is-loading' : ''}`}
                  type="button"
                  disabled={props.importingId === candidate.externalId
                    || candidate.availability === 'UNAVAILABLE'}
                  onClick={() => props.onImport(candidate)}
                >
                  <span className="broker-research-button-icon" aria-hidden="true">↓</span>
                  <span>{props.importingId === candidate.externalId
                    ? '正在导入解读…'
                    : candidate.availability === 'FAILED' ? '重试导入' : '导入并详细解读'}</span>
                </button>
              )}
            </article>
          ))}
        </div>
      )}

      {props.result?.completedAt && (
        <footer>
          来源：{sourceLabel(props.result.sourceCode)} · 最近同步 {formatDateTime(props.result.completedAt)}
          {props.result.failedCount > 0 ? ` · ${props.result.failedCount} 篇失败` : ''}
        </footer>
      )}
    </section>
  );
}

function ResearchUpload(props: {
  file?: File;
  title: string;
  institution: string;
  analyst: string;
  publishedDate: string;
  rating: string;
  targetPrice: string;
  busy: boolean;
  compact: boolean;
  onFile: (file?: File) => void;
  onTitle: (value: string) => void;
  onInstitution: (value: string) => void;
  onAnalyst: (value: string) => void;
  onPublishedDate: (value: string) => void;
  onRating: (value: string) => void;
  onTargetPrice: (value: string) => void;
  onUpload: () => void;
}) {
  return (
    <details className="broker-research-upload" open={!props.compact}>
      <summary>{props.compact ? '补充一篇研报' : '上传研报 PDF'}</summary>
      <div className="broker-research-upload-grid">
        <label><span>研报标题</span><input aria-label="研报标题" value={props.title} onChange={(e) => props.onTitle(e.target.value)} placeholder="可选，默认使用文件名" /></label>
        <label><span>机构</span><input aria-label="机构" value={props.institution} onChange={(e) => props.onInstitution(e.target.value)} placeholder="券商或研究机构" /></label>
        <label><span>分析师</span><input aria-label="分析师" value={props.analyst} onChange={(e) => props.onAnalyst(e.target.value)} /></label>
        <label><span>发布日期</span><input aria-label="发布日期" type="date" value={props.publishedDate} onChange={(e) => props.onPublishedDate(e.target.value)} /></label>
        <label><span>评级</span><input aria-label="评级" value={props.rating} onChange={(e) => props.onRating(e.target.value)} placeholder="买入 / 增持 / 中性" /></label>
        <label><span>目标价</span><input aria-label="目标价" type="number" value={props.targetPrice} onChange={(e) => props.onTargetPrice(e.target.value)} /></label>
      </div>
      <div className="broker-research-file-row">
        <label className="financials-file-input">
          <span>上传研报 PDF</span>
          <input aria-label="上传研报 PDF" type="file" accept="application/pdf,.pdf" onChange={(event) => props.onFile(event.target.files?.[0])} />
          <strong>{props.file?.name || '选择不超过 30MB 的 PDF'}</strong>
        </label>
        <button className="primary-button" type="button" disabled={!props.file || props.busy} onClick={props.onUpload}>
          {props.busy ? '正在详细解读…' : '上传并详细解读'}
        </button>
      </div>
    </details>
  );
}

function ResearchDetail({ detail }: { detail: BrokerResearchReportView }) {
  const { report, analysis, forecasts, claims } = detail;
  return (
    <div className="broker-research-detail">
      <section className="broker-research-identity">
        <div>
          <span className="broker-research-eyebrow">{report.institution || '来源机构待补充'}</span>
          <h3>{report.title}</h3>
          <p>{[report.analyst, report.publishedDate, report.pageCount ? `${report.pageCount} 页` : undefined].filter(Boolean).join(' · ')}</p>
        </div>
        <div className="broker-research-badges">
          {report.rating && <strong>{report.rating}</strong>}
          {report.targetPrice != null && <span>目标价 {formatValue(report.targetPrice, report.targetPriceCurrency)}</span>}
          <span>{qualityLabel(report.qualityLevel)}</span>
          <span>{report.analysisStatus === 'LLM' ? 'Agent 详细解读' : '规则解析'}</span>
        </div>
      </section>

      <ResearchListSection className="broker-research-core" title="研报核心结论" items={analysis.executiveSummary} evidence={analysis.evidenceSections?.executiveSummary} />

      <div className="broker-research-reading-grid">
        <ResearchListSection title="投资逻辑与论证链" items={analysis.investmentThesis} evidence={analysis.evidenceSections?.investmentThesis} />
        <ResearchListSection title="业务与公司分析" items={analysis.businessAnalysis} evidence={analysis.evidenceSections?.businessAnalysis} />
        <ResearchListSection title="行业与竞争格局" items={analysis.industryAnalysis} evidence={analysis.evidenceSections?.industryAnalysis} />
        <ResearchListSection title="关键假设" items={analysis.keyAssumptions} evidence={analysis.evidenceSections?.keyAssumptions} />
        <ResearchListSection title="潜在催化因素" items={analysis.catalysts} evidence={analysis.evidenceSections?.catalysts} />
        <ResearchListSection className="risk" title="风险与反证" items={analysis.risks} evidence={analysis.evidenceSections?.risks} />
      </div>

      <section className="broker-research-forecast">
        <SectionHeading kicker="Forecast verification" title="盈利预测与实际财报" />
        {forecasts.length ? (
          <div className="broker-research-table-wrap">
            <table>
              <thead><tr><th>指标 / 预测期</th><th>研报预测</th><th>财报实际</th><th>偏差</th><th>验证</th></tr></thead>
              <tbody>
                {forecasts.map((item, index) => (
                  <tr key={item.id ?? `${item.metricCode}-${item.forecastPeriod}-${index}`}>
                    <td><strong>{item.metricLabel}</strong><small>{item.forecastPeriod}</small></td>
                    <td>{formatValue(item.forecastValue, item.unit)}<small>{sourceAt(item.sourceQuote, item.sourcePage)}</small></td>
                    <td>{item.actualValue == null ? '等待对应财报' : formatValue(item.actualValue, item.actualUnit)}<small>{item.actualPeriod}</small></td>
                    <td>{varianceLabel(item.metricCode, item.variancePercent)}</td>
                    <td><Status value={item.verificationStatus} /><small>{item.verificationReason}</small></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : <EmptyLine text="当前文本未提取到可安全结构化的盈利预测数字，请结合原文阅读。" />}
      </section>

      <section className="broker-research-claims">
        <SectionHeading kicker="Claim evidence bridge" title="研报观点 × 财报事实" />
        {claims.length ? claims.map((claim, index) => (
          <article key={claim.id ?? `${claim.title}-${index}`}>
            <div className="broker-research-claim-main">
              <span>{claim.claimType === 'RISK' ? '风险' : claim.claimType === 'FORECAST' ? '预测' : '研报观点'}</span>
              <h5>{claim.title}</h5>
              <p>{claim.detail}</p>
              <blockquote>“{claim.sourceQuote}”{claim.sourcePage ? ` — 第 ${claim.sourcePage} 页` : ''}</blockquote>
            </div>
            <div className="broker-research-evidence-card">
              <Status value={claim.verificationStatus} />
              <strong>{claim.evidenceLabel
                ? `${claim.evidenceLabel} ${claim.evidenceValue ?? '—'}${claim.evidenceUnit ?? ''}`
                : '当前财报尚无直接证据'}</strong>
              <span>{claim.evidencePeriod}</span>
              <p>{claim.verificationReason}</p>
            </div>
          </article>
        )) : <EmptyLine text="尚未抽取到带原文摘录的可验证观点。" />}
      </section>

      <div className="broker-research-learning-grid">
        <ResearchListSection title="怎么学习这篇研报" items={analysis.learningNotes} />
        <section>
          <SectionHeading title="术语卡片" />
          {analysis.glossary.length ? (
            <dl>{analysis.glossary.map((item) => <div key={item.term}><dt>{item.term}</dt><dd>{item.explanation}</dd></div>)}</dl>
          ) : <EmptyLine text="当前研报没有需要单独解释的术语。" />}
        </section>
      </div>

      <details className="broker-research-source">
        <summary>展开完整研报文本与原件</summary>
        <div className="broker-research-source-actions">
          <a href={`/api/financials/research-reports/${report.id}/content`} target="_blank" rel="noreferrer">查看 PDF 原件</a>
          <span>{report.parseStatus === 'PARSED_TRUNCATED'
            ? '文本已提取，超长部分已安全截断'
            : report.parseStatus === 'PARSED' ? '文本已提取' : '扫描件待 OCR'}</span>
        </div>
        <pre>{report.extractedText || '当前 PDF 未提取到可读文本，请查看原件。'}</pre>
      </details>

      {(analysis.limitations.length > 0 || analysis.disclaimer) && (
        <footer className="broker-research-limitations">
          {analysis.limitations.map((item) => <span key={item}>{item}</span>)}
          <strong>{analysis.disclaimer}</strong>
        </footer>
      )}
    </div>
  );
}

function ResearchListSection({ title, items, evidence, className = '' }: {
  title: string;
  items?: string[];
  evidence?: Array<{ text: string; sourceQuote: string; sourcePage?: number }>;
  className?: string;
}) {
  return (
    <section className={`broker-research-list-section ${className}`}>
      <SectionHeading title={title} />
      {items?.length ? <ol>{items.map((item, index) => {
        const source = evidence?.find((point) => point.text === item);
        return (
          <li key={`${index}-${item}`}>
            <span>{item}</span>
            {source && <blockquote>“{source.sourceQuote}”{source.sourcePage ? ` — 第 ${source.sourcePage} 页` : ''}</blockquote>}
          </li>
        );
      })}</ol> : <EmptyLine text="当前研报没有足够证据形成该部分结论。" />}
    </section>
  );
}

function SectionHeading({ title, kicker }: { title: string; kicker?: string }) {
  return <header>{kicker && <p className="financials-section-kicker">{kicker}</p>}<h4>{title}</h4></header>;
}

function Status({ value }: { value?: string }) {
  const labels: Record<string, string> = {
    VERIFIED: '预测基本兑现',
    CONTRADICTED: '偏差较大',
    PENDING: '待后续财报',
    INSUFFICIENT_EVIDENCE: '证据不足',
    EVIDENCE_FOUND: '已关联财报事实'
  };
  return <span className={`broker-research-status ${(value ?? 'PENDING').toLowerCase()}`}>{labels[value ?? 'PENDING'] ?? value}</span>;
}

function EmptyLine({ text }: { text: string }) { return <div className="financials-empty-inline">{text}</div>; }
function detailPath(id: number, financialReportId?: number) {
  return `/api/financials/research-reports/${id}${financialReportId ? `?financialReportId=${financialReportId}` : ''}`;
}
function append(form: FormData, key: string, value: string) { if (value.trim()) form.append(key, value.trim()); }
function sourceAt(quote?: string, page?: number) { return quote ? `“${quote}”${page ? ` · 第 ${page} 页` : ''}` : ''; }
function varianceLabel(metricCode: string, value?: number | string) {
  if (value == null) return '—';
  return metricCode === 'GROSS_MARGIN'
    ? `${Number(value).toFixed(2)} 个百分点`
    : `${Number(value).toFixed(2)}%`;
}
function qualityLabel(value: string) { return value === 'HIGH' ? '高质量文本' : value === 'MEDIUM' ? '可阅读' : '证据有限'; }
function sourceLabel(value?: string) { return value === 'EASTMONEY' ? '东方财富公开研报' : value || '公开来源'; }
function formatDateTime(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN', { hour12: false });
}
function formatValue(value?: number | string, unit?: string) {
  if (value == null || value === '') return '—';
  const number = Number(value);
  const formatted = Number.isFinite(number) ? new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(number) : String(value);
  return unit === 'CNY' ? `¥${formatted}` : unit === 'CNY/SHARE' ? `¥${formatted}/股` : `${formatted}${unit ?? ''}`;
}
function messageOf(reason: unknown, fallback: string) { return reason instanceof Error ? reason.message : fallback; }
