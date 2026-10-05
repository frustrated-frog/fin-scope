import { useId } from 'react';

import { FinancialEvidenceRefs } from './FinancialEvidenceRefs';
import { formatEvidenceValue, reportLabel } from './financialPresentation';
import { FinancialEvidence, FinancialInterpretationClaim, FinancialInterpretationScope, FinancialInterpretationSection } from './financialTypes';
import './financialReport.css';

const assessments = { POSITIVE: '积极', NEUTRAL: '中性', NEGATIVE: '承压', INSUFFICIENT_EVIDENCE: '证据不足' };
const confidences = { HIGH: '高置信度', MEDIUM: '中置信度', LOW: '低置信度' };
const terms = [
  ['归母净利润', '归属于母公司股东的净利润，与包含少数股东损益的合并净利润有区别。比较净利率或现金转化率时要核对所用分母。'],
  ['毛利率', '收入扣除营业成本后的毛利与收入之比。营业总成本可能包含期间费用，不能直接替代营业成本。'],
  ['同比与环比', '同比比较上年同期，环比比较相邻期间。季度和累计口径不能直接混比，季节性会影响环比解释。'],
  ['营运资金', '日常经营占用的资金。应收和存货增加通常占用资金，应付和预收的变化也会影响现金收付。'],
  ['自由现金流', '本系统采用经营现金流减资本开支的口径。不同研究材料可能使用不同定义，比较前应核对。'],
  ['速动比率', '本系统按流动资产扣除存货后与流动负债比较，以倍数展示；受限资金等仍需核查附注。'],
  ['ROE', '净资产收益率，通常以匹配期间的净利润与平均股东权益计算。权益减少或财务杠杆提高也可能推高ROE。'],
  ['非经常性损益', '不属于通常持续经营结果或具有偶发性质的损益，识别和调整需依据对应市场的披露口径与原文。']
];

const claimTypes = { FACT: '事实', INFERENCE: '推断', WATCHPOINT: '观察' };

type EvidenceProps = {
  evidenceById: Map<string, FinancialEvidence>;
  onEvidence: (id: string) => void;
};

export function FinancialReportScope({ scope }: { scope: FinancialInterpretationScope }) {
  return <section className="financial-report-scope" aria-label="报告范围与材料覆盖">
    <div className="financial-report-scope-title">
      <span>研究范围</span>
      <h4>{scope.companyName} · {reportLabel(scope.periodEnd, scope.reportType)}</h4>
      <p>报告期末 {scope.periodEnd} · {scope.market || '市场未标记'} · {scope.scope === 'CONSOLIDATED' ? '合并口径' : scope.scope} · {scope.currency || '币种未标记'}</p>
    </div>
    <dl>
      <div><dt>底稿来源</dt><dd>{scope.sourceCode}</dd></div>
      <div><dt>本地历史</dt><dd>{scope.historicalReportCount} 份同口径报告</dd></div>
      <div><dt>本次分析材料</dt><dd>{scope.modelEvidenceCount} 条证据</dd></div>
    </dl>
    {scope.comparablePeriods.length > 0 && <p className="financial-report-periods">历史报告期：{scope.comparablePeriods.join(' / ')}</p>}
    <div className="financial-report-materials">
      <strong>材料覆盖边界</strong>
      {scope.materialLimitations.map((text) => <p key={text}>{text}</p>)}
    </div>
  </section>;
}

export function FinancialInterpretationReportReader({ sections, ...evidenceProps }: EvidenceProps & {
  sections: FinancialInterpretationSection[];
}) {
  const reportId = useId().replace(/:/g, '');
  const chapterId = (code: string) => `financial-report-${reportId}-${code}`;
  return <div className="financial-report-reader">
    <nav className="financial-report-directory" aria-label="研究报告章节目录">
      <strong>逐章阅读</strong>
      <p>先看事实，再读解释，最后核查反证。</p>
      {sections.map((section, index) => <a key={section.code} href={`#${chapterId(section.code)}`}>
        <span>{String(index + 1).padStart(2, '0')}</span>{section.title}
        {section.assessment === 'INSUFFICIENT_EVIDENCE' && <small>待补材料</small>}
      </a>)}
    </nav>
    <div className="financial-report-chapters">
      <details className="financial-report-learning financial-report-glossary">
        <summary>财务术语速查 · 点击展开解释</summary>
        <div><p>以下是一般财务知识，不作为这家公司的事实或行业判断。</p>
          {terms.map(([term, definition]) => <details key={term}><summary>{term}</summary><p>{definition}</p></details>)}
        </div>
      </details>
      {sections.map((section, index) => <section id={chapterId(section.code)} className="financial-report-chapter" key={section.code} aria-labelledby={`${chapterId(section.code)}-title`}>
        <header>
          <span className="financial-report-chapter-number">{String(index + 1).padStart(2, '0')}</span>
          <div><h4 id={`${chapterId(section.code)}-title`}>{section.title}</h4>
            <div className="financial-report-assessment" data-assessment={section.assessment.toLowerCase()}>
              <span>{assessments[section.assessment]}</span><span>{confidences[section.confidence]}</span>
            </div>
          </div>
        </header>
        <p className="financial-report-thesis">{section.summary}</p>
        <FinancialEvidenceRefs refs={section.refs} {...evidenceProps} />
        <ChapterClaims title="关键事实" claims={section.facts} {...evidenceProps} showValues />
        <ChapterClaims title="分析过程与经营含义" claims={section.analysis} {...evidenceProps} />
        {section.learningExplanation && <details className="financial-report-learning">
          <summary>通俗解释 · 怎么读这一章</summary>
          <div><p>{section.learningExplanation}</p>
            {section.commonMisreading && <p><strong>常见误读</strong>{section.commonMisreading}</p>}
          </div>
        </details>}
        <ChapterClaims title="替代解释与反证" claims={section.counterEvidence} {...evidenceProps} tone="counter" />
        <ChapterClaims title="下一步验证" claims={section.watchpoints} {...evidenceProps} tone="watch" />
        {section.limitations.length > 0 && <aside className="financial-report-chapter-limits" aria-label={`${section.title}的材料缺口`}>
          <strong>本章还缺什么</strong>
          {section.limitations.map((text) => <p key={text}>{text}</p>)}
        </aside>}
      </section>)}
      <section className="financial-report-reading-path" aria-label="财报学习阅读路径">
        <h4>回到三张表，自己核查一遍</h4>
        <ol>
          <li>先看利润表：对照收入、成本、费用和利润，确认变化发生在哪里。</li>
          <li>再看现金流量表：核查经营现金流能否支持利润，区分经营、投资和融资。</li>
          <li>回到资产负债表：结合应收、存货、现金和债务寻找资金变化的线索。</li>
          <li>整理未解决问题：按各章“下一步验证”补读业务说明、审计意见和重要附注。</li>
        </ol>
      </section>
    </div>
  </div>;
}

function ChapterClaims({ title, claims, evidenceById, onEvidence, showValues = false, tone = 'normal' }: EvidenceProps & {
  title: string;
  claims: FinancialInterpretationClaim[];
  showValues?: boolean;
  tone?: 'normal' | 'counter' | 'watch';
}) {
  if (!claims.length) {
    return null;
  }
  return <div className="financial-report-claim-group" data-tone={tone}>
    <h5>{title}</h5>
    {claims.map((claim, index) => <article key={`${claim.claim}-${index}`}>
      <div className="financial-report-claim-meta"><span>{claimTypes[claim.claimType]}</span>
        {claim.confidence && <span>{confidences[claim.confidence]}</span>}
      </div>
      <p>{claim.claim}</p>
      {showValues && <div className="financial-report-values">
        {claim.refs.map((id) => {
          const item = evidenceById.get(id);
          if (!item || !item.value || item.type === 'DATA_GAP' || item.type === 'FINDING' || item.type === 'TREND') {
            return null;
          }
          return <button type="button" key={id} aria-label={`核查${item.label}的数值`} onClick={() => onEvidence(id)}>
            <span>{item.label}</span><strong>{formatEvidenceValue(item)}</strong><small>{item.period || '期间未标记'}{item.detail?.includes('口径=') ? ` · ${item.detail.split('；')[0].replace('口径=', '')}` : ''}</small>
          </button>;
        })}
      </div>}
      <FinancialEvidenceRefs refs={claim.refs} evidenceById={evidenceById} onEvidence={onEvidence} />
    </article>)}
  </div>;
}
