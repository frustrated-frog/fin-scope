// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { expect, test } from 'vitest';

test('keeps dense price history inside the detail dialog at every viewport width', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');
  const dialogRule = styles.match(/\.expectation-dialog\s*{([^}]*)}/s)?.[1] ?? '';
  const historyRule = styles.match(/\.detail-history\s*{([^}]*)}/s)?.[1] ?? '';
  const barRule = styles.match(/\.detail-history i\s*{([^}]*)}/s)?.[1] ?? '';

  expect(dialogRule).toContain('box-sizing:border-box');
  expect(dialogRule).toContain('grid-template-columns:minmax(0,1fr)');
  expect(dialogRule).toContain('max-height:calc(100dvh - 40px)');
  expect(dialogRule).toContain('overflow-y:auto');
  expect(styles).toMatch(/\.expectation-dialog > \*,\.expectation-dialog header > div,\.detail-history\s*{[^}]*min-width:0/s);
  expect(styles).toMatch(/\.expectation-dialog header button\s*{[^}]*flex:0 0 28px/s);
  expect(historyRule).toContain('min-width:0');
  expect(barRule).toContain('flex:1 1 0');
  expect(barRule).toContain('min-width:0');
});
