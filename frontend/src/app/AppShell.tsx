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
      { id: 'research', label: 'Research', hint: '研究运行', code: 'RE' }
    ]
  },
  {
    label: '知识与判断',
    items: [
      { id: 'knowledge', label: 'Knowledge', hint: '知识工作台', code: 'KN' },
      { id: 'events', label: 'Events', hint: '事件档案', code: 'EV' },
      { id: 'evidence', label: 'Evidence', hint: '证据账本', code: 'ED' },
      { id: 'contentStudio', label: 'Studio', hint: '内容输出', code: 'ST' }
    ]
  },
  {
    label: '决策',
    items: [
      { id: 'watchlist', label: 'Watchlist', hint: '自选观察', code: 'WA' },
      { id: 'marketIntel', label: 'Market Intel', hint: '资金行为', code: 'MI' },
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
          <strong>Sources / Intake / Article / Brief / Topics</strong>
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
            <div className="market-chip topbar-pill">
              <span>Articles</span>
              <strong>{articlesCount}</strong>
            </div>
            <div className="market-chip topbar-pill">
              <span>Topics</span>
              <strong>{topicsCount}</strong>
            </div>
            <button
              className="ghost-button topbar-pill theme-toggle"
              type="button"
              aria-label={theme === 'dark' ? '切换为浅色模式' : '切换为深色模式'}
              onClick={onToggleTheme}
            >
              {theme === 'dark' ? '☀' : '☾'}
            </button>
            <button className="ghost-button topbar-pill" type="button" onClick={onRefresh}>刷新</button>
            <div className="status-pill topbar-pill">
              <span className="status-dot" aria-hidden="true" />
              {message}
            </div>
          </div>
        </header>

        {children}
      </main>

      <ToastHost toasts={toasts} />
    </div>
  );
}
