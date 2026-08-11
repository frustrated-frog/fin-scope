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
