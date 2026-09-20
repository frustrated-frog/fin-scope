import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import type { InvestmentObservationWorkspace } from './investmentObservationTypes';
import { dateTime } from './reactionTypes';

export function LegacyObservations() {
  const [workspace, setWorkspace] = useState<InvestmentObservationWorkspace>();
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    void api<InvestmentObservationWorkspace>('/api/investment-observations').then(result => {
      if (active) {
        setWorkspace(result);
      }
    }).catch(reason => {
      if (active) {
        setError(reason instanceof Error ? reason.message : '历史资料读取失败');
      }
    });
    return () => { active = false; };
  }, []);
  const items = workspace ? [...workspace.focus, ...workspace.tracking, ...workspace.learning, ...workspace.archived] : [];
  return <section className="reaction-legacy"><h4>原观察池历史资料</h4><p>保留原有观察文字与验证点。历史记录没有完整的事件时点与股票关联，不参与反应矩阵和收益比较。</p>
    {error && <p role="alert">{error}</p>}
    {!workspace && !error && <p>正在读取历史资料…</p>}
    {workspace && items.length === 0 && <p>暂无历史观察记录。</p>}
    {items.map(item => <article key={item.id}><h5>{item.title}</h5><p>{item.summary}</p><p>{item.nextValidation}</p><small>{item.stage === 'ARCHIVED' ? '已归档 · ' : ''}{dateTime(item.updatedAt)} · 来源关联待核对</small></article>)}
  </section>;
}
