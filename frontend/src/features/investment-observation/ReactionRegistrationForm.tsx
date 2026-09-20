import { useState } from 'react';
import type { EventType, ReactionSample } from './reactionTypes';
import { eventLabels, sourceHref } from './reactionTypes';

export function ReactionRegistrationForm({ sample, busy, onConfirm }: {
  sample: ReactionSample;
  busy: boolean;
  onConfirm: (body: { instrumentCode: string; instrumentName: string; eventType: EventType; publishedAt: string; relationNote: string; revision: number }) => Promise<void>;
}) {
  const [code, setCode] = useState(sample.instrumentCode);
  const [name, setName] = useState(sample.instrumentName || '');
  const [type, setType] = useState<EventType>(sample.eventType || 'EARNINGS');
  const [publishedAt, setPublishedAt] = useState(sample.publishedAt?.slice(0, 16) || '');
  const [relation, setRelation] = useState(sample.relationNote || '');
  const href = sourceHref(sample.sourceUrl);
  return (
    <form className="reaction-registration" onSubmit={event => {
      event.preventDefault();
      void onConfirm({ instrumentCode: code.trim().toUpperCase(), instrumentName: name.trim(), eventType: type,
        publishedAt: `${publishedAt}:00`, relationNote: relation.trim(), revision: sample.revision });
    }}>
      <p>核对公告后开始观察。确认后保留这份事件快照，后续更新只补充市场表现。</p>
      {href && <a href={href} target="_blank" rel="noreferrer">打开来源核对 ↗</a>}
      <div className="reaction-form-grid">
        <label>股票代码<input required pattern="(?:6[0-9]{5}\.SH|[03][0-9]{5}\.SZ|[489][0-9]{5}\.BJ)" placeholder="600519.SH" value={code} onChange={e => setCode(e.target.value.toUpperCase())} /></label>
        <label>股票名称<input required maxLength={80} value={name} onChange={e => setName(e.target.value)} /></label>
        <label>事件类型<select value={type} onChange={e => setType(e.target.value as EventType)}>{Object.entries(eventLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>公开时间（北京时间）<input type="datetime-local" required value={publishedAt} onChange={e => setPublishedAt(e.target.value)} /></label>
      </div>
      <label>与股票的关联依据<textarea required maxLength={1000} rows={3} value={relation} onChange={e => setRelation(e.target.value)} placeholder="例如：公告披露的合同签署方就是该上市公司。" /></label>
      <small>业绩披露与正式合同以原始公告为准。只有日期、没有可核对时刻的材料，请暂时保留在待确认。</small>
      <button className="reaction-primary" type="submit" disabled={busy}>{busy ? '正在保存…' : '确认并开始观察'}</button>
    </form>
  );
}
