import { useCallback, useEffect, useState } from 'react';
import { useThemeMemberData } from './useThemeMemberData';
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
  const [memberCodes, setMemberCodes] = useState<string[]>([]);
  const acceptMembers = useCallback((codes: string[]) => setMemberCodes(current => current.join(',') === codes.join(',') ? current : codes), []);
  const members = useThemeMemberData(memberCodes, businessDate, () => setAttempt(value => value + 1));
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
    {!!members.total && <section className="mp-research-section" aria-label="主题成员行情补齐">
      <div className="mp-research-compare"><span aria-live="polite">{members.running ? '正在检查并补齐主题行情' : '主题行情检查完成'} · {Object.keys(members.results).length}/{members.total}只 · 完整 {Object.values(members.results).filter(result => result.status === 'READY').length}只</span>
        <button type="button" disabled={members.running} onClick={members.retry}>重新检查主题行情</button></div>
      <p className="mp-research-note">只补齐已保存的主题成员，最多同时处理2只。供应商失败会短暂冷却；展开产业环节可查看逐股缺失原因。完成后自动更新日频结果。</p>
    </section>}
    {current?.calculatedAt && <p className="mp-research-note">{current.cacheHit ? '使用日频缓存' : '日频结果已计算'} · 生成于 {new Date(current.calculatedAt).toLocaleString('zh-CN')} · 日K更新后自动重算</p>}
    <ThemeResearchPanel businessDate={businessDate} stocks={current?.stocks ?? []} onOpenStock={onOpenStock}
      onMembersChange={acceptMembers} memberResults={members.results} activeMembers={members.activeCodes} />
    {current && <CohortResearchPanel research={current} onOpenStock={onOpenStock} />}
    {current?.warnings?.length ? <details className="mp-research-section mp-research-note"><summary>本地样本口径与数据说明</summary><ul>{current.warnings.map((warning, index) => <li key={index}>{warning}</li>)}</ul></details> : null}
  </div>;
}
