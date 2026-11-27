import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import {
  LearningEvidence,
  learningQuestions,
  periods,
  type Period,
  type StudyReport,
} from './LearningEvidence';
import { LearningJournal } from './LearningJournal';
import type { PaperVersion } from './PaperLab';
import './LearningWorkbench.css';

interface Props {
  versions: PaperVersion[];
  loading: boolean;
  error: string;
  onAccount: (id: number, day: string) => void;
  onVersion: (id: number) => void;
  onCompare: (version: PaperVersion, question?: string) => void;
  onFactors: () => void;
  onLab: () => void;
  onReading?: () => void;
}

const timing: Record<Period, string> = {
  daily: '盘前与盘后',
  weekly: '比较与诊断',
  monthly: '假设与结论',
  quarterly: '稳健性检验',
};

export function LearningWorkbench({
  versions,
  loading,
  error,
  onAccount,
  onVersion,
  onCompare,
  onFactors,
  onLab,
  onReading,
}: Props) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
  const [period, setPeriod] = useState<Period>(() => {
    const value = new URLSearchParams(window.location.search).get('study');
    return value && Object.prototype.hasOwnProperty.call(periods, value) ? value as Period : 'daily';
  });
  const [day, setDay] = useState(today);
  const [chosen, setChosen] = useState<number | undefined>(() => {
    const value = Number(new URLSearchParams(window.location.search).get('version'));
    return Number.isSafeInteger(value) && value > 0 ? value : undefined;
  });
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<{ key: string; report?: StudyReport; error?: string }>();
  const version =
    versions.find((v) => v.id === chosen) ??
    versions.filter((v) => v.started && v.lifecycle === 'RUNNING').sort((a, b) => a.id - b.id)[0] ??
    versions[0];
  const key = version && '/api/quant/paper/versions/learning/' + version.id + '?day=' + day;

  useEffect(() => {
    if (!key || !day) {
      return;
    }
    const controller = new AbortController();
    api<StudyReport>(key, { signal: controller.signal })
      .then((report) => {
        if (!report?.daily || !Array.isArray(report.accounts)) {
          throw new Error('学习记录格式有误，请重新读取');
        }
        if (!controller.signal.aborted) {
          setResult({ key, report });
        }
      })
      .catch((err: unknown) => {
        if (!controller.signal.aborted) {
          setResult({ key, error: err instanceof Error ? err.message : '读取学习记录失败' });
        }
      });
    const timer = day === today ? window.setTimeout(() => setReload((n) => n + 1), 60000) : undefined;
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [key, day, today, reload]);

  const current = result?.key === key ? result : undefined;
  const check = current?.report?.accounts.find((a) => a.version === version?.id);
  const refresh = () => setReload((n) => n + 1);
  return (
    <section id="quant-learning" className="learn-workbench" aria-label="量化学习台">
      <header className="learn-top">
        <div>
          <h4>量化学习台</h4>
          <p>选择策略和日期，查看当时的记录。</p>
        </div>
        {version && (
          <div className="learn-filters">
            <label>
              观察策略
              <select value={version.id} onChange={(e) => setChosen(Number(e.target.value))}>
                {versions.map((v) => (
                  <option key={v.id} value={v.id}>
                    #{v.id} · {v.spec.rule.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              观察日期
              <input
                type="date"
                value={day}
                max={today}
                min={version.created?.slice(0, 10)}
                onChange={(e) => {
                  if (e.target.value) {
                    setDay(e.target.value);
                  }
                }}
              />
            </label>
          </div>
        )}
      </header>
      <nav className="learn-periods" aria-label="学习周期">
        {(Object.keys(periods) as Period[]).map((p) => (
          <button type="button" key={p} aria-pressed={period === p} onClick={() => setPeriod(p)}>
            <strong>{periods[p]}</strong>
            <small>{timing[p]}</small>
          </button>
        ))}
      </nav>
      {current?.report && version ? (
        <div className="learn-grid">
          <section className="learn-observe" aria-label={periods[period] + '证据'}>
            <header>
              <span>{periods[period]}</span>
              <h4>{learningQuestions[period]}</h4>
            </header>
            <LearningEvidence
              period={period}
              report={current.report}
              account={onAccount}
              onChoose={setChosen}
            />
            {period !== 'daily' && (
              <footer className="learn-actions">
                {period === 'weekly' && <button onClick={onFactors}>查看因子 IC 与分组 →</button>}
                {period === 'monthly' && <button onClick={onLab}>检查数据与实验档案 →</button>}
                {period === 'quarterly' && (
                  <>
                    <button onClick={() => onVersion(version.id)}>打开完整回测 →</button>
                    {onReading && <button onClick={onReading}>打开研究知识库 →</button>}
                  </>
                )}
                {period !== 'monthly' && <button onClick={() => onCompare(version)}>设计同数据对照 →</button>}
              </footer>
            )}
          </section>
          <aside className="learn-aside">
            {period === 'daily' && check && check.reasons.length > 0 && (
              <section className="learn-reasons">
                <h5>未成交原因</h5>
                {check.reasons.map((reason) => (
                  <div className="learn-reason" key={reason.reason}>
                    <span>{reason.reason}</span>
                    <strong>{reason.count} 笔</strong>
                    <progress aria-label={reason.reason} value={reason.count} max={check.rejected} />
                  </div>
                ))}
              </section>
            )}
            <LearningJournal
              key={key + '-' + period}
              period={period}
              report={current.report}
              versions={versions}
              reload={refresh}
              onRegistered={() => setDay(today)}
              onCompare={(question) => onCompare(version, question)}
            />
          </aside>
        </div>
      ) : (
        <div className="learn-placeholder">
          {current?.error ? (
            <p role="alert">
              {current.error} <button onClick={refresh}>重试</button>
            </p>
          ) : loading || key ? (
            <p role="status">正在读取策略与观测…</p>
          ) : error ? (
            <p>策略目录暂不可用。</p>
          ) : (
            <>
              <p>先创建一个策略实验，即可从它的回测开始学习。</p>
              <button onClick={onLab}>打开实验室 →</button>
            </>
          )}
        </div>
      )}
    </section>
  );
}
