export type NewsReading = {
  lead: string;
  concepts: Array<{ term: string; meaning: string }>;
  sections: Array<{ question: string; answer: string }>;
};

export function interpretationText(value: string) {
  return value.replace(/[ \t]*\[(?:signal|evidence):\d+(?:[ \t]*[,，、][ \t]*(?:signal|evidence):\d+)*\]/gi, '').trim();
}

export function conceptLine({ term, meaning }: NewsReading['concepts'][number]) {
  return `${term}：${meaning}`;
}

export function sectionLine({ question, answer }: NewsReading['sections'][number]) {
  return `${question} ${answer}`;
}
