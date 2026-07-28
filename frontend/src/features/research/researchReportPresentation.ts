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
