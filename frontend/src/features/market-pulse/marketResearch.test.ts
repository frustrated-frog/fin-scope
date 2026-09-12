import { expect, test } from 'vitest';
import { filterSectors, parseThemeMembers, summarizeTheme, buildResearchContext } from './marketResearch';
import type { ResearchTheme } from './marketResearchTypes';

const sector = { sectorCode: '1', sectorName: '半导体', rotationScore: 70, return1d: 1, return5d: 4, return20d: -2, excessReturn5d: 2, breadthRatio: .7 };
test('filters actual metrics and excludes missing required values without treating them as zero', () => {
  expect(filterSectors([sector, { ...sector, sectorCode: '2', breadthRatio: undefined }], { kind: 'REPAIR', minBreadth: 60, maxReturn5d: 10, search: '' })).toEqual([sector]);
  expect(filterSectors([{ ...sector, excessReturn5d: undefined }], { kind: 'EMERGING', search: '' })).toEqual([]);
  expect(filterSectors([{ ...sector, return1d: -1, return20d: 5 }], { kind: 'PULLBACK', search: '' })).toHaveLength(1);
});
test('manual theme members reject duplicates, non stocks and missing evidence', () => {
  expect(parseThemeMembers('600519.SH,贵州茅台,白酒,人工研究笔记')).toHaveLength(1);
  expect(() => parseThemeMembers('600519.SH,贵州茅台,白酒,笔记\n600519.SH,贵州茅台,白酒,笔记')).toThrow('重复');
  expect(() => parseThemeMembers('000300.SH,指数,指数,笔记')).toThrow();
  expect(() => parseThemeMembers('600519.SH,贵州茅台,白酒,')).toThrow();
});
test('theme aggregation enforces effective date, common valid sample and minimum coverage', () => {
  const theme: ResearchTheme = { id: 'test', name: '测试', effectiveDate: '2026-09-10', members: ['600001.SH', '600002.SH', '600003.SH'].map(instrumentCode => ({ instrumentCode, name: instrumentCode, segment: '设备', evidence: '笔记' })) };
  const stocks = theme.members.map(member => ({ instrumentCode: member.instrumentCode, return1d: 1, return5d: 3, return20d: 5, amount: 100, groupCodes: [] }));
  expect(summarizeTheme(theme, stocks, '2026-09-11', 5).returnPct).toBe(3);
  expect(summarizeTheme(theme, stocks, '2026-09-09', 5).returnPct).toBeUndefined();
  expect(summarizeTheme(theme, stocks.slice(0, 2), '2026-09-11', 5).returnPct).toBeUndefined();
  expect(summarizeTheme(theme, stocks, '2026-09-11', 5).amount).toBe(300);
});
test('research handoff carries selected industry and observed date', () => {
  expect(buildResearchContext([sector], '2026-09-11').preferredSectors).toEqual(['半导体']);
  expect(buildResearchContext([sector], '2026-09-11').businessDate).toBe('2026-09-11');
});

test('persisted theme relationships reject incomplete fields instead of creating the text undefined', async () => {
  const { loadThemes, THEME_STORAGE_KEY } = await import('./marketResearch');
  localStorage.setItem(THEME_STORAGE_KEY, JSON.stringify([{ id: 't', name: '主题', effectiveDate: '2026-09-10', members: [{ instrumentCode: '600519.SH' }] }]));
  expect(() => loadThemes()).toThrow();
  localStorage.removeItem(THEME_STORAGE_KEY);
});

test('member parser excludes non-A stock prefixes even with a market suffix', () => {
  expect(() => parseThemeMembers('399001.SZ,指数,指数,笔记')).toThrow();
  expect(() => parseThemeMembers('900001.BJ,非股票,测试,笔记')).toThrow();
});
