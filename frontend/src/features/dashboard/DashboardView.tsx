import { useEffect, useState } from 'react';

import { Table } from '../../shared/components/Table';
import { Article, Dashboard } from '../../shared/types';

function useCountUp(end: number, duration: number = 800, delay: number = 0) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (end === 0) {
      setCount(0);
      return;
    }
    const timer = setTimeout(() => {
      const startTime = Date.now();
      const animate = () => {
        const elapsed = Date.now() - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        setCount(Math.floor(eased * end));
        if (progress < 1) {
          requestAnimationFrame(animate);
        }
      };
      requestAnimationFrame(animate);
    }, delay);
    return () => clearTimeout(timer);
  }, [end, duration, delay]);

  return count;
}

export function DashboardView({ dashboard, articles }: { dashboard: Dashboard | null; articles: Article[] }) {
  const sourceCount = useCountUp(dashboard?.sourceCount ?? 0, 800, 0);
  const articleCount = useCountUp(dashboard?.articleCount ?? 0, 800, 100);
  const briefCount = useCountUp(dashboard?.briefCount ?? 0, 800, 200);
  const novelCount = useCountUp(articles.filter((article) => article.noveltyType === 'NEW').length, 800, 300);

  if (!dashboard) {
    return (
      <section className="content-grid">
        <div className="dashboard-grid">
          {[1, 2, 3, 4].map((index) => (
            <div key={index} className="card">
              <div className="card-content">
                <div className="skeleton skeleton-text" style={{ width: '80px' }}></div>
                <div className="skeleton skeleton-heading" style={{ width: '60px' }}></div>
                <div className="skeleton skeleton-text" style={{ width: '100px' }}></div>
              </div>
            </div>
          ))}
        </div>
      </section>
    );
  }

  const metrics = [
    { label: '信息源', value: sourceCount, caption: 'Sources', note: '稳定采集通道' },
    { label: '文章池', value: articleCount, caption: 'Article', note: '可复盘素材' },
    { label: '简报', value: briefCount, caption: 'Briefs', note: '每日沉淀' },
    { label: '新内容', value: novelCount, caption: 'Novelty', note: '今日高新意' }
  ];
  const latestRun = dashboard.latestFetchRuns[0];
  const totalNew = dashboard.latestFetchRuns.reduce((sum, run) => sum + run.successCount, 0);
  const totalDuplicate = dashboard.latestFetchRuns.reduce((sum, run) => sum + run.duplicateCount, 0);
  const successfulRuns = dashboard.latestFetchRuns.filter((run) => run.status === 'COMPLETED').length;

  return (
    <section className="content-grid">
      <section className="dashboard-hero">
        <div className="dashboard-hero-copy">
          <span className="dashboard-hero-kicker">Research Command</span>
          <h3>把分散信息收束成可复用的投研记忆</h3>
          <p>
            信源采集、候选筛选、AI 解读、简报和主题库在这里形成闭环。当前工作台优先展示最近抓取质量和新增内容动向。
          </p>
        </div>
        <div className="dashboard-hero-panel" aria-label="最近抓取状态">
          <span>Latest run</span>
          <strong>{latestRun?.sourceName ?? '等待首个抓取'}</strong>
          <p>{latestRun ? `${latestRun.status} · 新增 ${latestRun.successCount} · 重复 ${latestRun.duplicateCount}` : '配置一个信源后开始建立本地情报流。'}</p>
        </div>
      </section>

      <div className="dashboard-grid">
        {metrics.map((metric, index) => (
          <div key={metric.label} className="dashboard-card" style={{ animationDelay: `${index * 70}ms` }}>
            <span className="dashboard-card-label">{metric.caption}</span>
            <strong className="dashboard-card-value">{metric.value}</strong>
            <small className="dashboard-card-caption">{metric.label}</small>
            <em>{metric.note}</em>
          </div>
        ))}
      </div>

      <div className="dashboard-run-strip">
        <div>
          <span>Recent new</span>
          <strong>{totalNew}</strong>
        </div>
        <div>
          <span>Duplicates</span>
          <strong>{totalDuplicate}</strong>
        </div>
        <div>
          <span>Completed runs</span>
          <strong>{successfulRuns}</strong>
        </div>
      </div>

      <div className="panel">
        <div className="panel-heading">
          <h3>最近抓取</h3>
          <span className="badge">Fetch Runs</span>
        </div>
        <Table
          headers={['来源', '状态', '新增', '重复']}
          rows={dashboard.latestFetchRuns.map((run) => [
            run.sourceName,
            run.status,
            String(run.successCount),
            String(run.duplicateCount)
          ])}
          empty="还没有抓取记录"
        />
      </div>
    </section>
  );
}
