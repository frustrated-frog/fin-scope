import { CapitalRuleExplanation } from './marketIntelTypes';

export function CapitalRuleExplanationCard({ explanation }: { explanation: CapitalRuleExplanation }) {
  return (
    <section className="market-intel-rule" aria-labelledby="capital-rule-heading">
      <header>
        <div>
          <p className="market-intel-kicker">自动规则解释 · 不调用模型</p>
          <h3 id="capital-rule-heading">这组数据怎么读</h3>
        </div>
        <span className="market-intel-version">{explanation.ruleVersion}</span>
      </header>
      <p className="market-intel-rule-summary">{explanation.summary}</p>
      <ul className="market-intel-rule-list">
        {explanation.items.map((item, index) => (
          <li key={`${item.text}-${index}`}>
            <span className={`market-intel-rule-level ${item.level.toLowerCase()}`}>{item.level}</span>
            <div>
              <strong>{item.text}</strong>
              <small>{item.metricRefs.join(' · ')}</small>
            </div>
          </li>
        ))}
      </ul>
      {explanation.dataGaps.length > 0 && (
        <div className="market-intel-gap-note">
          <strong>现在不能下的结论</strong>
          <ul>{explanation.dataGaps.map((gap) => <li key={gap}>{gap}</li>)}</ul>
        </div>
      )}
    </section>
  );
}

