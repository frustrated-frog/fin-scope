// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';

import { expect, test } from 'vitest';

test('keeps the market pulse grid inside the workspace width', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');
  const pageRule = styles.match(/\.market-pulse-page\s*{([^}]*)}/s)?.[1] ?? '';
  const warningRule = styles.match(/\.market-pulse-warning\s*{([^}]*)}/s)?.[1] ?? '';
  const historyRule = styles.match(/\.market-pulse-history-row\s*{([^}]*)}/s)?.[1] ?? '';
  const marketPulseStyles = styles.slice(styles.indexOf('/* Market Pulse'));

  expect(pageRule).toContain('grid-template-columns: minmax(0, 1fr)');
  expect(warningRule).toContain('overflow-wrap: anywhere');
  expect(historyRule).toContain('minmax(0, 1fr)');
  expect(styles).toMatch(/@media\s*\(max-width:\s*1100px\)\s*{\s*\.market-intel-hero/s);
  expect(marketPulseStyles).toMatch(/@media\s*\(max-width:\s*1260px\)\s*{\s*\.market-pulse-hero[\s\S]*?\.market-pulse-decision-grid\s*{[^}]*grid-template-columns:\s*1fr/s);
});
