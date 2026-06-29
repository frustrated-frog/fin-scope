import { useMemo, useState } from 'react';

import { Table } from '../../shared/components/Table';
import { ResearchRun, ResearchRunDetail } from '../../shared/types';

const themes = [
  { code: 'china_macro', label: '中国宏观' },
  { code: 'ai_startup', label: 'AI 创业' },
  { code: 'company_ipo', label: '公司 / IPO' }
];

export function ResearchView({
  runs,
  detail,
  busy,
  onRun,
  onOpenRun,
  onOpenBrief
}: {
  runs: ResearchRun[];
  detail: ResearchRunDetail | null;
  busy: boolean;
  onRun: (input: {
    runDate: string;
    themeCodes: string[];
    maxSourcesPerTheme: number;
    includeDisabled: boolean;
  }) => Promise<void>;
  onOpenRun: (id: number) => Promise<void>;
  onOpenBrief: (date: string) => void;
}) {
  const today = useMemo(() => new Date().toISOString().slice(0, 10), []);
  const [runDate, setRunDate] = useState(today);
  const [themeCodes, setThemeCodes] = useState<string[]>(['china_macro', 'ai_startup', 'company_ipo']);
  const [maxSourcesPerTheme, setMaxSourcesPerTheme] = useState(3);
  const [includeDisabled, setIncludeDisabled] = useState(false);

  async function submit() {
    await onRun({ runDate, themeCodes, maxSourcesPerTheme, includeDisabled });
  }

  function toggleTheme(code: string) {
    setThemeCodes((current) => current.includes(code)
      ? current.filter((item) => item !== code)
      : [...current, code]);
  }

  return (
    <section className="research-workbench">
      <div className="research-control-panel">
        <div>
          <p className="eyebrow">Research run</p>
          <h3>生成今日研究</h3>
        </div>
        <div className="research-controls">
          <label className="inline-select">
            <span>日期</span>
            <input type="date" value={runDate} onChange={(event) => setRunDate(event.target.value)} />
          </label>
          <label className="inline-select">
            <span>每主题来源数</span>
            <input
              min={1}
              max={8}
              type="number"
              value={maxSourcesPerTheme}
              onChange={(event) => setMaxSourcesPerTheme(Number(event.target.value))}
            />
          </label>
          <label className="research-toggle">
            <input
              type="checkbox"
              checked={includeDisabled}
              onChange={(event) => setIncludeDisabled(event.target.checked)}
            />
            <span>包含停用来源</span>
          </label>
        </div>
        <div className="theme-segments">
          {themes.map((theme) => (
            <button
              key={theme.code}
              className={themeCodes.includes(theme.code) ? 'segment active' : 'segment'}
              type="button"
              onClick={() => toggleTheme(theme.code)}
            >
              {theme.label}
            </button>
          ))}
        </div>
        <button
          className="primary-button"
          type="button"
          disabled={busy || themeCodes.length === 0}
          onClick={submit}
        >
          {busy ? '运行中' : '运行研究'}
        </button>
      </div>

      <div className="research-grid">
        <div className="panel">
          <div className="panel-head">
            <div>
              <p className="eyebrow">Runs</p>
              <h3>研究运行记录</h3>
            </div>
            <span className="subtle-badge">{runs.length} runs</span>
          </div>
          <Table
            headers={['日期', '状态', '来源', '事件', '证据', '学习/选题']}
            rows={runs.map((run) => [
              <button className="link-button" type="button" onClick={() => onOpenRun(run.id)}>{run.runDate}</button>,
              run.status,
              `${run.fetchedSourceCount ?? 0}/${run.sourceCount}`,
              `${run.eventCount ?? 0}`,
              `${run.evidenceCount ?? 0}`,
              `${run.learningTaskCount ?? 0}/${run.contentIdeaCount ?? 0}`
            ])}
            empty="还没有研究运行"
          />
        </div>

        <aside className="research-detail-panel">
          <div className="panel-head">
            <div>
              <p className="eyebrow">Trace</p>
              <h3>运行细节</h3>
            </div>
            {detail?.run?.briefDate && (
              <button className="ghost-button" type="button" onClick={() => onOpenBrief(detail.run.briefDate as string)}>
                打开简报
              </button>
            )}
          </div>
          {detail ? (
            <>
              <div className="run-summary">
                <strong>{detail.run.status}</strong>
                <span>{detail.run.summary || '-'}</span>
              </div>
              <div className="planned-source-list">
                <div className="section-kicker">计划来源</div>
                {(detail.plannedSources || []).length ? detail.plannedSources.map((source) => (
                  <div className="planned-source-row" key={`${source.sourceId}-${source.sourceName}`}>
                    <strong>{source.sourceName}</strong>
                    <span>{source.sourceTier || 'UNKNOWN'} · C{source.credibility ?? '-'} · {source.enabled ? 'ON' : 'OFF'}</span>
                  </div>
                )) : (
                  <p className="empty-state compact">没有来源快照</p>
                )}
              </div>
              <div className="agent-trace-list">
                {detail.agentRuns.map((run) => (
                  <div className="trace-row" key={run.id}>
                    <div>
                      <strong>{run.nodeName}</strong>
                      <span>{run.status}</span>
                      {(run.output || run.errorMessage) && (
                        <small>{run.errorMessage || run.output}</small>
                      )}
                    </div>
                    <small>{run.durationMs}ms</small>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <p className="empty-state">选择一次运行查看 agent trace。</p>
          )}
        </aside>
      </div>
    </section>
  );
}
