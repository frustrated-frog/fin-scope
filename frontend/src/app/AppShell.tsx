import { ReactNode } from 'react';

import { ToastHost } from '../shared/components/ToastHost';
import { ToastItem, View } from '../shared/types';

const navItems: Array<{ id: View; label: string; hint: string; code: string }> = [
  { id: 'dashboard', label: 'Dashboard', hint: '总览', code: '01' },
  { id: 'sources', label: 'Sources', hint: '信源', code: '02' },
  { id: 'article', label: 'Article', hint: '文章', code: '03' },
  { id: 'briefs', label: 'Briefs', hint: '日报', code: '04' },
  { id: 'research', label: 'Research', hint: '运行', code: '05' },
  { id: 'events', label: 'Events', hint: '事件', code: '06' },
  { id: 'evidence', label: 'Evidence', hint: '证据', code: '07' },
  { id: 'topics', label: 'Topics', hint: '主题', code: '08' },
  { id: 'learning', label: 'Learning', hint: '学习', code: '09' },
  { id: 'contentStudio', label: 'Studio', hint: '选题', code: '10' },
  { id: 'agents', label: 'Agent Runs', hint: 'Trace', code: '11' },
  { id: 'settings', label: 'Settings', hint: '设置', code: '12' }
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
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">FS</span>
          <div>
            <h1>FinScope</h1>
            <p>Research Intelligence</p>
          </div>
        </div>
        <div className="sidebar-signal">
          <span>Pipeline</span>
          <strong>Sources / Article / Brief / Topics</strong>
        </div>
        <nav aria-label="Workspace">
          {navItems.map((item) => (
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
            <div className="status-pill topbar-pill">{message}</div>
          </div>
        </header>

        {children}
      </main>

      <ToastHost toasts={toasts} />
    </div>
  );
}
