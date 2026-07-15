import { useMemo, useState } from 'react';
import {
  datasetEvidenceNotice,
  explainFactorAnalysis,
  lifecycleLabel,
  researchDirectionLabel
} from './factorPresentation';
import { QuantDataset, QuantFactorAnalysis, ResearchFactorDefinition } from './quantTypes';
import { FactorResearchAgentPanel } from './FactorResearchAgentPanel';

interface FactorGuideProps {
  definitions: ResearchFactorDefinition[];
  selectedCode?: string;
  onSelect: (code: string) => void;
  selectedDataset?: QuantDataset;
  availableFactors?: Set<string>;
  analysis?: QuantFactorAnalysis;
  busy?: boolean;
  onAnalyze: (code: string) => void;
  researchDraftId?: number;
  addToast?: (message: string, type?: 'success' | 'error' | 'info') => void;
}

function percent(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

export function FactorGuide({
  definitions,
  selectedCode,
  onSelect,
  selectedDataset,
  availableFactors,
  analysis,
  busy,
  onAnalyze,
  researchDraftId,
  addToast = () => undefined
}: FactorGuideProps) {
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('全部');
  const categories = useMemo(
    () => ['全部', ...Array.from(new Set(definitions.map(item => item.category)))],
    [definitions]
  );
  const filtered = definitions.filter(item => {
    const matchesCategory = category === '全部' || item.category === category;
    const term = query.trim().toLocaleLowerCase();
    const matchesQuery = !term
      || item.name.toLocaleLowerCase().includes(term)
      || item.identity.code.toLocaleLowerCase().includes(term);
    return matchesCategory && matchesQuery;
  });
  const selected = definitions.find(item => item.identity.code === selectedCode) ?? definitions[0];

  if (!selected) {
    return <div className="quant-factor-empty" role="status">专业因子目录暂不可用，请稍后重试。</div>;
  }

  const isDatasetReady = selectedDataset?.status === 'READY';
  const isAvailable = Boolean(availableFactors?.has(selected.identity.code));
  const canAnalyze = Boolean(isDatasetReady && isAvailable);
  const explanation = analysis && selectedDataset
    ? explainFactorAnalysis(analysis, selected.expectedDirection, selectedDataset.dataKind)
    : undefined;

  return <div className="quant-factor-explorer">
    <aside className="quant-factor-index" aria-label="因子目录">
      <header>
        <span>FACTOR INDEX</span>
        <strong>{definitions.length} 个可审计定义</strong>
      </header>
      <label className="quant-factor-search">
        <span>搜索因子</span>
        <input
          type="search"
          aria-label="搜索因子"
          placeholder="名称或代码"
          value={query}
          onChange={event => setQuery(event.target.value)}
        />
      </label>
      <div className="quant-factor-categories" aria-label="因子分类">
        {categories.map(item => <button
          type="button"
          key={item}
          className={category === item ? 'active' : ''}
          aria-pressed={category === item}
          onClick={() => setCategory(item)}
        >{item}</button>)}
      </div>
      <nav className="quant-factor-list" aria-label="可研究因子">
        {filtered.map(item => <button
          type="button"
          key={item.identity.namespace + item.identity.code}
          className={selected.identity.code === item.identity.code ? 'active' : ''}
          aria-current={selected.identity.code === item.identity.code ? 'page' : undefined}
          onClick={() => onSelect(item.identity.code)}
        >
          <span><strong>{item.name}</strong><small>{item.identity.code}</small></span>
          <em>{lifecycleLabel(item.status)}</em>
        </button>)}
        {!filtered.length && <p>没有匹配的因子</p>}
      </nav>
    </aside>

    <article className="quant-factor-guide" aria-labelledby="factor-guide-title">
      <header className="quant-factor-guide-head">
        <div>
          <p>{selected.identity.namespace.toUpperCase()} · {selected.category} · v{selected.identity.version}</p>
          <h4 id="factor-guide-title">{selected.name}</h4>
          <code>{selected.identity.code}</code>
        </div>
        <span data-status={selected.status}>{lifecycleLabel(selected.status)}</span>
      </header>

      <section className="quant-factor-plain" aria-label="一分钟看懂">
        <div><span>01</span><h5>它衡量什么</h5><p>{selected.plainMeaning}</p></div>
        <div><span>02</span><h5>何时可能有帮助</h5><p>{selected.hypothesis}</p></div>
        <div><span>03</span><h5>最容易误读的地方</h5><p>{selected.interpretationBoundary}</p></div>
        <div><span>04</span><h5>默认研究方向</h5><p>{researchDirectionLabel(selected.expectedDirection)}</p></div>
      </section>

      <section className="quant-factor-why">
        <div><span>WHY IT MAY EXIST</span><h5>为什么有人研究它</h5></div>
        <p>{selected.economicRationale}</p>
      </section>

      <details className="quant-factor-details">
        <summary>公式、字段与版本</summary>
        <dl>
          <div><dt>确定性公式</dt><dd><code>{selected.calculationKey}</code></dd></div>
          <div><dt>必需字段</dt><dd>{selected.requiredFields.join(' · ')}</dd></div>
          <div><dt>可获得时间</dt><dd>{selected.availableAtRule}</dd></div>
          <div><dt>缺失处理</dt><dd>{selected.missingPolicy}</dd></div>
          <div><dt>数据来源</dt><dd>{selected.sourceType} · {selected.sourceRef}</dd></div>
          <div><dt>计算版本</dt><dd>{selected.calculationVersion}</dd></div>
          <div><dt>评价协议</dt><dd>{selected.evaluationPolicyCode} · v{selected.evaluationPolicyVersion}</dd></div>
        </dl>
      </details>

      <section className="quant-factor-diagnostic" aria-labelledby="factor-diagnostic-title">
        <header>
          <div><span>EVIDENCE CHECK</span><h5 id="factor-diagnostic-title">用数据检验，不凭名字下结论</h5></div>
          <button
            type="button"
            disabled={!canAnalyze || busy}
            onClick={() => onAnalyze(selected.identity.code)}
          >{busy ? '正在计算…' : '用当前数据集验证'}</button>
        </header>
        {!selectedDataset && <p className="quant-factor-blocker">先在策略实验室选择一份数据集。</p>}
        {selectedDataset && !isDatasetReady && <p className="quant-factor-blocker">当前数据集尚未通过质量门禁。</p>}
        {selectedDataset && isDatasetReady && !isAvailable && <p className="quant-factor-blocker">当前数据集没有可计算输入；资金因子需要先冻结资金分区并通过质量门禁。</p>}
        {selectedDataset && <p className="quant-factor-dataset-note">{datasetEvidenceNotice(selectedDataset.dataKind)}</p>}
        {explanation && analysis && <div className="quant-factor-evidence" data-level={explanation.evidenceLevel}>
          <div className="quant-factor-verdict">
            <span>当前样本描述</span>
            <strong>{explanation.headline}</strong>
            <p>{explanation.detail}</p>
          </div>
          <dl>
            <div><dt>方向对齐 IC</dt><dd>{explanation.directionAdjustedIcMean.toFixed(3)}</dd></div>
            <div><dt>方向一致日占比</dt><dd>{percent(explanation.favorableIcRatio)}</dd></div>
            <div><dt>原始 ICIR</dt><dd>{analysis.icIr.toFixed(2)}</dd></div>
            <div><dt>有效交易日</dt><dd>{analysis.sampleCount}</dd></div>
            {analysis.directionAdjustedCiLower !== undefined && analysis.directionAdjustedCiUpper !== undefined
              ? <div><dt>95% HAC 区间</dt><dd>[{analysis.directionAdjustedCiLower.toFixed(3)}, {analysis.directionAdjustedCiUpper.toFixed(3)}]</dd></div> : null}
            {analysis.minCrossSectionSize !== undefined ? <div><dt>最小横截面</dt><dd>{analysis.minCrossSectionSize} 只</dd></div> : null}
            {analysis.coverageRatio !== undefined ? <div><dt>有效日期覆盖</dt><dd>{percent(analysis.coverageRatio)}</dd></div> : null}
            {analysis.directionAdjustedQuantileSpread !== undefined ? <div><dt>首尾分位差</dt><dd>{percent(analysis.directionAdjustedQuantileSpread)}</dd></div> : null}
            {analysis.directionAdjustedMonotonicity !== undefined ? <div><dt>分位单调性</dt><dd>{analysis.directionAdjustedMonotonicity.toFixed(2)}</dd></div> : null}
          </dl>
          {analysis.evaluationPolicyVersion && <p className="quant-factor-conclusion">评价门禁：{analysis.evaluationPolicyVersion} · {analysis.validationEligible ? '具备结论资格' : '未通过准入'}</p>}
          {analysis.conclusion && <p className="quant-factor-conclusion">研究结论：{analysis.conclusion === 'INCONCLUSIVE' ? '证据不足' : analysis.conclusion === 'SUPPORTED' ? '支持假设' : '反驳假设'}。生命周期不会由本次诊断自动升级。</p>}
          {analysis.caveats?.length ? <ul className="quant-factor-caveats">{analysis.caveats.map(item => <li key={item}>{item}</li>)}</ul> : null}
          <small>数据集指纹 {analysis.datasetFingerprint.slice(0, 16)}</small>
        </div>}
      </section>
      <FactorResearchAgentPanel factor={selected} dataset={selectedDataset} researchDraftId={researchDraftId}
        enabled={canAnalyze} addToast={addToast} />
    </article>
  </div>;
}
