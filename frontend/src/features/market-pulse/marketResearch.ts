import type { SectorRotation, StockDiscoveryMarketContext } from './marketPulseTypes';
import type { ResearchPeriod, ResearchStock, ResearchTheme, SectorFilter, ThemeMember } from './marketResearchTypes';

export const THEME_STORAGE_KEY = 'finscope.market-pulse.themes.v1';
export const sectorRules = {
  ALL: '查看全部行业；按5日超额收益排序，缺失值排在最后。',
  EMERGING: '初步转强：5日超额收益 > 0，且今日涨幅 > 0。',
  PULLBACK: '中期强势回落：20日涨幅 > 0，且今日涨幅 < 0。',
  REPAIR: '弱势修复：20日涨幅 < 0，且5日涨幅 > 0。'
};
export function finite(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}
export function pct(value: number | null | undefined) {
  return finite(value) ? `${value > 0 ? '+' : ''}${value.toFixed(2)}%` : '—';
}
export function ratio(value: number | null | undefined) {
  return finite(value) ? `${(value * 100).toFixed(0)}%` : '—';
}
export function filterSectors(sectors: SectorRotation[], filter: SectorFilter): SectorRotation[] {
  return sectors.filter(item => {
    if (!item.sectorName.includes(filter.search.trim())) {
      return false;
    }
    if (filter.minBreadth != null && (!finite(item.breadthRatio) || item.breadthRatio * 100 < filter.minBreadth)) {
      return false;
    }
    if (filter.maxReturn5d != null && (!finite(item.return5d) || item.return5d > filter.maxReturn5d)) {
      return false;
    }
    switch (filter.kind) {
      case 'EMERGING': return finite(item.excessReturn5d) && item.excessReturn5d > 0 && finite(item.return1d) && item.return1d > 0;
      case 'PULLBACK': return finite(item.return20d) && item.return20d > 0 && finite(item.return1d) && item.return1d < 0;
      case 'REPAIR': return finite(item.return20d) && item.return20d < 0 && finite(item.return5d) && item.return5d > 0;
      default: return true;
    }
  }).sort((a, b) => (finite(b.excessReturn5d) ? b.excessReturn5d : -Infinity)
    - (finite(a.excessReturn5d) ? a.excessReturn5d : -Infinity) || a.sectorCode.localeCompare(b.sectorCode));
}
export function buildResearchContext(sectors: SectorRotation[], businessDate?: string): StockDiscoveryMarketContext {
  return {
    businessDate, source: 'SECTOR_FILTER', transitionCode: 'RANGE_BALANCE', transitionLabel: '行业条件筛选', riskPosture: 'BALANCED',
    preferredSectors: sectors.map(sector => sector.sectorName), avoidSectors: [], chasePolicy: 'NO_CHASING',
    summary: `从 Market Pulse 行业条件筛选进入研究：${sectors.map(sector => sector.sectorName).join('、')}。筛选命中不代表模型验证或收益预测。`
  };
}
export function shanghaiDate(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}
export function defaultThemes(): ResearchTheme[] {
  return ['算力建设', '机器人', '半导体'].map((name, index) => ({ id: `theme-${index}`, name, effectiveDate: shanghaiDate(), members: [] }));
}
export function parseThemeMembers(text: string): ThemeMember[] {
  const lines = text.split('\n').map(line => line.trim()).filter(Boolean);
  if (lines.length > 50) {
    throw new Error('每个主题最多维护50只股票');
  }
  const seen = new Set<string>();
  return lines.map((line, index) => {
    const [rawCode, name, segment, ...evidenceParts] = line.split(/[,，]/).map(value => value.trim());
    const instrumentCode = rawCode.toUpperCase();
    const evidence = evidenceParts.join('，');
    if (!/^(?:(?:600|601|603|605|688)\d{3}\.SH|(?:000|001|002|003|300|301)\d{3}\.SZ|(?:43|83|87|88|92)\d{4}\.BJ)$/.test(instrumentCode)) {
      throw new Error(`第${index + 1}行请输入A股代码，如600519.SH`);
    }
    if (seen.has(instrumentCode)) {
      throw new Error(`股票代码重复：${instrumentCode}`);
    }
    if (!name || !segment || !evidence || name.length > 40 || segment.length > 40 || evidence.length > 300) {
      throw new Error(`第${index + 1}行需填写名称、产业环节及归属依据（名称/环节≤40字，依据≤300字）`);
    }
    seen.add(instrumentCode);
    return { instrumentCode, name, segment, evidence };
  });
}
export function loadThemes(): ResearchTheme[] {
  const raw = localStorage.getItem(THEME_STORAGE_KEY);
  if (!raw) {
    return defaultThemes();
  }
  const parsed: unknown = JSON.parse(raw);
  if (!Array.isArray(parsed) || parsed.length < 1 || parsed.length > 5) {
    throw new Error('本地主题配置格式无效');
  }
  const ids = new Set<string>();
  return parsed.map((item: ResearchTheme) => {
    if (!item || typeof item.id !== 'string' || ids.has(item.id) || typeof item.name !== 'string' || !item.name.trim() || item.name.length > 40
      || !/^\d{4}-\d{2}-\d{2}$/.test(item.effectiveDate) || !Array.isArray(item.members)) {
      throw new Error('本地主题配置格式无效');
    }
    ids.add(item.id);
    if (item.members.some(member => !member || ['instrumentCode', 'name', 'segment', 'evidence'].some(field => typeof member[field as keyof ThemeMember] !== 'string'))) {
      throw new Error('本地主题成员字段不完整');
    }
    const members = parseThemeMembers(item.members.map(member => `${member.instrumentCode},${member.name},${member.segment},${member.evidence}`).join('\n'));
    return { ...item, members };
  });
}
export function stockReturn(stock: ResearchStock | undefined, period: ResearchPeriod) {
  return period === 1 ? stock?.return1d : period === 5 ? stock?.return5d : stock?.return20d;
}
export function summarizeTheme(theme: ResearchTheme, stocks: ResearchStock[], businessDate: string, period: ResearchPeriod) {
  const byCode = new Map(stocks.map(stock => [stock.instrumentCode, stock]));
  const effective = businessDate >= theme.effectiveDate;
  const matched = effective ? theme.members.map(member => byCode.get(member.instrumentCode)).filter((stock): stock is ResearchStock => !!stock) : [];
  const valid = matched.filter(stock => finite(stockReturn(stock, period)));
  const coverage = theme.members.length ? valid.length / theme.members.length : 0;
  const ready = effective && valid.length >= 3 && coverage >= .8;
  const amounts = matched.map(stock => stock.amount).filter(finite);
  return {
    effective, validCount: valid.length, coverage,
    returnPct: ready ? valid.reduce((total, stock) => total + stockReturn(stock, period)!, 0) / valid.length : undefined,
    advanceRatio: ready ? valid.filter(stock => stockReturn(stock, period)! > 0).length / valid.length : undefined,
    amount: ready && amounts.length === valid.length && valid.length === matched.length ? amounts.reduce((sum, amount) => sum + amount, 0) : undefined
  };
}
