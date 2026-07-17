import { ChangeEvent, useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { FinancialInterpretationPanel } from './FinancialInterpretationPanel';
import { FinancialStatementTable } from './FinancialStatementTable';
import {
  defaultReportPeriod,
  formatMetric,
  qualityLabels,
  reportLabel,
  reportTypeLabels,
  statementLabels
} from './financialPresentation';
import {
  FinancialDocument,
  FinancialInstrument,
  FinancialReport,
  FinancialReportType,
  FinancialReportView,
  FinancialStatementType,
  FinancialUnit
} from './financialTypes';

type Tab = 'OVERVIEW' | FinancialStatementType | 'AGENT' | 'QUALITY' | 'DOCUMENTS';

const tabs: Array<{ id: Tab; label: string }> = [
  { id: 'OVERVIEW', label: '分析总览' },
  { id: 'INCOME', label: '利润表' },
  { id: 'BALANCE_SHEET', label: '资产负债表' },
  { id: 'CASH_FLOW', label: '现金流量表' },
  { id: 'AGENT', label: 'Agent 解读' },
  { id: 'QUALITY', label: '数据质量' },
  { id: 'DOCUMENTS', label: '原文凭证' }
];

export function FinancialsView({
  addToast,
  setMessage
}: {
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage: (message: string) => void;
}) {
  const initialPeriod = useMemo(() => defaultReportPeriod(), []);
  const [instruments, setInstruments] = useState<FinancialInstrument[]>([]);
  const [instrumentId, setInstrumentId] = useState<number>();
  const [reports, setReports] = useState<FinancialReport[]>([]);
  const [view, setView] = useState<FinancialReportView>();
  const [documents, setDocuments] = useState<FinancialDocument[]>([]);
  const [activeTab, setActiveTab] = useState<Tab>('OVERVIEW');
  const [unit, setUnit] = useState<FinancialUnit>('YI');
  const [periodMode, setPeriodMode] = useState<'CUMULATIVE' | 'QUARTER'>('CUMULATIVE');
  const [periodEnd, setPeriodEnd] = useState(initialPeriod.periodEnd);
  const [reportType, setReportType] = useState<FinancialReportType>(initialPeriod.reportType);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [file, setFile] = useState<File>();
  const loadSequence = useRef(0);

  useEffect(() => {
    let active = true;
    api<FinancialInstrument[]>('/api/financials/instruments')
      .then((items) => {
        if (!active) return;
        setInstruments(items);
        setInstrumentId(items[0]?.id);
        if (!items.length) setError('还没有 A 股标的。请先在自选或标的库中添加股票。');
      })
      .catch((reason) => setError(messageOf(reason, '财报标的加载失败')));
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!instrumentId) return;
    const sequence = ++loadSequence.current;
    setBusy(true);
    setError('');
    api<FinancialReport[]>(`/api/financials/instruments/${instrumentId}/reports`)
      .then(async (items) => {
        if (sequence !== loadSequence.current) return;
        setReports(items);
        if (!items.length) {
          setView(undefined);
          setDocuments([]);
          return;
        }
        await loadReport(items[0].id, sequence);
      })
      .catch((reason) => {
        if (sequence === loadSequence.current) setError(messageOf(reason, '本地财报档案加载失败'));
      })
      .finally(() => {
        if (sequence === loadSequence.current) setBusy(false);
      });
  }, [instrumentId]);

  async function loadReport(reportId: number, sequence = ++loadSequence.current) {
    setBusy(true);
    setError('');
    try {
      const [detail, files] = await Promise.all([
        api<FinancialReportView>(`/api/financials/reports/${reportId}`),
        api<FinancialDocument[]>(`/api/financials/reports/${reportId}/documents`)
      ]);
      if (sequence !== loadSequence.current) return;
      setView(detail);
      setDocuments(files);
      setPeriodEnd(detail.report.periodEnd);
      setReportType(detail.report.reportType);
      setActiveTab('OVERVIEW');
      setPeriodMode('CUMULATIVE');
    } catch (reason) {
      if (sequence === loadSequence.current) setError(messageOf(reason, '财报明细加载失败'));
    } finally {
      if (sequence === loadSequence.current) setBusy(false);
    }
  }

  async function refreshReport() {
    if (!instrumentId) return;
    setBusy(true);
    setError('');
    setMessage('正在抓取并解析财报');
    try {
      const detail = await api<FinancialReportView>(
        `/api/financials/instruments/${instrumentId}/refresh`,
        {
          method: 'POST',
          body: JSON.stringify({ periodEnd, reportType })
        }
      );
      setView(detail);
      setReports((current) => [
        detail.report,
        ...current.filter((item) => item.id !== detail.report.id)
      ]);
      setDocuments(await api<FinancialDocument[]>(`/api/financials/reports/${detail.report.id}/documents`));
      setActiveTab('OVERVIEW');
      setPeriodMode('CUMULATIVE');
      const label = reportLabel(detail.report.periodEnd, detail.report.reportType);
      setMessage(`${label}解析完成`);
      addToast(`${label}已抓取并完成分析`, 'success');
    } catch (reason) {
      const message = messageOf(reason, '财报抓取失败');
      setError(message);
      setMessage(message);
      addToast(message, 'error');
    } finally {
      setBusy(false);
    }
  }

  async function uploadDocument() {
    if (!file || !instrumentId || !view?.report.id) return;
    setBusy(true);
    setError('');
    setMessage('正在上传并解析财报 PDF');
    try {
      const form = new FormData();
      form.append('instrumentId', String(instrumentId));
      form.append('reportId', String(view.report.id));
      form.append('file', file);
      const stored = await api<FinancialDocument>('/api/financials/documents/upload', {
        method: 'POST',
        body: form
      });
      setDocuments((current) => [stored, ...current.filter((item) => item.id !== stored.id)]);
      setFile(undefined);
      setMessage('财报 PDF 已归档');
      addToast(stored.parseStatus === 'PARSED' ? 'PDF 已归档并提取文本' : 'PDF 已归档，复杂页面待 OCR', 'success');
    } catch (reason) {
      const message = messageOf(reason, 'PDF 上传失败');
      setError(message);
      addToast(message, 'error');
    } finally {
      setBusy(false);
    }
  }

  const currentInstrument = instruments.find((item) => item.id === instrumentId);
  const currentReport = view?.report;

  return (
    <div className="financials-page">
      <section className="financials-hero">
        <div className="financials-hero-copy">
          <p className="financials-kicker">Financial statement audit trail</p>
          <h3>{currentInstrument ? `${currentInstrument.name}财报底稿` : '公司财报分析'}</h3>
          <p>把利润表、资产负债表和现金流量表放在同一条可核对链路中，先看经营事实，再看质量与风险。</p>
        </div>
        <div className="financials-selector">
          <label>
            <span>分析标的</span>
            <select
              aria-label="分析标的"
              value={instrumentId ?? ''}
              onChange={(event) => setInstrumentId(Number(event.target.value))}
            >
              {instruments.map((item) => (
                <option key={item.id} value={item.id}>{item.code} · {item.name}</option>
              ))}
            </select>
          </label>
          <label>
            <span>本地报告</span>
            <select
              aria-label="本地报告"
              value={currentReport?.id ?? ''}
              disabled={!reports.length}
              onChange={(event) => loadReport(Number(event.target.value))}
            >
              {!reports.length && <option value="">尚未抓取</option>}
              {reports.map((item) => (
                <option key={item.id} value={item.id}>{reportLabel(item.periodEnd, item.reportType)}</option>
              ))}
            </select>
          </label>
        </div>
        <div className="financials-assurance">
          <span className={`financials-assurance-status ${(currentReport?.qualityStatus ?? 'UNVERIFIED').toLowerCase()}`}>
            {qualityLabels[currentReport?.qualityStatus ?? 'UNVERIFIED']}
          </span>
          <dl>
            <div><dt>数据源</dt><dd>{currentReport?.sourceCode || '等待抓取'}</dd></div>
            <div><dt>口径</dt><dd>{currentReport?.scope === 'CONSOLIDATED' ? '合并口径' : currentReport?.scope || '—'}</dd></div>
            <div><dt>审计</dt><dd>{currentReport?.audited ? '已审计' : '未标记'}</dd></div>
          </dl>
        </div>
      </section>

      <section className="financials-fetch-strip" aria-label="财报抓取">
        <div>
          <strong>{reports.length ? '补充或重抓报告期' : '建立第一份财报底稿'}</strong>
          <span>当前一期支持 A 股非金融企业，数据抓取后保存在本地。</span>
        </div>
        <label>
          <span>报告期末</span>
          <input
            aria-label="报告期末"
            type="date"
            value={periodEnd}
            onChange={(event) => setPeriodEnd(event.target.value)}
          />
        </label>
        <label>
          <span>报告类型</span>
          <select
            aria-label="报告类型"
            value={reportType}
            onChange={(event) => setReportType(event.target.value as FinancialReportType)}
          >
            {Object.entries(reportTypeLabels).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
        <button className="primary-button" type="button" disabled={busy || !instrumentId || !periodEnd} onClick={refreshReport}>
          {busy ? '处理中…' : '抓取并解析财报'}
        </button>
      </section>

      {error && <div className="financials-alert" role="alert">{error}</div>}

      {view ? (
        <>
          <section className="financials-report-bar">
            <div>
              <p>{view.instrument.code} · {view.instrument.market}</p>
              <strong>{reportLabel(view.report.periodEnd, view.report.reportType)}</strong>
              {view.report.warningMessage && <span>{view.report.warningMessage}</span>}
            </div>
            <div className="financials-view-controls">
              <div className="financials-period-switch" aria-label="报表期间口径">
                <button
                  type="button"
                  className={periodMode === 'CUMULATIVE' ? 'active' : ''}
                  onClick={() => setPeriodMode('CUMULATIVE')}
                >
                  累计
                </button>
                <button
                  type="button"
                  className={periodMode === 'QUARTER' ? 'active' : ''}
                  onClick={() => setPeriodMode('QUARTER')}
                >
                  单季度
                </button>
              </div>
              <div className="financials-unit-switch" aria-label="金额单位">
                {(['YUAN', 'WAN', 'YI'] as FinancialUnit[]).map((item) => (
                  <button
                    type="button"
                    key={item}
                    className={unit === item ? 'active' : ''}
                    onClick={() => setUnit(item)}
                  >
                    {item === 'YUAN' ? '元' : item === 'WAN' ? '万元' : '亿元'}
                  </button>
                ))}
              </div>
            </div>
          </section>

          <div className="financials-tabs" role="tablist" aria-label="财报分析视图">
            {tabs.map((tab) => (
              <button
                role="tab"
                aria-selected={activeTab === tab.id}
                type="button"
                key={tab.id}
                className={activeTab === tab.id ? 'active' : ''}
                onClick={() => setActiveTab(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {activeTab === 'OVERVIEW' && (
            <Overview reportView={view} unit={unit} />
          )}
          {activeTab === 'INCOME' && (
            <FinancialStatementTable title={statementLabels.INCOME} items={view.statements.INCOME ?? []} unit={unit} periodMode={periodMode} />
          )}
          {activeTab === 'BALANCE_SHEET' && (
            <FinancialStatementTable title={statementLabels.BALANCE_SHEET} items={view.statements.BALANCE_SHEET ?? []} unit={unit} />
          )}
          {activeTab === 'CASH_FLOW' && (
            <FinancialStatementTable title={statementLabels.CASH_FLOW} items={view.statements.CASH_FLOW ?? []} unit={unit} periodMode={periodMode} />
          )}
          {activeTab === 'AGENT' && <FinancialInterpretationPanel reportId={view.report.id} />}
          {activeTab === 'QUALITY' && <QualityPanel reportView={view} />}
          {activeTab === 'DOCUMENTS' && (
            <DocumentsPanel
              documents={documents}
              file={file}
              busy={busy}
              onFileChange={(event) => setFile(event.target.files?.[0])}
              onUpload={uploadDocument}
            />
          )}
        </>
      ) : (
        <section className="financials-first-run">
          <span aria-hidden="true">三表</span>
          <div>
            <h3>选择报告期，建立第一份可复算底稿</h3>
            <p>系统会抓取结构化利润表、资产负债表和现金流量表，并计算现金含量、利润率、负债水平与异常增长关系。</p>
          </div>
        </section>
      )}
    </div>
  );
}

function Overview({ reportView, unit }: { reportView: FinancialReportView; unit: FinancialUnit }) {
  return (
    <div className="financials-overview">
      <section className="financials-bridge" aria-label="三表勾稽轨道">
        {(['INCOME', 'BALANCE_SHEET', 'CASH_FLOW'] as FinancialStatementType[]).map((type, index) => (
          <div key={type}>
            <span>{index + 1}</span>
            <strong>{statementLabels[type]}</strong>
            <small>{(reportView.statements[type] ?? []).length} 个科目</small>
          </div>
        ))}
      </section>

      <section className="financials-metric-panel">
        <header>
          <div>
            <p className="financials-section-kicker">Reproducible metrics</p>
            <h4>经营质量指标</h4>
          </div>
          <span>均由当前底稿复算</span>
        </header>
        <dl className="financials-metrics">
          {reportView.metrics.length ? reportView.metrics.map((metric) => (
            <div key={metric.metricCode}>
              <dt>{metric.label}</dt>
              <dd>{formatMetric(metric.value, metric.unit)}</dd>
              <small>{metric.qualityStatus === 'FRESH' ? '输入完整' : '输入不完整'}</small>
            </div>
          )) : <div className="financials-empty-inline">当前报告尚不足以计算核心指标。</div>}
        </dl>
      </section>

      <section className="financials-key-statements">
        {(['INCOME', 'BALANCE_SHEET', 'CASH_FLOW'] as FinancialStatementType[]).map((type) => (
          <FinancialStatementTable
            key={type}
            title={statementLabels[type]}
            items={reportView.statements[type] ?? []}
            unit={unit}
            compact
          />
        ))}
      </section>

      <section className="financials-findings">
        <header>
          <div>
            <p className="financials-section-kicker">Rule-based review</p>
            <h4>需要继续核对</h4>
          </div>
          <span>{reportView.findings.length} 条规则发现</span>
        </header>
        <div className="financials-finding-list">
          {reportView.findings.length ? reportView.findings.map((finding) => (
            <article key={finding.ruleCode} data-severity={finding.severity.toLowerCase()}>
              <span>{finding.severity === 'HIGH' ? '高关注' : '需关注'}</span>
              <div>
                <h5>{finding.title}</h5>
                <p>{finding.explanation}</p>
                {finding.limitations && <small>{finding.limitations}</small>}
              </div>
            </article>
          )) : <div className="financials-empty-inline">当前规则未发现显著异常关系。</div>}
        </div>
        {reportView.dataGaps.length > 0 && (
          <div className="financials-gap-list">
            <strong>数据缺口</strong>
            {reportView.dataGaps.map((gap) => <span key={gap}>{gap}</span>)}
          </div>
        )}
      </section>
    </div>
  );
}

function QualityPanel({ reportView }: { reportView: FinancialReportView }) {
  const counts = Object.values(reportView.statements).flat().reduce<Record<string, number>>((result, item) => {
    result[item.valueOrigin] = (result[item.valueOrigin] ?? 0) + 1;
    return result;
  }, {});
  return (
    <section className="financials-quality-panel">
      <header>
        <p className="financials-section-kicker">Data lineage</p>
        <h4>数据来源与派生口径</h4>
      </header>
      <div className="financials-quality-grid">
        <div><strong>{counts.REPORTED ?? 0}</strong><span>原始披露科目</span></div>
        <div><strong>{counts.DERIVED ?? 0}</strong><span>单季派生科目</span></div>
        <div><strong>{counts.CALCULATED ?? 0}</strong><span>计算科目</span></div>
        <div><strong>{reportView.dataGaps.length}</strong><span>待补数据缺口</span></div>
      </div>
      <div className="financials-method-note">
        <strong>口径说明</strong>
        <p>累计利润表和现金流量表会在可获得前序累计值时派生单季值；所有派生值保留来源标记。规则发现只描述数据关系，不构成买卖建议。</p>
      </div>
    </section>
  );
}

function DocumentsPanel({
  documents,
  file,
  busy,
  onFileChange,
  onUpload
}: {
  documents: FinancialDocument[];
  file?: File;
  busy: boolean;
  onFileChange: (event: ChangeEvent<HTMLInputElement>) => void;
  onUpload: () => void;
}) {
  return (
    <section className="financials-documents">
      <div className="financials-upload">
        <div>
          <p className="financials-section-kicker">Source evidence</p>
          <h4>原始财报 PDF</h4>
          <p>上传的原文会与当前报告绑定。文本型 PDF 可直接提取；复杂扫描件会保留并标记为待 OCR。</p>
        </div>
        <label className="financials-file-input">
          <span>上传财报 PDF</span>
          <input aria-label="上传财报 PDF" type="file" accept="application/pdf,.pdf" onChange={onFileChange} />
          <strong>{file?.name || '选择不超过 30MB 的 PDF'}</strong>
        </label>
        <button className="primary-button" type="button" disabled={!file || busy} onClick={onUpload}>
          {busy ? '解析中…' : '上传并解析 PDF'}
        </button>
      </div>
      <div className="financials-document-list">
        {documents.length ? documents.map((document) => (
          <article key={document.id}>
            <div>
              <strong>{document.originalFileName}</strong>
              <span>{document.pageCount ? `${document.pageCount} 页` : '页数待识别'} · {document.parseStatus === 'PARSED' ? '文本已提取' : '待 OCR'}</span>
            </div>
            <a href={`/api/financials/documents/${document.id}/content`} target="_blank" rel="noreferrer">查看原文</a>
          </article>
        )) : <div className="financials-empty-inline">当前报告还没有绑定原始 PDF。</div>}
      </div>
    </section>
  );
}

function messageOf(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason.message : fallback;
}
