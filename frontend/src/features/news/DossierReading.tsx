import { Fragment, type ReactNode } from 'react';
import { conceptLine, interpretationText, type NewsReading } from './newsReading';

export function DossierReading({ reading, facts, whyItMatters, newDevelopment, impactChain = [], detailsLabel, singleSource }: {
  reading?: NewsReading | null;
  facts: string[];
  whyItMatters?: string;
  newDevelopment?: string;
  impactChain?: string[];
  detailsLabel?: string;
  singleSource?: boolean;
}) {
  return <div className="radar-interpretation-grid">
    <div className="radar-dossier-findings">
      <InterpretationBlock label={reading ? '一句话看懂' : '已确认事实'}
        value={reading?.lead ?? facts.join('\n')} fact singleSource={singleSource}/>
      {reading ? <InterpretationList label="关键概念" values={reading.concepts.map(conceptLine)}/>
        : newDevelopment && <InterpretationBlock label="本次新变化" value={newDevelopment}/>}
    </div>
    {whyItMatters && <InterpretationBlock label="为什么重要" value={whyItMatters} accent/>}
    <InterpretationList label={detailsLabel ?? (reading ? '延伸解读' : '影响链条')}
      values={reading ? reading.sections.map(({ question, answer }) => {
        const title = interpretationText(question);
        const body = interpretationText(answer);
        return (title || body) && <Fragment>
          {title && <h5>{title}</h5>}
          {body && <p>{body}</p>}
        </Fragment>;
      }) : impactChain} ordered/>
  </div>;
}

function InterpretationBlock({ label, value, accent, fact, singleSource }: {
  label: string; value: string; accent?: boolean; fact?: boolean; singleSource?: boolean;
}) {
  const className = accent ? 'radar-dossier-impact' : fact ? 'radar-dossier-fact' : undefined;
  return <article className={className}>
    <header className="radar-interpretation-heading">
      <h4>{accent ? <><span lang="en">WHY IT MATTERS</span> · </> : null}{label}</h4>
      {singleSource ? <span className="radar-single-source">单一来源</span> : null}
    </header>
    <p>{interpretationText(value)}</p>
  </article>;
}
function InterpretationList({ label, values, ordered = false }: { label: string; values: ReactNode[]; ordered?: boolean }) {
  const List = ordered ? 'ol' : 'ul';
  const content = values.map((value) => typeof value === 'string' ? interpretationText(value) : value).filter(Boolean);
  if (!content.length) {
    return null;
  }
  return <article className={ordered ? 'radar-dossier-chain' : undefined}>
    <header className="radar-interpretation-heading">
      <h4>{label}</h4><small>{content.length}</small>
    </header>
    <List>{content.map((value, index) => <li key={index}>{value}</li>)}</List>
  </article>;
}
