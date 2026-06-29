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
    { label: '信息源', value: sourceCount, caption: 'Sources' },
    { label: '文章池', value: articleCount, caption: 'Article' },
    { label: '简报', value: briefCount, caption: 'Briefs' },
    { label: '新内容', value: novelCount, caption: 'Novelty' }
  ];

  return (
    <section className="content-grid">
      <div className="dashboard-grid">
        {metrics.map((metric) => (
          <div key={metric.label} className="dashboard-card">
            <span className="dashboard-card-label">{metric.caption}</span>
            <strong className="dashboard-card-value">{metric.value}</strong>
            <small className="dashboard-card-caption">{metric.label}</small>
          </div>
        ))}
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
