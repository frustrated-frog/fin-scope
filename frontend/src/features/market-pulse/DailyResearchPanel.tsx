import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import { SectorResearchPanel } from './SectorResearchPanel';
import { ThemeResearchPanel } from './ThemeResearchPanel';
import { CohortResearchPanel } from './CohortResearchPanel';
import type { DailyResearch } from './marketResearchTypes';
import type { SectorRotation, StockDiscoveryMarketContext } from './marketPulseTypes';
import './DailyResearchPanel.css';

type Props = {
  businessDate: string;
  sectors: SectorRotation[];
  refreshKey?: string;
  onOpenStockDiscovery?: (context: StockDiscoveryMarketContext) => void;
  onOpenStock?: (code: string) => void;
};
export function DailyResearchPanel({ businessDate, sectors, refreshKey, onOpenStockDiscovery, onOpenStock }: Props) {
  const [research, setResearch] = useState<DailyResearch>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setResearch(undefined);
    setError('');
    api<DailyResearch>(`/api/market-pulse/research/${businessDate}`).then(value => {
      if (!active) {
        return;
      }
      if (value.businessDate !== businessDate || !Array.isArray(value.stocks) || !Array.isArray(value.groups)) {
        throw new Error('日频研究数据日期或格式不匹配，请重试');
      }
      setResearch(value);
    }).catch(cause => {
      if (active) {
        setError(cause instanceof Error ? cause.message : '日频样本加载失败');
      }
    }).finally(() => {
      if (active) {
        setLoading(false);
      }
    });
    return () => { active = false; };
  }, [businessDate, refreshKey, attempt]);
  const current = research?.businessDate === businessDate ? research : undefined;
  return <div className="mp-daily-research">
    <SectorResearchPanel key={businessDate} sectors={sectors} businessDate={businessDate} onOpenStockDiscovery={onOpenStockDiscovery} />
    <div className="mp-research-status" aria-live="polite"><span>{loading ? '正在读取本地日频样本…' : error || `样本日期 ${businessDate} · ${current?.sampleCount ?? 0} 只本地股票`}</span>
      <button type="button" disabled={loading} onClick={() => setAttempt(attempt + 1)}>重试样本加载</button>
    </div>
    <ThemeResearchPanel businessDate={businessDate} stocks={current?.stocks ?? []} onOpenStock={onOpenStock} />
    {current && <CohortResearchPanel research={current} onOpenStock={onOpenStock} />}
    {current?.warnings?.length ? <details className="mp-research-section mp-research-note"><summary>本地样本口径与数据说明</summary><ul>{current.warnings.map((warning, index) => <li key={index}>{warning}</li>)}</ul></details> : null}
  </div>;
}
