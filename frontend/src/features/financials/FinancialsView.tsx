import { ChangeEvent, useEffect, useMemo, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { FinancialInterpretationPanel } from './FinancialInterpretationPanel';
import { FinancialStatementTable } from './FinancialStatementTable';
import { GlobalCompanySearch } from './GlobalCompanySearch';
import { ResearchReportAnalysisPanel } from './ResearchReportAnalysisPanel';
import { ValuationPanel } from './ValuationPanel';
import {
  defaultReportPeriod,
  formatMetric,
  qualityLabels,
  reportLabel,
  reportPeriodEnd,
  reportTypeLabels,
  statementLabels
} from './financialPresentation';
import {
  FinancialDocument,
  CompanySearchResult,
  FinancialInstrument,
  FinancialReport,
  FinancialReportType,
  FinancialReportView,
  FinancialStatementType,
  FinancialUnit
} from './financialTypes';

type Tab = 'OVERVIEW' | 'VALUATION' | FinancialStatementType | 'AGENT' | 'RESEARCH' | 'QUALITY' | 'DOCUMENTS';

const tabs: Array<{ id: Tab; label: string }> = [
  { id: 'OVERVIEW', label: '分析总览' },
  { id: 'VALUATION', label: '估值' },
  { id: 'INCOME', label: '利润表' },
  { id: 'BALANCE_SHEET', label: '资产负债表' },
  { id: 'CASH_FLOW', label: '现金流量表' },
  { id: 'AGENT', label: 'Agent 解读' },
  { id: 'RESEARCH', label: '研报分析' },
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
  const [archiveReports, setArchiveReports] = useState<Record<number, FinancialReport[]>>({});
  const [archiveLoading, setArchiveLoading] = useState(true);
  const [view, setView] = useState<FinancialReportView>();
  const [documents, setDocuments] = useState<FinancialDocument[]>([]);
  const [externalCompany, setExternalCompany] = useState<CompanySearchResult>();
  const [activeTab, setActiveTab] = useState<Tab>('OVERVIEW');
  const [unit, setUnit] = useState<FinancialUnit>('YI');
  const [periodMode, setPeriodMode] = useState<'CUMULATIVE' | 'QUARTER'>('CUMULATIVE');
  const [reportYear, setReportYear] = useState(initialPeriod.periodEnd.slice(0, 4));
  const [reportType, setReportType] = useState<FinancialReportType>(initialPeriod.reportType);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [file, setFile] = useState<File>();
  const loadSequence = useRef(0);
  const currentYear = new Date().getFullYear();
  const reportYearNumber = Number(reportYear);
  const reportYearValid = /^\d{4}$/.test(reportYear)
    && reportYearNumber >= 1990
    && reportYearNumber <= currentYear;
  const periodEnd = reportYearValid ? reportPeriodEnd(reportYear, reportType) : '';

  useEffect(() => {
    let active = true;
    api<FinancialInstrument[]>('/api/financials/instruments')
      .then(async (items) => {
        if (!active) return;
        setInstruments((current) => [
          ...current,
          ...items.filter((item) => !current.some((existing) => existing.id === item.id))
        ]);
        const entries = await Promise.all(items.map(async (item) => {
          try {
            const itemReports = await api<FinancialReport[]>(`/api/financials/instruments/${item.id}/reports`);
            return { instrumentId: item.id, reports: itemReports, failed: false };
          } catch {
            return { instrumentId: item.id, reports: [] as FinancialReport[], failed: true };
          }
        }));
        if (!active) return;
        setArchiveReports((current) => {
          const next = { ...current };
          entries.forEach((entry) => {
            if (!entry.failed) next[entry.instrumentId] = mergeReports(entry.reports, current[entry.instrumentId] ?? []);
          });
          return next;
        });
        if (entries.some((entry) => entry.failed)) setError('部分公司的财报档案加载失败，已展示其余可用报告。');
      })
      .catch((reason) => setError(messageOf(reason, '财报档案加载失败')))
      .finally(() => { if (active) setArchiveLoading(false); });
    return () => { active = false; };
  }, []);

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
      setReportYear(detail.report.periodEnd.slice(0, 4));
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
    if (!instrumentId || !periodEnd) return;
    const sequence = ++loadSequence.current;
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
      setArchiveReports((current) => ({
        ...current,
        [instrumentId]: mergeReports(current[instrumentId] ?? [], [detail.report])
      }));
      if (sequence !== loadSequence.current) return;
      setView(detail);
      setReports((current) => [
        detail.report,
        ...current.filter((item) => item.id !== detail.report.id)
      ]);
      setDocuments(await api<FinancialDocument[]>(`/api/financials/reports/${detail.report.id}/documents`));
      if (sequence !== loadSequence.current) return;
      setActiveTab('OVERVIEW');
      setPeriodMode('CUMULATIVE');
      const label = reportLabel(detail.report.periodEnd, detail.report.reportType);
      setMessage(`${label}解析完成`);
      addToast(`${label}已抓取并完成分析`, 'success');
    } catch (reason) {
      if (sequence !== loadSequence.current) return;
      const message = messageOf(reason, '财报抓取失败');
      setError(message);
      setMessage(message);
      addToast(message, 'error');
    } finally {
      if (sequence === loadSequence.current) setBusy(false);
    }
  }

  async function refreshGlobalReport() {
    if (!externalCompany || !periodEnd || !supportsGlobalFetch(externalCompany.providerCode)) return;
    const security = externalCompany.securities[0];
    if (!security) return;
    const sequence = ++loadSequence.current;
    const sourceName = externalCompany.providerCode === 'SEC_EDGAR' ? 'SEC' : 'DART';
    setBusy(true);
    setError('');
    setMessage(`正在从 ${sourceName} 抓取 ${externalCompany.displayName} 财报`);
    try {
      const detail = await api<FinancialReportView>('/api/financials/global/refresh', {
        method: 'POST',
        body: JSON.stringify({
          providerCode: externalCompany.providerCode,
          providerCompanyId: externalCompany.providerCompanyId,
          displayName: externalCompany.displayName,
          symbol: security.symbol,
          exchange: security.exchange,
          periodEnd,
          reportType
        })
      });
      setInstruments((current) => [
        detail.instrument,
        ...current.filter((item) => item.id !== detail.instrument.id)
      ]);
      setArchiveReports((current) => ({
        ...current,
        [detail.instrument.id]: mergeReports(current[detail.instrument.id] ?? [], [detail.report])
      }));
      if (sequence !== loadSequence.current) return;
      setReports([detail.report]);
      setView(detail);
      setDocuments([]);
      setExternalCompany(undefined);
      setInstrumentId(detail.instrument.id);
      setReportYear(detail.report.periodEnd.slice(0, 4));
      setReportType(detail.report.reportType);
      setActiveTab('OVERVIEW');
      setPeriodMode('CUMULATIVE');
      const label = reportLabel(detail.report.periodEnd, detail.report.reportType);
      setMessage(`${externalCompany.displayName} ${label}解析完成`);
      addToast(`${externalCompany.displayName} ${label}已抓取并完成分析`, 'success');
    } catch (reason) {
      if (sequence !== loadSequence.current) return;
      const message = messageOf(reason, `${sourceName} 财报抓取失败`);
      setError(message);
      setMessage(message);
      addToast(message, 'error');
    } finally {
      if (sequence === loadSequence.current) setBusy(false);
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
  const capturedReportCount = Object.values(archiveReports).reduce((count, items) => count + items.length, 0);

  async function openReport(instrument: FinancialInstrument, reportToOpen: FinancialReport) {
    const sequence = ++loadSequence.current;
    setExternalCompany(undefined);
    setInstrumentId(instrument.id);
    setReports(archiveReports[instrument.id] ?? []);
    await loadReport(reportToOpen.id, sequence);
  }

  function returnToArchive() {
    ++loadSequence.current;
    setBusy(false);
    setView(undefined);
    setDocuments([]);
    setReports([]);
    setInstrumentId(undefined);
    setExternalCompany(undefined);
    setError('');
  }

  function chooseCaptureTarget(instrument: FinancialInstrument) {
    ++loadSequence.current;
    setBusy(false);
    setExternalCompany(undefined);
    setView(undefined);
    setDocuments([]);
    setInstrumentId(instrument.id);
    setReports(archiveReports[instrument.id] ?? []);
    setError('');
  }

  function selectCompany(company: CompanySearchResult) {
    setError('');
    setActiveTab('OVERVIEW');
    if (company.localInstrumentId) {
      const security = company.securities[0];
      const selectedInstrument: FinancialInstrument = {
        id: company.localInstrumentId,
        code: security?.symbol ?? company.providerCompanyId,
        name: company.displayName,
        type: 'STOCK',
        market: security?.exchange ?? security?.market ?? company.countryCode
      };
      ++loadSequence.current;
      setBusy(false);
      setInstruments((current) => [
        selectedInstrument,
        ...current.filter((item) => item.id !== selectedInstrument.id)
      ]);
      setExternalCompany(undefined);
      setView(undefined);
      setDocuments([]);
      setInstrumentId(selectedInstrument.id);
      setReports(archiveReports[selectedInstrument.id] ?? []);
      return;
    }
    ++loadSequence.current;
    setBusy(false);
    setExternalCompany(company);
    setInstrumentId(undefined);
    setReports([]);
    setView(undefined);
    setDocuments([]);
  }

  return (
    <div className="financials-page">
      {!view && <section className="global-company-command" aria-label="全球公司入口">
        <div>
          <p className="financials-kicker">Global issuer directory</p>
          <h3>查找任何上市公司</h3>
          <p>按公司名称或股票代码搜索，抓取后的报告会自动归入下方财报档案。</p>
        </div>
        <GlobalCompanySearch onSelect={selectCompany} />
      </section>}

      {!view && (externalCompany || currentInstrument) && <>
        {externalCompany && <GlobalCompanyIdentity company={externalCompany} />}
        <section className="financials-fetch-strip" aria-label="财报抓取">
        <div>
          <strong>{externalCompany
            ? externalCompany.providerCode === 'SEC_EDGAR' ? '从 SEC 建立财报底稿'
              : externalCompany.providerCode === 'KRX_KIND' ? '从 DART 建立财报底稿' : '该市场等待结构化财报接入'
            : reports.length ? '补充或重抓报告期' : '建立第一份财报底稿'}</strong>
          <span>{externalCompany
            ? supportsGlobalFetch(externalCompany.providerCode)
              ? externalCompany.providerCode === 'SEC_EDGAR'
                ? '按财年匹配 10-K / 10-Q，实际期末日以公司披露为准，抓取后保存在本地。'
                : '通过 DART 官方披露检索定位 XBRL 报告，解析 K-IFRS 连接三表并保存在本地。'
              : '当前已完成公司识别，但尚未接入该市场的结构化三张表。'
            : '当前支持 A 股非金融企业，数据抓取后保存在本地。'}</span>
        </div>
        <label>
          <span className="financials-field-heading">
            报告年度
            <small>{periodEnd ? `将按 ${periodEnd} 查询` : `请输入 1990–${currentYear} 年`}</small>
          </span>
          <input
            aria-label="报告年度"
            type="number"
            min="1990"
            max={currentYear}
            step="1"
            inputMode="numeric"
            value={reportYear}
            onChange={(event) => setReportYear(event.target.value)}
          />
        </label>
        <label>
          <span className="financials-field-heading">报告类型</span>
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
        <button
          className="primary-button"
          type="button"
          disabled={busy || !periodEnd || (externalCompany
            ? !supportsGlobalFetch(externalCompany.providerCode)
            : !instrumentId)}
          onClick={externalCompany ? refreshGlobalReport : refreshReport}
        >
          {busy ? '处理中…' : externalCompany?.providerCode === 'SEC_EDGAR'
            ? '抓取并解析 SEC 财报'
            : externalCompany?.providerCode === 'KRX_KIND' ? '抓取并解析 DART 财报'
            : externalCompany ? '等待数据源接入' : '抓取并解析财报'}
        </button>
        </section>
      </>}

      {error && <div className="financials-alert" role="alert">{error}</div>}

      {externalCompany ? <GlobalCompanyWorkspace company={externalCompany} /> : view ? (
        <>
          <button className="financials-detail-back" type="button" onClick={returnToArchive}>
            <span aria-hidden="true">←</span> 返回财报列表
          </button>
          <section className="financials-report-bar">
            <div>
              <p>{view.instrument.code} · {view.instrument.market} · {view.report.sourceCode}</p>
              <h2>{view.instrument.name} · {reportLabel(view.report.periodEnd, view.report.reportType)}</h2>
              {view.report.warningMessage && <span>{view.report.warningMessage}</span>}
            </div>
            <div className="financials-report-assurance">
              <span className={`financials-assurance-status ${view.report.qualityStatus.toLowerCase()}`}>
                {qualityLabels[view.report.qualityStatus]}
              </span>
              <small>{view.report.scope === 'CONSOLIDATED' ? '合并口径' : view.report.scope} · {view.report.audited ? '已审计' : '未标记审计'}</small>
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
          {activeTab === 'VALUATION' && <ValuationPanel instrumentId={view.instrument.id} />}
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
          {activeTab === 'RESEARCH' && (
            <ResearchReportAnalysisPanel instrumentId={view.instrument.id} financialReportId={view.report.id} />
          )}
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
      ) : <FinancialReportArchive
        instruments={instruments}
        reportsByInstrument={archiveReports}
        loading={archiveLoading}
        reportCount={capturedReportCount}
        captureInstrumentId={instrumentId}
        onOpenReport={openReport}
        onCapture={chooseCaptureTarget}
      />}
    </div>
  );
}

function FinancialReportArchive({
  instruments,
  reportsByInstrument,
  loading,
  reportCount,
  captureInstrumentId,
  onOpenReport,
  onCapture
}: {
  instruments: FinancialInstrument[];
  reportsByInstrument: Record<number, FinancialReport[]>;
  loading: boolean;
  reportCount: number;
  captureInstrumentId?: number;
  onOpenReport: (instrument: FinancialInstrument, report: FinancialReport) => void;
  onCapture: (instrument: FinancialInstrument) => void;
}) {
  const archivedInstruments = instruments.filter((instrument) => (reportsByInstrument[instrument.id] ?? []).length > 0);
  return (
    <section className="financials-archive">
      <header className="financials-archive-header">
        <div>
          <p className="financials-section-kicker">Local filing archive</p>
          <h2>已抓取财报</h2>
          <p>按公司归档本地报告。选择一份报告，进入三表、质量检查与 Agent 解读详情。</p>
        </div>
        <dl>
          <div><dt>公司</dt><dd>{archivedInstruments.length}</dd></div>
          <div><dt>报告</dt><dd>{reportCount}</dd></div>
        </dl>
      </header>

      {loading ? <div className="financials-archive-empty">正在整理本地财报档案…</div> : archivedInstruments.length ? (
        <div className="financials-company-list">
          {archivedInstruments.map((instrument) => {
            const companyReports = reportsByInstrument[instrument.id] ?? [];
            return (
              <article className="financials-company-group" key={instrument.id}>
                <header className="financials-company-header">
                  <div className="financials-company-identity">
                    <span>{instrument.market}</span>
                    <div>
                      <h3>{instrument.name}</h3>
                      <p>{instrument.code} · {companyReports.length} 份本地报告</p>
                    </div>
                  </div>
                  <button
                    className={captureInstrumentId === instrument.id ? 'active' : ''}
                    type="button"
                    aria-label={`抓取 ${instrument.name} 新报告`}
                    onClick={() => onCapture(instrument)}
                  >
                    {captureInstrumentId === instrument.id ? '已选择抓取' : '抓取新报告'}
                  </button>
                </header>
                {companyReports.length ? (
                  <div className="financials-company-reports">
                    {companyReports.map((item) => (
                      <button
                        className="financials-report-row"
                        type="button"
                        key={item.id}
                        aria-label={`查看 ${reportLabel(item.periodEnd, item.reportType)} · ${instrument.name}`}
                        onClick={() => onOpenReport(instrument, item)}
                      >
                        <time dateTime={item.periodEnd}>{item.periodEnd.slice(0, 4)}</time>
                        <strong>{reportLabel(item.periodEnd, item.reportType)}</strong>
                        <span>{item.sourceCode}</span>
                        <span className={`financials-report-status ${item.qualityStatus.toLowerCase()}`}>
                          {qualityLabels[item.qualityStatus]}
                        </span>
                        <small>{item.audited ? '已审计' : '未标记审计'} <b aria-hidden="true">→</b></small>
                      </button>
                    ))}
                  </div>
                ) : <div className="financials-company-empty">尚未抓取报告，可从这里建立第一份财报底稿。</div>}
              </article>
            );
          })}
        </div>
      ) : (
        <div className="financials-archive-empty">
          <strong>还没有已抓取财报</strong>
          <span>在上方输入公司名称或股票代码，选择报告期后即可建立第一份本地档案。</span>
        </div>
      )}
    </section>
  );
}

function GlobalCompanyIdentity({ company }: { company: CompanySearchResult }) {
  const security = company.securities[0];
  return (
    <div className="global-company-identity">
      <span className={`global-company-capability ${company.capabilityLevel.toLowerCase()}`}>
        {company.capabilityLevel === 'L4' ? '完整分析可用'
          : company.capabilityLevel === 'L3' ? '三张表可用'
            : company.capabilityLevel === 'L2' ? '原始披露可用' : '公司已识别'}
      </span>
      <strong>{security ? `${security.symbol} · ${security.exchange} · ${company.countryCode ?? security.market}` : company.countryCode}</strong>
      {company.nativeName && company.nativeName !== company.displayName && <small>{company.nativeName}</small>}
    </div>
  );
}

function GlobalCompanyWorkspace({ company }: { company: CompanySearchResult }) {
  const disclosureUrl = officialDisclosureUrl(company);
  return (
    <section className="global-company-workspace">
      <div className="global-company-workspace-mark" aria-hidden="true">{company.countryCode ?? 'GL'}</div>
      <div>
        <p className="financials-section-kicker">Disclosure capability</p>
        <h4>{company.displayName} 披露抓取</h4>
        <p>
          {company.providerCode === 'SEC_EDGAR'
            ? '已关联 SEC 公司主体和全部上市代码。选择财年与报告类型后，可直接抓取 Company Facts 并生成本地三表底稿。'
            : company.providerCode === 'KRX_KIND'
              ? '已关联韩国交易所公司主体。可通过全网搜索定位官方 DART XBRL 披露，并生成本地 K-IFRS 三表底稿。'
              : '该公司已经可以独立进入财报工作台，后续数据抓取不会依赖自选关系。'}
        </p>
        {disclosureUrl && (
          <a className="global-company-disclosure-link" href={disclosureUrl} target="_blank" rel="noreferrer">
            查看官方披露
          </a>
        )}
        <dl>
          <div><dt>主体标识</dt><dd>{company.providerCompanyId}</dd></div>
          <div><dt>目录来源</dt><dd>{company.providerCode}</dd></div>
          <div><dt>当前能力</dt><dd>{company.capabilityLevel}</dd></div>
        </dl>
      </div>
    </section>
  );
}

function supportsGlobalFetch(providerCode: string) {
  return providerCode === 'SEC_EDGAR' || providerCode === 'KRX_KIND';
}

function officialDisclosureUrl(company: CompanySearchResult) {
  if (company.providerCode !== 'SEC_EDGAR') return undefined;
  const cik = company.providerCompanyId.replace(/^CIK/i, '');
  return cik ? `https://www.sec.gov/edgar/browse/?CIK=${cik}&owner=exclude` : undefined;
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

function mergeReports(current: FinancialReport[], incoming: FinancialReport[]) {
  const reportsById = new Map(current.map((report) => [report.id, report]));
  incoming.forEach((report) => reportsById.set(report.id, report));
  return Array.from(reportsById.values()).sort((left, right) => right.periodEnd.localeCompare(left.periodEnd));
}

function messageOf(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason.message : fallback;
}
