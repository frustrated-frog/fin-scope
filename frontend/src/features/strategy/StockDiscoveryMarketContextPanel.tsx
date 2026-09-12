import type { StockDiscoveryMarketContext } from '../../shared/types/marketContext';

export function StockDiscoveryMarketContextPanel({ context }: { context: StockDiscoveryMarketContext }) {
  const filtered = context.source === 'SECTOR_FILTER';
  const source = filtered ? '来自行业机会筛选器' : '来自市场转折雷达';
  return <section className="discovery-market-context" data-risk={filtered ? undefined : context.riskPosture} aria-label={`${source}的研究上下文`}>
    <header><span>{source}</span><strong>{context.transitionLabel}</strong><p>{context.summary}</p><time>{context.businessDate ?? '当前交易日'}</time></header>
    <dl>
      {!filtered && <div><dt>风险姿态</dt><dd>{{ OFFENSIVE: '进攻观察', BALANCED: '均衡试错', DEFENSIVE: '防守优先' }[context.riskPosture]}</dd></div>}
      <div><dt>{filtered ? '所选行业' : '优先研究'}</dt><dd>{context.preferredSectors.join(' · ') || '等待行业确认'}</dd></div>
      {!filtered && <><div><dt>谨慎方向</dt><dd>{context.avoidSectors.join(' · ') || '当前无强制回避'}</dd></div>
        <div><dt>参与纪律</dt><dd>{{ CONFIRMATION_ALLOWED: '确认后参与', PULLBACK_ONLY: '只等回撤确认', NO_CHASING: '不追高' }[context.chasePolicy]}</dd></div></>}
    </dl>
    {filtered && <p>下方如有已生成的股票发现报告，并未按本次筛选重新计算；请核对报告日期及行业覆盖。</p>}
  </section>;
}
