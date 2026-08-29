// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';

import { expect, test } from 'vitest';

const stylesheets = [
  'src/styles.css',
  'src/features/industry-chain/industry-chain.css',
  'src/features/strategy/QuantVisualizations.css',
  'src/features/strategy/BacktestAuditPanel.css',
  'src/features/strategy/StockDiscoveryAccuracyPanel.css'
];

function readStylesheet(path: string) {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  return readFileSync(`${cwd}/${path}`, 'utf8');
}

test('keeps every production text declaration at a readable minimum size', () => {
  const undersized: string[] = [];

  for (const stylesheet of stylesheets) {
    const css = readStylesheet(stylesheet);
    for (const match of css.matchAll(/(?:font-size:\s*|font:[^;{}]*?\s)(\d+(?:\.\d+)?)px(?:[\s/;])/g)) {
      if (Number(match[1]) < 11) {
        undersized.push(`${stylesheet}: ${match[0].trim()}`);
      }
    }
  }

  expect(undersized).toEqual([]);
});

test('defines the global type roles and accessibility fallbacks', () => {
  const css = readStylesheet('src/styles.css');

  expect(css).toContain('--font-body:');
  expect(css).toContain('--font-display:');
  expect(css).toContain('--font-data:');
  expect(css).toMatch(/@media\s*\(prefers-reduced-transparency:\s*reduce\)/);
  expect(css).toMatch(/@media\s*\(prefers-contrast:\s*more\)/);
});
