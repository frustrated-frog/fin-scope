// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { expect, test } from 'vitest';

test('keeps the create field focus treatment on one container layer', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/features/industry-chain/industry-chain.css`, 'utf8');

  expect(styles).toMatch(/\.ic-workbench \.ic-create input:focus[^}]*outline:\s*0[^}]*box-shadow:\s*none[^}]*transform:\s*none/s);
  const focusWithinRule = styles.match(/\.ic-create:focus-within\s*{([^}]*)}/s)?.[1] ?? '';
  expect(focusWithinRule).toContain('border-color: rgba(90, 216, 210, .48)');
  expect(focusWithinRule).toContain('box-shadow: 0 8px 20px');
  expect(focusWithinRule).not.toContain('0 0 0');
});

test('defines the complete responsive semantic graph visual contract', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/features/industry-chain/industry-chain.css`, 'utf8');

  ['material', 'equipment', 'component', 'product', 'technology', 'application', 'company']
    .forEach((type) => expect(styles).toContain(`.ic-node--${type}`));
  ['structure', 'value', 'bottleneck', 'technology', 'localization', 'company']
    .forEach((layer) => expect(styles).toContain(`.ic-layer-bar--${layer}`));
  expect(styles).toContain('.ic-node-expand');
  expect(styles).toContain('.ic-node-highlights');
  expect(styles).toContain('.ic-node-highlight-label');
  expect(styles).toMatch(/\.ic-node-highlight\.is-critical[^}]*color:/);
  expect(styles).toMatch(/@media\s*\(max-width:\s*760px\)[\s\S]*\.ic-mobile-reader\s+\.ic-node-highlights/);
  expect(styles).toContain('text-wrap: balance');
  expect(styles).toContain('.ic-inspector-expand');
  expect(styles).toContain('.ic-edge-type--substitutes');
  expect(styles).toMatch(/\.ic-layer-guide\s*{/);
  expect(styles).toMatch(/\.ic-layer-legend\s*{/);
  expect(styles).toMatch(/\.ic-node-layer-badge\s*{/);
  expect(styles).toMatch(/@media\s*\(max-width:\s*760px\)[\s\S]*?\.ic-layer-guide[^}]*overflow-x:\s*auto/s);
  expect(styles).toMatch(/grid-template-columns:\s*minmax\(0,\s*1fr\)\s+340px/);
  expect(styles).toMatch(/@media\s*\(max-width:\s*1100px\)[\s\S]*\.ic-inspector\s*{[^}]*position:\s*absolute/);
  expect(styles).toMatch(/@media\s*\(max-width:\s*760px\)[\s\S]*\.ic-inspector\s*{[^}]*position:\s*relative/);
  expect(styles).toContain('@media (prefers-reduced-motion: reduce)');
});

test('centers a fixed graph on wide desktops and keeps the semantic layer copy readable', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/features/industry-chain/industry-chain.css`, 'utf8');

  expect(styles).toMatch(/\.ic-canvas\s*{[^}]*margin-inline:\s*auto/s);
  expect(styles).toMatch(/\.ic-layer-title strong\s*{[^}]*font-size:\s*14px/s);
  expect(styles).toMatch(/\.ic-layer-options button strong\s*{[^}]*font-size:\s*12px/s);
  expect(styles).toMatch(/\.ic-layer-options button small\s*{[^}]*font-size:\s*9px/s);
  expect(styles).toMatch(/\.ic-layer-guide p span\s*{[^}]*font-size:\s*10px/s);
  expect(styles).toMatch(/\.ic-layer-legend > span\s*{[^}]*font:\s*500 9px\/1\.2/s);
});
