import { useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import type { MarketEventConfirmation, MarketPulseWorkspace, MarketRegime, SectorRotation } from './marketPulseTypes';

const stageLabels: Record<string, string> = {
  RISK_ON: '放量进攻',
  HIGH_LEVEL_DIVERGENCE: '高位分歧',
  SELL_OFF: '风险释放',
  POST_SELL_OFF_REPAIR: '急跌后修复',
  RANGE_ROTATION: '震荡轮动',
  INSUFFICIENT_DATA: '数据不足'
};

const stateLabels: Record<string, string> = {
  UPTREND: '上行', RANGE: '震荡', DOWNTREND: '下行',
  EXPANDING: '放量', NORMAL: '常态', SHRINKING: '缩量',
  HIGH: '偏高', NEUTRAL: '中性', LOW: '偏低',
  FAST: '快速', SLOW: '缓慢',
  EMERGING: '萌芽', ACCELERATING: '加速', PERSISTENT: '持续', OVERHEATED: '过热',
  FADING: '退潮', REVERSING: '反转试探', WEAK: '弱势', INSUFFICIENT_DATA: '数据不足',
  CONFIRMED: '同向确认', UNCONFIRMED: '事件未获确认', MARKET_LEADING: '行情先行', QUIET: '低响应'
};

type ViewProps = {
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage: (message: string) => void;
};

function label(value?: string) {
  return value ? stateLabels[value] ?? stageLabels[value] ?? value : '—';
}

function percent(value?: number, sourceIsPercent = false) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  const normalized = sourceIsPercent ? value : value * 100;
  return `${normalized > 0 ? '+' : ''}${normalized.toFixed(2)}%`;
}

function money(value?: number) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  return `${value >= 0 ? '+' : ''}${(value / 100000000).toFixed(1)} 亿`;
}

function dateText(value?: string | number[]) {
  if (Array.isArray(value)) {
    return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')}`;
  }
  return value ?? '—';
}

function MarketTape({ regimes }: { regimes: MarketRegime[] }) {
  return (
    <section className="market-pulse-tape" aria-label="市场节奏轨">
      <header>
        <div><span>05D RHYTHM</span><h4>市场节奏轨</h4></div>
        <small>每个节点是当日收盘后的冻结判断</small>
      </header>
      <div className="market-pulse-tape-track">
        {regimes.length ? regimes.slice(0, 5).reverse().map((item) => {
          const dailyReturn = item.features?.return1d;
          const direction = dailyReturn != null && dailyReturn < 0 ? 'down' : 'up';
          return (
            <article key={dateText(item.businessDate)} className={`market-pulse-tape-node ${direction}`}>
              <time>{dateText(item.businessDate).slice(5)}</time>
              <span aria-hidden="true" />
              <strong>{stageLabels[item.marketStage ?? ''] ?? '待判断'}</strong>
              <small>{percent(dailyReturn)}</small>
            </article>
          );
        }) : <p className="market-pulse-inline-empty">积累每日快照后，这里会显示行情节奏。</p>}
      </div>
    </section>
  );
}

function SectorRow({ item, rank }: { item: SectorRotation; rank: number }) {
  return (
    <article className="market-pulse-sector-row">
      <span className="market-pulse-rank">{String(rank).padStart(2, '0')}</span>
      <div>
        <span className={`market-pulse-stage stage-${item.stage?.toLowerCase()}`}>{label(item.stage)}</span>
        <h4>{item.sectorName}</h4>
        <small>{item.explanations?.[0] ?? '等待更多历史形成解释'}</small>
      </div>
      <dl>
        <div><dt>1 日</dt><dd className={(item.return1d ?? 0) < 0 ? 'negative' : 'positive'}>{percent(item.return1d, true)}</dd></div>
        <div><dt>5 日</dt><dd className={(item.return5d ?? 0) < 0 ? 'negative' : 'positive'}>{percent(item.return5d, true)}</dd></div>
        <div><dt>主力净流入</dt><dd>{money(item.mainNetInflow)}</dd></div>
      </dl>
      <div className="market-pulse-score" aria-label={`轮动评分 ${item.rotationScore}`}>
        <strong>{item.rotationScore}</strong><span>轮动分</span>
        <i><b style={{ width: `${item.rotationScore}%` }} /></i>
      </div>
    </article>
  );
}

function EventRow({ item }: { item: MarketEventConfirmation }) {
  return (
    <article className="market-pulse-event-row">
      <div className="market-pulse-event-axis" aria-hidden="true">
        <i style={{ left: `${Math.min(100, item.eventScore)}%`, bottom: `${Math.min(100, item.marketReactionScore)}%` }} />
      </div>
      <div>
        <span>{item.sectorName ?? '未映射行业'} · {label(item.confirmationState)}</span>
        <h4>{item.title}</h4>
        <p>{item.evidence?.[0] ?? '正在等待更多独立证据'}</p>
      </div>
      <dl>
        <div><dt>事件</dt><dd>{item.eventScore}</dd></div>
        <div><dt>行情</dt><dd>{item.marketReactionScore}</dd></div>
      </dl>
    </article>
  );
}

export function MarketPulseView({ addToast, setMessage }: ViewProps) {
  const [workspace, setWorkspace] = useState<MarketPulseWorkspace | null>(null);
  const [dates, setDates] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = async (date?: string) => {
    setLoading(true);
    try {
      const [nextWorkspace, nextDates] = await Promise.all([
        api<MarketPulseWorkspace>(date ? `/api/market-pulse/${date}` : '/api/market-pulse/latest'),
        api<string[]>('/api/market-pulse/dates')
      ]);
      setWorkspace(nextWorkspace);
      setDates(nextDates);
    } catch (error) {
      addToast(error instanceof Error ? error.message : '市场机会加载失败', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const refresh = async () => {
    setRefreshing(true);
    setMessage('正在刷新市场机会判断');
    try {
      await api('/api/market-pulse/refresh', { method: 'POST' });
      await load();
      addToast('市场机会判断已刷新', 'success');
      setMessage('市场机会判断已刷新');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '市场机会刷新失败', 'error');
      setMessage('市场机会刷新失败');
    } finally {
      setRefreshing(false);
    }
  };

  const regime = workspace?.regime;
  const sectors = useMemo(() => [...(workspace?.sectors ?? [])].sort((a, b) => b.rotationScore - a.rotationScore), [workspace]);
  const candidates = workspace?.candidates ?? [];

  if (loading && !workspace) {
    return <section className="market-pulse-page"><div className="market-pulse-loading" role="status">正在校准市场状态与行业轮动…</div></section>;
  }

  if (!workspace || workspace.qualityStatus === 'UNAVAILABLE') {
    return (
      <section className="market-pulse-page">
        <div className="market-pulse-empty">
          <span aria-hidden="true">⌁</span><h3>还没有第一份市场判断</h3>
          <p>刷新后会冻结指数特征、行业轮动、Radar 事件确认和通过门禁的股票研究候选。</p>
          <button type="button" onClick={refresh} disabled={refreshing}>刷新今日判断</button>
        </div>
      </section>
    );
  }

  return (
    <section className="market-pulse-page">
      <header className="market-pulse-hero">
        <div className="market-pulse-hero-main">
          <p className="market-pulse-kicker">MARKET REGIME · {workspace.businessDate ?? 'LATEST'}</p>
          <h3>{stageLabels[regime?.marketStage ?? ''] ?? '等待判断'}</h3>
          <p>{regime?.explanation ?? '正在等待足够行情数据形成判断。'}</p>
          <div className="market-pulse-regime-strip" aria-label="市场状态维度">
            <span><small>趋势</small><strong>{label(regime?.trendState)}</strong></span>
            <span><small>流动性</small><strong>{label(regime?.liquidityState)}</strong></span>
            <span><small>风险偏好</small><strong>{label(regime?.riskAppetiteState)}</strong></span>
            <span><small>轮动速度</small><strong>{label(regime?.rotationState)}</strong></span>
          </div>
        </div>
        <div className="market-pulse-confidence">
          <span className={`quality-${workspace.qualityStatus.toLowerCase()}`}>{workspace.qualityStatus}</span>
          <strong>{regime?.confidenceScore ?? 0}</strong>
          <small>判断置信度 / 100</small>
          <i><b style={{ width: `${regime?.confidenceScore ?? 0}%` }} /></i>
        </div>
        <div className="market-pulse-controls">
          <label><span>历史截面</span><select value={workspace.businessDate ?? ''} onChange={(event) => void load(event.target.value)}>
            {!dates.length && <option value={workspace.businessDate}>{workspace.businessDate}</option>}
            {dates.map(date => <option key={date} value={date}>{date}</option>)}
          </select></label>
          <button type="button" aria-label="刷新今日判断" onClick={refresh} disabled={refreshing}>{refreshing ? '正在计算…' : '刷新今日判断'}</button>
        </div>
      </header>

      {(workspace.warnings ?? []).length > 0 && <div className="market-pulse-warning"><strong>数据边界</strong>{workspace.warnings?.join('；')}</div>}

      <MarketTape regimes={workspace.recentRegimes ?? []} />

      <div className="market-pulse-decision-grid">
        <section className="market-pulse-sectors">
          <header><div><span>SECTOR ROTATION</span><h3>行业轮动</h3></div><p>只按可回溯行情和资金特征排序；历史不足的行业不会进入机会前列。</p></header>
          <div>{sectors.length ? sectors.slice(0, 10).map((item, index) => <SectorRow item={item} rank={index + 1} key={item.sectorCode} />) : <p className="market-pulse-inline-empty">行业行情暂不可用。</p>}</div>
        </section>

        <section className="market-pulse-events">
          <header><div><span>EVENT × PRICE</span><h3>事件与行情确认</h3></div><p>右上象限代表事件强、市场也响应；它提升研究优先级，但不单独触发候选。</p></header>
          <div>{(workspace.eventConfirmations ?? []).length ? workspace.eventConfirmations?.slice(0, 6).map(item => <EventRow item={item} key={`${item.radarEventId}-${item.title}`} />) : <p className="market-pulse-inline-empty">近 48 小时没有可确认的行业事件。</p>}</div>
        </section>
      </div>

      <section className="market-pulse-candidates">
        <header>
          <div><span>VERIFIED RESEARCH QUEUE</span><h3>股票研究候选</h3></div>
          <p><strong>{candidates.length}</strong> / 5 · 行业轮动与股票模型必须同时通过门禁</p>
        </header>
        {candidates.length ? <div className="market-pulse-candidate-grid">{candidates.map((item, index) => (
          <article key={item.instrumentCode}>
            <header><span>{String(index + 1).padStart(2, '0')}</span><div><small>{item.instrumentCode} · {item.sectorName}</small><h4>{item.name}</h4></div><strong>{item.calibratedProbability == null ? '—' : `${Math.round(item.calibratedProbability * 100)}%`}<small>校准概率</small></strong></header>
            <blockquote>{item.whyNow}</blockquote>
            <div><section><h5>为什么进入研究队列</h5><ul>{(item.reasons ?? []).map(reason => <li key={reason}>{reason}</li>)}</ul></section><section><h5>主要风险</h5><ul>{(item.risks ?? []).map(risk => <li key={risk}>{risk}</li>)}</ul></section></div>
            <footer><strong>失效条件</strong><span>{(item.invalidationConditions ?? []).join('；') || '尚未定义'}</span></footer>
          </article>
        ))}</div> : <div className="market-pulse-candidate-empty"><strong>今天可以没有股票候选</strong><p>没有标的同时通过行业轮动、模型健康度与稳健性门禁。保留现金和继续观察也是研究结论。</p></div>}
      </section>

      <footer className="market-pulse-disclaimer"><span>研究边界</span><p>研究候选不是买入指令。页面用于提高研究优先级，最终决策仍需核验公司基本面、估值、流动性与个人风险承受能力。</p><time>{workspace.generatedAt ? `生成于 ${workspace.generatedAt.replace('T', ' ').slice(0, 16)}` : ''}</time></footer>
    </section>
  );
}
