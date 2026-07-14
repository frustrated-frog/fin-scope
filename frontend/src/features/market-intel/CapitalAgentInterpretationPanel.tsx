import { CapitalInterpretation } from './marketIntelTypes';

const confidenceLabels = { LOW: '低置信度', MID: '中置信度', HIGH: '高置信度' } as const;

export function CapitalAgentInterpretationPanel({
  interpretation,
  busy,
  onRun
}: {
  interpretation: CapitalInterpretation | null;
  busy: boolean;
  onRun: () => void;
}) {
  return (
    <section className="market-intel-agent" aria-labelledby="capital-agent-heading">
      <header>
        <div>
          <p className="market-intel-kicker">Evidence-bound Agent</p>
          <h3 id="capital-agent-heading">Agent 深度解读</h3>
        </div>
        <button className="primary-button" type="button" disabled={busy} onClick={onRun}>
          {busy ? 'Agent 分析中…' : interpretation ? '重新运行 Agent 解读' : '运行 Agent 解读'}
        </button>
      </header>

      {!interpretation && !busy && (
        <div className="market-intel-agent-primer">
          <span aria-hidden="true">AI</span>
          <p>点击后才调用模型。Agent 只能引用上方已保存的资金事实，并会把“拆单、吸筹、出货”等判断标记为待验证假设。</p>
        </div>
      )}
      {busy && <p className="market-intel-agent-running" role="status">正在组装事实、校验证据引用并生成反证清单…</p>}
      {interpretation && (
        <div className="market-intel-agent-report">
          <p className="market-intel-agent-summary">{interpretation.plainSummary}</p>
          <div className="market-intel-agent-columns">
            <section>
              <h4>确认事实</h4>
              <ul>{interpretation.facts.map((fact) => <li key={fact}>{fact}</li>)}</ul>
            </section>
            <section>
              <h4>接下来观察</h4>
              <ul>{interpretation.observationPoints.map((point) => <li key={point}>{point}</li>)}</ul>
            </section>
          </div>
          {interpretation.hypotheses.map((hypothesis, index) => (
            <article className="market-intel-hypothesis" key={`${hypothesis.type}-${index}`}>
              <header>
                <span>{hypothesis.type.replace(/_/g, ' ')}</span>
                <strong className={`confidence-${hypothesis.confidence.toLowerCase()}`}>
                  {confidenceLabels[hypothesis.confidence]}
                </strong>
              </header>
              <p>{hypothesis.claim}</p>
              <dl>
                <div><dt>证据引用</dt><dd>{hypothesis.supportingMetricRefs.join(' · ') || '无有效引用'}</dd></div>
                <div><dt>反证 / 限制</dt><dd>{hypothesis.counterEvidence.join('；') || '暂无'}</dd></div>
              </dl>
            </article>
          ))}
          <p className="market-intel-disclaimer">{interpretation.disclaimer}</p>
        </div>
      )}
    </section>
  );
}
