// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';

import { describe, expect, test } from 'vitest';

const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
const css = readFileSync(`${cwd}/src/styles.css`, 'utf8');
const marketPulseMarker = '/* Market Pulse — an A-share market-internals and sector-rotation field. */';
const marketPulseStart = css.indexOf(marketPulseMarker);
const marketPulseCss = css.slice(marketPulseStart);
const otherPagesCss = css.slice(0, marketPulseStart);

describe('Market Pulse readability styles', () => {
  test('keeps the readability upgrade scoped to the market pulse page', () => {
    expect(marketPulseStart).toBeGreaterThan(-1);
    expect(marketPulseCss).toContain('--mp-text-caption: 11px;');
    expect(marketPulseCss).toContain('--mp-text-body: 14px;');
    expect(otherPagesCss).toContain('.investment-observation-kicker { margin: 0 0 12px; color: var(--observation-green); font: 750 9px');
  });

  test('does not render market pulse labels below eleven pixels', () => {
    const undersized = [...marketPulseCss.matchAll(/font(?:-size)?:\s*[^;{}]*?\b(\d+(?:\.\d+)?)px/g)]
      .filter((match) => Number(match[1]) < 11)
      .map((match) => match[0]);

    expect(undersized).toEqual([]);
  });

  test('uses readable controls and page-local accessibility fallbacks', () => {
    expect(marketPulseCss).toMatch(/\.market-pulse-controls select,\s*\.market-pulse-controls button\s*\{[^}]*min-height:\s*44px/s);
    expect(marketPulseCss).toContain('.market-pulse-page button:active');
    expect(marketPulseCss).toContain('@media (prefers-reduced-transparency: reduce)');
    expect(marketPulseCss).toContain('@media (prefers-contrast: more)');
  });
});
