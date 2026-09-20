import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { ReactionChange, ReactionSample } from './reactionTypes';
import { dateTime } from './reactionTypes';

const changeLabels: Record<string, string> = {
  FIRST_REACTION: '首次反应', NEW_SESSION: '新交易日', WINDOW_COMPLETED: '五日窗口结束',
  PATH_CHANGED: '路径变化', DATA_CORRECTION: '数据修订'
};
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());

export function ReactionActivity({ revision, onOpen }: { revision: number; onOpen: (id: number) => void }) {
  const [date, setDate] = useState(today);
  const [changes, setChanges] = useState<ReactionChange[]>([]);
  const [followed, setFollowed] = useState<ReactionSample[]>([]);
  const [moreChanges, setMoreChanges] = useState(false);
  const [moreFollowed, setMoreFollowed] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    let pending = false;
    setChanges([]);
    setFollowed([]);
    setLoading(true);
    async function load() {
      if (pending) {
        return;
      }
      pending = true;
      try {
        const [next, tracked] = await Promise.all([
          api<ReactionChange[]>(`/api/investment-reactions/changes?date=${date}`),
          api<ReactionSample[]>('/api/investment-reactions/followed')
        ]);
        if (!Array.isArray(next) || !Array.isArray(tracked)) {
          throw new Error('变化记录响应格式无效，请稍后重试');
        }
        if (active) {
          setChanges(current => [...next, ...current.filter(item => next.length === 100 && item.id < next[next.length - 1].id)]);
          setFollowed(current => [...tracked, ...current.filter(item => tracked.length === 100 && item.id < tracked[tracked.length - 1].id)]);
          setMoreChanges(next.length === 100);
          setMoreFollowed(tracked.length === 100);
          setError('');
        }
      } catch (reason) {
        if (active) {
          setError(reason instanceof Error ? reason.message : '变化记录读取失败');
        }
      } finally {
        pending = false;
        if (active) {
          setLoading(false);
        }
      }
    }
    void load();
    const timer = window.setInterval(() => { void load(); }, 15000);
    return () => { active = false; window.clearInterval(timer); };
  }, [date, revision]);

  async function more(kind: 'changes' | 'followed') {
    setBusy(true);
    try {
      if (kind === 'changes') {
        const next = await api<ReactionChange[]>(`/api/investment-reactions/changes?date=${date}&beforeId=${changes[changes.length - 1].id}`);
        setChanges(current => [...current, ...next.filter(item => !current.some(value => value.id === item.id))]);
        setMoreChanges(next.length === 100);
      } else {
        const next = await api<ReactionSample[]>(`/api/investment-reactions/followed?beforeId=${followed[followed.length - 1].id}`);
        setFollowed(current => [...current, ...next.filter(item => !current.some(value => value.id === item.id))]);
        setMoreFollowed(next.length === 100);
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '读取失败');
    } finally {
      setBusy(false);
    }
  }
  return <section className="reaction-activity" aria-label="每日反应与关注">
    <div><header><h4>每日反应变化</h4><label>记录日期<input type="date" value={date} onChange={event => setDate(event.target.value || today())} /></label></header>
      <p className="reaction-note">按系统发现变化的日期记录；同日行情修订单独标记，刷新页面不会重复生成。</p>
      {error && <p className="reaction-warning" role="alert">{error}</p>}
      {loading ? <p>正在读取变化…</p> : changes.length === 0 ? <p className="reaction-empty">这一天尚无新的价格反应。行情到期后会自动记录。</p> : <ol className="reaction-feed">{changes.map(change => <li key={change.id}>
        <small>{changeLabels[change.changeType] || change.changeType} · 行情 {change.tradeDate} · 记录 {dateTime(change.detectedAt)}{change.followed && ' · 已关注'}</small>
        <button onClick={() => onOpen(change.sampleId)}>{change.instrumentName} · {change.title}</button><p>{change.summary}</p>
      </li>)}</ol>}
      {moreChanges && <button disabled={busy} onClick={() => void more('changes')}>更多当日变化</button>}
    </div>
    <aside><h4>我的关注</h4><p className="reaction-note">关注以事件为单位，关联股票一起保留在这里。</p>
      {!loading && followed.length === 0 && <p>在事件详情中点击“关注事件”，之后可持续查看。</p>}
      {followed.map(sample => <button className="reaction-followed" key={sample.id} onClick={() => onOpen(sample.id)}><strong>{sample.instrumentName || '待关联'}</strong><span>{sample.title}</span></button>)}
      {moreFollowed && <button disabled={busy} onClick={() => void more('followed')}>更多关注</button>}
    </aside>
  </section>;
}
