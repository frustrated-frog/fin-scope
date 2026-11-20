export type NewsReading = {
  lead: string;
  concepts: Array<{ term: string; meaning: string }>;
  sections: Array<{ question: string; answer: string }>;
};

export function interpretationText(value: string) {
  return value.replace(/[ \t]*\[(?:signal|evidence):\d+(?:[ \t]*[,，、][ \t]*(?:signal|evidence):\d+)*\]/gi, '').trim();
}

export function NewsReadingContent({ reading, radarSources }: { reading: NewsReading; radarSources?: number }) {
  const radar = radarSources !== undefined;
  return <>
    <article className={radar ? 'radar-dossier-fact' : undefined}>
      <header className={radar ? 'radar-interpretation-heading' : undefined}>
        <h4>一句话看懂</h4>
        {radarSources === 1 && <span className="radar-single-source">单一来源</span>}
      </header>
      <p>{interpretationText(reading.lead)}</p>
    </article>
    {reading.concepts.length > 0 && <article>
      <header className={radar ? 'radar-interpretation-heading' : undefined}><h4>关键概念</h4></header>
      {reading.concepts.map(({ term, meaning }, index) => <details key={term} open={index === 0}>
        <summary>{interpretationText(term)}</summary><p>{interpretationText(meaning)}</p>
      </details>)}
    </article>}
    {reading.sections.map(({ question, answer }, index) => <article key={question} className={radar && index === 0 ? 'radar-dossier-impact' : undefined}>
      <details open={index === 0}>
        <summary><strong>{interpretationText(question)}</strong></summary>
        <p>{interpretationText(answer)}</p>
      </details>
    </article>)}
  </>;
}
