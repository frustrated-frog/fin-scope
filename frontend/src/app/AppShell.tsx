import { ReactNode } from 'react';

import { ToastHost } from '../shared/components/ToastHost';
import { ToastItem, View } from '../shared/types';

const navGroups: Array<{
  label: string;
  items: Array<{ id: View; label: string; hint: string; code: string }>;
}> = [
  {
    label: '研究流',
    items: [
      { id: 'dashboard', label: 'Dashboard', hint: '今日总览', code: 'OV' },
      { id: 'sources', label: 'Sources', hint: '信源配置', code: 'SO' },
      { id: 'intake', label: 'Intake', hint: '候选筛选', code: 'IN' },
      { id: 'article', label: 'Article', hint: '文章研究', code: 'AR' },
      { id: 'briefs', label: 'Briefs', hint: '每日简报', code: 'BR' },
      { id: 'research', label: 'Research', hint: '研究运行', code: 'RE' },
      { id: 'news', label: 'News Wire', hint: '实时资讯', code: 'NW' }
    ]
  },
  {
    label: '知识与判断',
    items: [
      { id: 'knowledge', label: 'Facts & Knowledge', hint: '事实与知识', code: 'KN' },
      { id: 'contentStudio', label: 'Studio', hint: '内容输出', code: 'ST' },
      { id: 'majorEvents', label: 'Timeline', hint: '大事记', code: 'TL' }
    ]
  },
  {
    label: '决策',
    items: [
      { id: 'watchlist', label: 'Watchlist', hint: '自选观察', code: 'WA' },
      { id: 'marketIntel', label: 'Market Intel', hint: '资金行为', code: 'MI' },
      { id: 'financials', label: 'Financials', hint: '财报分析', code: 'FI' },
      { id: 'strategy', label: 'Strategy', hint: '策略工作台', code: 'SG' }
    ]
  },
  {
    label: '系统',
    items: [
      { id: 'agents', label: 'Agent Runs', hint: '运行追踪', code: 'AG' },
      { id: 'settings', label: 'Settings', hint: '本地设置', code: 'SE' }
    ]
  }
];

export function AppShell({
  view,
  currentTitle,
  theme,
  articlesCount,
  topicsCount,
  message,
  toasts,
  onChangeView,
  onToggleTheme,
  onRefresh,
  children
}: {
  view: View;
  currentTitle: string;
  theme: 'light' | 'dark';
  articlesCount: number;
  topicsCount: number;
  message: string;
  toasts: ToastItem[];
  onChangeView: (view: View) => void;
  onToggleTheme: () => void;
  onRefresh: () => void;
  children: ReactNode;
}) {
  return (
    <div className="app-shell" data-theme={theme}>
      <div className="shell-ambient" aria-hidden="true" />
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            <img src="/favicon.svg" alt="" />
          </span>
          <div>
            <h1>FinScope</h1>
            <p>Research Intelligence</p>
          </div>
        </div>
        <div className="sidebar-signal">
          <span className="signal-label">Pipeline</span>
          <strong>Sources / Intake / Article / Brief / Knowledge</strong>
          <div className="signal-flow" aria-hidden="true">
            <span />
            <span />
            <span />
            <span />
            <span />
          </div>
        </div>
        <nav aria-label="Workspace">
          {navGroups.map((group) => (
            <section className="nav-group" key={group.label} aria-label={group.label}>
              <p className="nav-group-label">{group.label}</p>
              {group.items.map((item) => (
                <button
                  key={item.id}
                  className={view === item.id ? 'nav-item active' : 'nav-item'}
                  aria-label={item.label}
                  onClick={() => onChangeView(item.id)}
                >
                  <span className="nav-code">{item.code}</span>
                  <span className="nav-copy">
                    <span>{item.label}</span>
                    <small>{item.hint}</small>
                  </span>
                </button>
              ))}
            </section>
          ))}
        </nav>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Local-first investment intelligence</p>
            <h2>{currentTitle}</h2>
          </div>
          <div className="topbar-actions">
            <div className="topbar-readouts" role="group" aria-label="数据概览">
              <div className="topbar-readout" aria-label={`文章数量 ${articlesCount}`}>
                <span>Articles</span>
                <strong>{articlesCount}</strong>
              </div>
              <div className="topbar-readout" aria-label={view === 'knowledge' ? `认识档案 ${topicsCount}` : `主题数量 ${topicsCount}`}>
                <span>{view === 'knowledge' ? 'Files' : 'Topics'}</span>
                <strong>{topicsCount}</strong>
              </div>
            </div>
            <div className="topbar-controls" role="group" aria-label="界面控制">
              <button
                className="ghost-button topbar-control theme-toggle"
                type="button"
                aria-label={theme === 'dark' ? '切换为浅色模式' : '切换为深色模式'}
                onClick={onToggleTheme}
              >
                {theme === 'dark' ? (
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <circle cx="12" cy="12" r="3.5" />
                    <path d="M12 2v2.2M12 19.8V22M4.93 4.93l1.56 1.56M17.51 17.51l1.56 1.56M2 12h2.2M19.8 12H22M4.93 19.07l1.56-1.56M17.51 6.49l1.56-1.56" />
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M20.4 15.1A8.6 8.6 0 0 1 8.9 3.6 8.6 8.6 0 1 0 20.4 15.1Z" />
                  </svg>
                )}
              </button>
              <button className="ghost-button topbar-control topbar-refresh" type="button" onClick={onRefresh}>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M20 6v5h-5M4 18v-5h5" />
                  <path d="M6.1 8.2A7 7 0 0 1 18.8 7M17.9 15.8A7 7 0 0 1 5.2 17" />
                </svg>
                <span>刷新</span>
              </button>
            </div>
            <div className="topbar-status" role="status" aria-label="系统状态">
              <span className="topbar-status-signal" aria-hidden="true" />
              <span className="topbar-status-copy">
                <small>系统状态</small>
                <strong title={message}>{message}</strong>
              </span>
            </div>
          </div>
        </header>

        {children}
      </main>

      <ToastHost toasts={toasts} />
    </div>
  );
}
