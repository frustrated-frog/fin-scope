import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import { studyText, type Study, type StudyPage } from './AutonomousStudies';
import { methodName } from './StudyEvidence';
import './FinancialStatistics.css';

function briefValues(study?: Study) {
  const m = study?.evidence?.metrics;
  if (!m) {
    return [];
  }
  if ('groups' in m) {
    return m.groups.map((g) => ({
      name: g.state === 'UP' ? '上涨日相关性' : '下跌日相关性',
      value: g.mean,
      label: g.mean.toFixed(3),
    }));
  }
  if ('legs' in m) {
    return m.legs.map((l) => ({
      name: methodName[l.method],
      value: l.net,
      label: (l.net * 100).toFixed(1) + '%',
    }));
  }
  return [
    { name: '扣费前盈亏', value: m.beforeFees },
    { name: '扣费后盈亏', value: m.net },
  ].map((row) => ({ ...row, label: '¥ ' + row.value.toFixed(2) }));
}

export function ResearchBrief({
  onStudies,
  onStatistics,
}: {
  onStudies: (id?: number) => void;
  onStatistics: () => void;
}) {
  const [study, setStudy] = useState<Study>();
  const [status, setStatus] = useState('正在读取已发表的研究…');
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date());
  useEffect(() => {
    const ctrl = new AbortController();
    async function read() {
      const page = await api<StudyPage>('/api/quant/research-center', { signal: ctrl.signal });
      const rows = page.items?.filter((s) => s.state === 'PUBLISHED' && s.report) ?? [];
      const item = rows.find((s) => s.trigger_key === 'daily:' + today) ?? rows[0] ?? page.highlights?.[0];
      if (ctrl.signal.aborted) {
        return;
      }
      setStudy(item);
      setStatus('暂无已发表的研究');
      if (item) {
        const detail = await api<Study>('/api/quant/research-center/' + item.id, { signal: ctrl.signal });
        if (!ctrl.signal.aborted) {
          setStudy(detail);
        }
      }
    }
    void read().catch(() => {
      if (!ctrl.signal.aborted) {
        setStatus('研究摘要暂时无法读取');
      }
    });
    return () => ctrl.abort();
  }, [today]);
  const values = briefValues(study);
  const extent = Math.max(...values.map((r) => Math.abs(r.value)), 0.000001);
  return (
    <section className="fs-brief" aria-label="研究入口">
      <div className="fs-brief-copy">
        <small>{study?.trigger_key === 'daily:' + today ? '今日 AI 研究' : '最新 AI 研究'}</small>
        {study ? (
          <button onClick={() => onStudies(study.id)}>
            {studyText(study.plan?.title)} <span>↗</span>
          </button>
        ) : (
          <p>{status}</p>
        )}
        {study?.report && (
          <p className="fs-brief-finding">{studyText(study.report.finding)?.split('。')[0]}。</p>
        )}
        <button className="fs-all" onClick={() => onStudies()}>
          全部研究 →
        </button>
      </div>
      {!!values.length && (
        <div className="fs-mini">
          {values.map((row) => (
            <div key={row.name}>
              <small>{row.name}</small>
              <i>
                <b
                  style={{
                    width: (Math.abs(row.value) / extent) * 49 + '%',
                    left: row.value < 0 ? 'auto' : '50%',
                    right: row.value < 0 ? '50%' : 'auto',
                  }}
                />
              </i>
              <strong>{row.label}</strong>
            </div>
          ))}
        </div>
      )}
      <button className="fs-open" onClick={onStatistics}>
        <strong>金融统计与组合研究 ↗</strong>
        <small>收益分布 · 市场状态 · 资产定价</small>
      </button>
    </section>
  );
}
