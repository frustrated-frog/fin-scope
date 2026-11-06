export type ReportSection = { title: string; id: string };

export function slugReportHeading(value: string) {
  return value.trim().toLowerCase().replace(/[^\p{L}\p{N}_-]+/gu, '-').replace(/^-+|-+$/g, '');
}

export function extractReportSections(markdown: string): ReportSection[] {
  return markdown.split('\n').flatMap((line) => {
    if (!line.startsWith('## ') || line.startsWith('### ')) return [];
    const title = line.slice(3).trim();
    return title ? [{ title, id: `section-${slugReportHeading(title)}` }] : [];
  });
}

export function evidenceHeadingId(value: string) {
  const match = value.trim().match(/^(E\d+)\b/i);
  return match ? `evidence-${match[1].toLowerCase()}` : undefined;
}

export function sanitizeLegacyEvidenceAnchors(markdown: string) {
  return markdown.replace(/^[ \t]*<a id=["']evidence-e\d+["']><\/a>[ \t]*$/gim, '');
}


/** Presentation-only cleanup; persisted reports and evidence stay unchanged. */
export function prepareReportMarkdown(markdown: string) {
  return sanitizeLegacyEvidenceAnchors(markdown)
    .replace(/\[\]\(\s*(?:\/|#)?\s*\)/g, '')
    .replace(/移动版\s*网页版\)?[\s\S]{0,1800}?您当前的位置[：:]\s*[^>\n]{1,40}>\s*[^>\n]{1,40}>\s*正文\s*/g, '')
    .trim();
}
