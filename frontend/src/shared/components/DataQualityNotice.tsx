import { AggregatedMarketDataQuality } from '../marketData/marketDataQuality';

export function DataQualityNotice({ quality }: { quality?: AggregatedMarketDataQuality }) {
  if (!quality || quality.status === 'FRESH_PRIMARY') return null;
  const severe = quality.status === 'STALE_FALLBACK' || quality.status === 'UNAVAILABLE';
  return (
    <div
      className={`market-data-notice is-${quality.status.toLowerCase().replace(/_/g, '-')}`}
      role={severe ? 'alert' : 'status'}
      aria-live={severe ? 'assertive' : 'polite'}
    >
      <strong>{titleOf(quality.status)}</strong>
      <span>{messageOf(quality)}</span>
    </div>
  );
}

function titleOf(status: AggregatedMarketDataQuality['status']) {
  switch (status) {
    case 'FRESH_FALLBACK': return '已自动切换备用数据源';
    case 'PARTIAL_FRESH': return '本次刷新仅部分成功';
    case 'STALE_FALLBACK': return '当前展示的是旧数据';
    case 'UNAVAILABLE': return '当前行情不可用';
    default: return '行情数据正常';
  }
}

function messageOf(quality: AggregatedMarketDataQuality) {
  const details: string[] = [];
  if (quality.warning) details.push(quality.warning);
  if (quality.sourceCode) details.push(`来源：${quality.sourceCode}`);
  if (quality.staleAgeSeconds != null) details.push(`快照时间：${formatAge(quality.staleAgeSeconds)}`);
  if (quality.status === 'STALE_FALLBACK') details.push('请勿视为实时行情。');
  if (quality.status === 'UNAVAILABLE') details.push('系统未找到可用的实时数据或历史快照。');
  return details.join(' ');
}

function formatAge(seconds: number) {
  const safe = Math.max(0, Math.floor(seconds));
  if (safe < 60) return `${safe} 秒前`;
  if (safe < 3600) return `${Math.floor(safe / 60)} 分钟前`;
  if (safe < 86400) return `${Math.floor(safe / 3600)} 小时前`;
  return `${Math.floor(safe / 86400)} 天前`;
}
