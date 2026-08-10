// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { expect, test } from 'vitest';

test('turns the fund ledger into mobile cards without horizontal scrolling', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(/\.watchlist-fund-table\s*{[^}]*width:\s*100%;[^}]*border-collapse:\s*collapse;/s);
  expect(styles).toMatch(/\.watchlist-fund-number[^}]*font-variant-numeric:\s*tabular-nums;/s);
  expect(styles).toMatch(
    /@media\s*\(max-width:\s*700px\)[\s\S]*?\.watchlist-fund-table\s+thead\s*{[^}]*display:\s*none;/s
  );
  expect(styles).toMatch(
    /@media\s*\(max-width:\s*700px\)[\s\S]*?\.watchlist-fund-table\s+tbody\s+tr\s*{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/s
  );
  expect(styles).toMatch(/\.watchlist-fund-table-wrap\s*{[^}]*overflow:\s*visible;/s);
});
