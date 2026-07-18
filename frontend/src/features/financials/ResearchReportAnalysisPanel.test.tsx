import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { ResearchReportAnalysisPanel } from './ResearchReportAnalysisPanel';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const report = {
  id: 12,
  instrumentId: 7,
  linkedFinancialReportId: 9,
  title: '贵州茅台深度报告',
  institution: '测试证券',
  analyst: '张三',
  publishedDate: '2026-04-20',
  reportType: 'DEEP_DIVE',
  rating: '买入',
  targetPrice: 1800,
  targetPriceCurrency: 'CNY',
  sourceType: 'UPLOAD',
  originalFileName: 'research.pdf',
  pageCount: 30,
  parseStatus: 'PARSED',
  analysisStatus: 'LLM',
  qualityLevel: 'HIGH'
};

const detail = {
  report: { ...report, extractedText: '完整研报原文：公司业务、行业格局、盈利预测和风险分析。' },
  analysis: {
    executiveSummary: ['品牌壁垒稳固', '产品结构升级支撑盈利能力'],
    investmentThesis: ['高端品牌具有定价权', '直营渠道提升经营效率'],
    businessAnalysis: ['高端产品收入占比持续提升', '渠道库存需要跟踪'],
    industryAnalysis: ['行业进入存量竞争阶段'],
    keyAssumptions: ['高端需求保持稳定'],
    catalysts: ['新品放量'],
    risks: ['消费需求恢复不及预期'],
    learningNotes: ['先核对量价假设，再看利润预测'],
    evidenceSections: {
      executiveSummary: [
        { text: '品牌壁垒稳固', sourceQuote: '公司拥有长期积累的品牌壁垒', sourcePage: 5 },
        { text: '产品结构升级支撑盈利能力', sourceQuote: '产品结构持续升级', sourcePage: 8 }
      ]
    },
    glossary: [{ term: '吨价', explanation: '销售收入除以销量' }],
    limitations: ['预测依赖需求假设'],
    disclaimer: '仅供研究学习，不构成投资建议。'
  },
  forecasts: [{
    id: 1,
    metricCode: 'REVENUE',
    metricLabel: '营业收入',
    forecastPeriod: '2025-12-31',
    forecastValue: 1000,
    unit: 'CNY',
    sourceQuote: '预计2025年营业收入1000亿元',
    sourcePage: 18,
    actualValue: 1100,
    actualUnit: 'CNY',
    actualPeriod: '2025-12-31',
    variancePercent: 10,
    verificationStatus: 'VERIFIED',
    verificationReason: '实际值与预测值的偏差不超过10%'
  }, {
    id: 3,
    metricCode: 'GROSS_MARGIN',
    metricLabel: '毛利率',
    forecastPeriod: '2025-12-31',
    forecastValue: 50,
    unit: '%',
    sourceQuote: '预计毛利率50%',
    actualValue: 55,
    actualUnit: '%',
    actualPeriod: '2025-12-31',
    variancePercent: 5,
    verificationStatus: 'VERIFIED',
    verificationReason: '实际毛利率与预测值相差不超过10个百分点'
  }],
  claims: [{
    id: 2,
    category: 'INVESTMENT_THESIS',
    title: '产品结构升级',
    detail: '高端产品占比提升有望支撑毛利率',
    claimType: 'OPINION',
    sourceQuote: '产品结构持续升级',
    sourcePage: 8,
    financialMetricCode: 'GROSS_MARGIN',
    verificationStatus: 'EVIDENCE_FOUND',
    verificationReason: '已找到财报事实，请结合研报论证方向人工核对',
    evidenceLabel: '毛利率',
    evidenceValue: '55',
    evidenceUnit: '%',
    evidencePeriod: '2025-12-31'
  }]
};

const emptySync = {
  status: 'SUCCESS',
  sourceCode: 'EASTMONEY',
  candidates: [],
  importedReports: [],
  importedCount: 0,
  skippedCount: 0,
  failedCount: 0,
  errors: [],
  completedAt: '2026-07-19T01:00:00'
};

beforeEach(() => vi.clearAllMocks());

test('renders a detailed research learning view linked to financial facts', async () => {
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path === '/api/financials/instruments/7/research-reports/sync?financialReportId=9') return emptySync;
    if (path === '/api/financials/instruments/7/research-reports') return [report];
    if (path === '/api/financials/research-reports/12?financialReportId=9') return detail;
    throw new Error(`unexpected api call: ${path}`);
  });

  render(<ResearchReportAnalysisPanel instrumentId={7} financialReportId={9} />);

  expect(await screen.findByRole('heading', { name: '研报核心结论' })).toBeInTheDocument();
  expect(screen.getByText('产品结构升级支撑盈利能力')).toBeInTheDocument();
  expect(screen.getAllByText('“产品结构持续升级” — 第 8 页').length).toBeGreaterThan(0);
  expect(screen.getByRole('heading', { name: '业务与公司分析' })).toBeInTheDocument();
  expect(screen.getByText('高端产品收入占比持续提升')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '盈利预测与实际财报' })).toBeInTheDocument();
  expect(screen.getByText('实际值与预测值的偏差不超过10%')).toBeInTheDocument();
  expect(screen.getByText('5.00 个百分点')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '研报观点 × 财报事实' })).toBeInTheDocument();
  expect(screen.getByText('毛利率 55%')).toBeInTheDocument();
  expect(screen.getByText('先核对量价假设，再看利润预测')).toBeInTheDocument();
  expect(screen.getByText('完整研报原文：公司业务、行业格局、盈利预测和风险分析。')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '重新详细解析' }))
    .toHaveClass('broker-research-button--reanalyze');
});

test('uploads a PDF with learning metadata and immediately displays the analysis', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path === '/api/financials/instruments/7/research-reports/sync?financialReportId=9') return emptySync;
    if (path === '/api/financials/instruments/7/research-reports') return [];
    if (path === '/api/financials/research-reports/upload' && options?.method === 'POST') return detail;
    throw new Error(`unexpected api call: ${path}`);
  });

  render(<ResearchReportAnalysisPanel instrumentId={7} financialReportId={9} />);
  await screen.findByText('暂未自动获取到公开研报');
  await user.type(screen.getByLabelText('研报标题'), '贵州茅台深度报告');
  await user.type(screen.getByLabelText('机构'), '测试证券');
  await user.upload(screen.getByLabelText('上传研报 PDF'),
    new File(['%PDF-1.4'], 'research.pdf', { type: 'application/pdf' }));
  await user.click(screen.getByRole('button', { name: '上传并详细解读' }));

  await waitFor(() => expect(api).toHaveBeenCalledWith(
    '/api/financials/research-reports/upload',
    expect.objectContaining({ method: 'POST', body: expect.any(FormData) })
  ));
  expect(await screen.findByRole('heading', { name: '研报核心结论' })).toBeInTheDocument();
});

test('does not let a stale reanalysis response overwrite a newly selected report', async () => {
  const user = userEvent.setup();
  const otherReport = { ...report, id: 13, title: '另一篇研报' };
  const otherDetail = { ...detail, report: { ...detail.report, id: 13, title: '另一篇研报' } };
  let resolveReanalysis!: (value: typeof detail) => void;
  const pendingReanalysis = new Promise<typeof detail>((resolve) => { resolveReanalysis = resolve; });
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path === '/api/financials/instruments/7/research-reports/sync?financialReportId=9') return emptySync;
    if (path === '/api/financials/instruments/7/research-reports') return [report, otherReport];
    if (path === '/api/financials/research-reports/12?financialReportId=9') return detail;
    if (path === '/api/financials/research-reports/13?financialReportId=9') return otherDetail;
    if (path === '/api/financials/research-reports/12/reanalyze?financialReportId=9') return pendingReanalysis;
    throw new Error(`unexpected api call: ${path}`);
  });

  render(<ResearchReportAnalysisPanel instrumentId={7} financialReportId={9} />);
  await screen.findByRole('heading', { name: '贵州茅台深度报告' });
  await user.click(screen.getByRole('button', { name: '重新详细解析' }));
  await user.selectOptions(screen.getByLabelText('当前研报'), '13');
  expect(await screen.findByRole('heading', { name: '另一篇研报' })).toBeInTheDocument();

  resolveReanalysis(detail);
  await waitFor(() => expect(screen.getByRole('heading', { name: '另一篇研报' })).toBeInTheDocument());
});

test('automatically syncs public reports and imports a selected candidate for detailed reading', async () => {
  const user = userEvent.setup();
  const candidate = {
    sourceCode: 'EASTMONEY',
    externalId: 'AP1',
    sourceUrl: 'https://pdf.dfcfw.com/pdf/H3_AP1_1.pdf',
    stockCode: '600519',
    title: '贵州茅台公司点评',
    institution: '公开证券',
    analyst: '李四',
    publishedDate: '2026-07-18',
    rating: '增持',
    pageCount: 12,
    availability: 'AVAILABLE'
  };
  const sync = { ...emptySync, candidates: [candidate] };
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path === '/api/financials/instruments/7/research-reports/sync?financialReportId=9'
        && options?.method === 'POST') return sync;
    if (path === '/api/financials/instruments/7/research-reports') return [];
    if (path === '/api/financials/instruments/7/research-reports/import'
        && options?.method === 'POST') return detail;
    throw new Error('unexpected api call: ' + path);
  });

  render(<ResearchReportAnalysisPanel instrumentId={7} financialReportId={9} />);

  expect(await screen.findByText('贵州茅台公司点评')).toBeInTheDocument();
  expect(screen.getByText('公开证券 · 李四 · 2026-07-18')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '同步最新研报' }))
    .toHaveClass('broker-research-button--sync');
  const importButton = screen.getByRole('button', { name: '导入并详细解读' });
  expect(importButton).toHaveClass('broker-research-button--import');
  await user.click(importButton);

  await waitFor(() => expect(api).toHaveBeenCalledWith(
    '/api/financials/instruments/7/research-reports/import',
    expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        sourceCode: 'EASTMONEY',
        externalId: 'AP1',
        financialReportId: 9
      })
    })
  ));
  expect(await screen.findByRole('heading', { name: '研报核心结论' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '阅读详细解读' }))
    .toHaveClass('broker-research-button--read');
});

test('keeps manual upload available when automatic synchronization fails', async () => {
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path === '/api/financials/instruments/7/research-reports/sync?financialReportId=9') {
      throw new Error('公开研报来源暂时不可用');
    }
    if (path === '/api/financials/instruments/7/research-reports') return [];
    throw new Error('unexpected api call: ' + path);
  });

  render(<ResearchReportAnalysisPanel instrumentId={7} financialReportId={9} />);

  expect(await screen.findByRole('alert')).toHaveTextContent('公开研报来源暂时不可用');
  expect(screen.getByLabelText('上传研报 PDF')).toBeInTheDocument();
});

test('does not let an old company synchronization overwrite the newly selected company', async () => {
  const nextReport = { ...report, id: 88, instrumentId: 8, title: '新公司研报' };
  const nextDetail = { ...detail, report: { ...detail.report, ...nextReport } };
  let resolveOldSync!: (value: typeof emptySync) => void;
  const oldSync = new Promise<typeof emptySync>((resolve) => { resolveOldSync = resolve; });
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path === '/api/financials/instruments/7/research-reports/sync?financialReportId=9') return oldSync;
    if (path === '/api/financials/instruments/8/research-reports/sync?financialReportId=9') return emptySync;
    if (path === '/api/financials/instruments/8/research-reports') return [nextReport];
    if (path === '/api/financials/research-reports/88?financialReportId=9') return nextDetail;
    if (path === '/api/financials/instruments/7/research-reports') return [];
    throw new Error('unexpected api call: ' + path);
  });

  const view = render(<ResearchReportAnalysisPanel instrumentId={7} financialReportId={9} />);
  view.rerender(<ResearchReportAnalysisPanel instrumentId={8} financialReportId={9} />);

  expect(await screen.findByRole('heading', { name: '新公司研报' })).toBeInTheDocument();
  resolveOldSync(emptySync);
  await waitFor(() =>
    expect(screen.getByRole('heading', { name: '新公司研报' })).toBeInTheDocument());
});
