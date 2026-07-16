import type { MarketDataQualityStatus } from '../../shared/types';

const STATUS_LABELS: Record<MarketDataQualityStatus, string> = {
  FRESH_PRIMARY: '数据完整',
  FRESH_FALLBACK: '备用源可用',
  PARTIAL_FRESH: '部分可用',
  STALE_FALLBACK: '历史快照',
  UNAVAILABLE: '数据不可用'
};

const PROVIDER_LABELS: Record<string, string> = {
  EASTMONEY: '东方财富',
  EASTMONEY_CAPITAL_FLOW: '东方财富资金流',
  EASTMONEY_DRAGON_TIGER: '东方财富龙虎榜',
  LAST_GOOD_SNAPSHOT: '本地最近成功快照',
  MARKET_DATA_GATEWAY: '行情数据网关',
  SINA_STOCK: '新浪行情',
  TENCENT_STOCK: '腾讯行情'
};

const WARNING_LABELS: Record<string, string> = {
  QUOTE_UNAVAILABLE: '实时报价暂不可用',
  INTRADAY_MARKET_UNAVAILABLE: '日内行情暂不可用',
  DAILY_MARKET_UNAVAILABLE: '日线行情暂不可用',
  REALTIME_FUND_FLOW_UNAVAILABLE: '实时资金流暂不可用',
  HISTORICAL_FUND_FLOW_UNAVAILABLE: '历史资金流暂不可用',
  TIMELINE_ALIGNMENT_GAP: '部分时间点行情未与资金流对齐'
};

const ERROR_LABELS: Record<string, string> = {
  CONNECTION_ERROR: '连接失败',
  TIMEOUT: '请求超时',
  SCHEMA_DRIFT: '数据格式异常',
  EMPTY_FUND_FLOW: '返回数据为空'
};

export function marketDataStatusLabel(status: MarketDataQualityStatus) {
  return STATUS_LABELS[status] ?? '数据状态未知';
}

export function marketDataProviderLabel(code?: string | null) {
  if (!code) return '等待首次刷新';
  return PROVIDER_LABELS[code] ?? (/^[A-Z0-9_]+$/.test(code) ? '其他行情源' : code);
}

export function marketIntelWarningMessages(warnings: string[]) {
  const values = new Set<string>();
  for (const warning of warnings ?? []) {
    for (const item of warning.split(/[；;]/)) {
      const normalized = item.trim();
      if (!normalized) continue;
      values.add(readableWarning(normalized));
    }
  }
  return Array.from(values);
}

function readableWarning(warning: string) {
  const [code, errorType] = warning.split(':', 2);
  const label = WARNING_LABELS[code];
  if (!label) return /^[A-Z0-9_]+(?::[A-Z0-9_]+)?$/.test(warning)
    ? '部分辅助行情暂不可用'
    : warning;
  const reason = errorType ? ERROR_LABELS[errorType] : undefined;
  return reason ? `${label}（${reason}）` : label;
}
