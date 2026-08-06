// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { expect, test } from 'vitest';

test('centers the kline overlay in the workspace without covering the topbar at narrow widths', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(
    /\.watchlist-kline-backdrop\s*{[^}]*top:\s*var\(--watchlist-kline-top,\s*0px\);[^}]*left:\s*var\(--watchlist-kline-left,\s*0px\);/s
  );
  expect(styles).toMatch(
    /@media\s*\(max-width:\s*980px\)[\s\S]*?\.watchlist-kline-backdrop\s*{[^}]*left:\s*0;/s
  );
  expect(styles).not.toMatch(
    /@media\s*\(max-width:\s*980px\)[\s\S]*?\.watchlist-kline-backdrop\s*{[^}]*top:\s*0;/s
  );
  expect(styles).toMatch(
    /@media\s*\(max-width:\s*700px\)[\s\S]*?\.watchlist-kline-backdrop\s*{[^}]*align-items:\s*flex-end;[^}]*padding:\s*12px;/s
  );
});
