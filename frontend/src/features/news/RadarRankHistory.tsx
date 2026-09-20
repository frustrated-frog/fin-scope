import { radarRankPath } from './radarRankPath';
import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import { dateTime } from '../investment-observation/reactionTypes';

interface RankPoint {
  observedAt: string;
  rankPosition: number;
  hotspotScore: number;
  reportCount: number;
  sourceCount: number;
}
export function RadarRankHistory({ eventId }: { eventId: string | number }) {
  const [points, setPoints] = useState<RankPoint[]>();
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    void api<RankPoint[]>(`/api/research-radar/events/${eventId}/rank-history`)
      .then((values) => {
        if (active) {
          setPoints(Array.isArray(values) ? values : []);
        }
      })
      .catch((cause) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : '排名历史暂不可用');
        }
      });
    return () => {
      active = false;
    };
  }, [eventId]);
  const ordered = [...(points ?? [])].reverse().slice(-24);
  return (
    <section className="radar-rank-history">
      <h3>全局排名历史</h3>
      <p>每半小时记录区间内最近一次排名；断档表示没有该时段记录，不代表排名为零。</p>
      {error ? (
        <p role="alert">{error}</p>
      ) : !points ? (
        <p>正在读取排名记录…</p>
      ) : !points.length ? (
        <p>尚无历史记录，下一次雷达生产后开始积累。</p>
      ) : (
        <>
          {ordered.length > 1 ? (
            <svg viewBox="0 0 500 92" role="img" aria-label="最近24个排名记录，位置越高表示排名越靠前">
              <path d={radarRankPath(ordered)} fill="none" stroke="currentColor" strokeWidth="2" />
            </svg>
          ) : (
            <p>首次记录，尚无可比较的历史基线。</p>
          )}
          <div className="radar-rank-table">
            <table>
              <thead>
                <tr>
                  <th>半小时区间起点</th>
                  <th>全局排名</th>
                  <th>报道数</th>
                  <th>独立来源</th>
                </tr>
              </thead>
              <tbody>
                {points.slice(0, 12).map((point) => (
                  <tr key={point.observedAt}>
                    <td>{dateTime(point.observedAt)}</td>
                    <td>#{point.rankPosition}</td>
                    <td>{point.reportCount}</td>
                    <td>{point.sourceCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
