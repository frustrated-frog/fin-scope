import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { fundamentalSource } from './fundamentalResearchTypes';
import type { FundamentalBusinessFinding, FundamentalBusinessResearch } from './fundamentalResearchTypes';

const supportLabels = { SUPPORTED: '资料支持', PARTIALLY_SUPPORTED: '部分支持', CONTRADICTED: '有反向证据' };

export function FundamentalBusinessResearchPanel({ code, disabled, onAdopt }: {
  code: string; disabled: boolean; onAdopt: (finding: FundamentalBusinessFinding) => void;
}) {
  const [view, setView] = useState<FundamentalBusinessResearch>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [pollPaused, setPollPaused] = useState(false);
  const version = useRef(0);
  const base = `/api/watchlist/stocks/${encodeURIComponent(code)}/fundamental-research/business-research`;

  async function load(start = false) {
    const current = ++version.current;
    setBusy(true);
    setError('');
    setPollPaused(false);
    try {
      const result = await api<FundamentalBusinessResearch>(start ? `${base}/runs` : base, start ? { method: 'POST' } : undefined);
      if (version.current === current) {
        setView(result);
      }
    } catch (reason) {
      if (version.current === current) {
        setError(reason instanceof Error ? reason.message : '经营研究读取失败，请重试');
        setPollPaused(true);
      }
    } finally {
      if (version.current === current) {
        setBusy(false);
      }
    }
  }

  useEffect(() => {
    setView(undefined);
    void load();
    return () => { version.current += 1; };
  }, [code]);

  useEffect(() => {
    if (view?.status !== 'RUNNING' || busy || pollPaused) {
      return undefined;
    }
    const timer = window.setTimeout(() => void load(), 2500);
    return () => window.clearTimeout(timer);
  }, [code, view, busy, pollPaused]);

  const running = view?.status === 'RUNNING';
  return <section className="fr-business-research" aria-label="经营研究摘要">
    <header><div><h5>从公开资料整理经营认识</h5><p aria-live="polite">{view?.message || '正在读取经营研究…'}</p></div>
      <button type="button" disabled={busy || running || disabled} onClick={() => void load(true)}>{running ? '正在整理经营…' : view?.findings?.length ? '重新整理经营' : '自动整理经营'}</button>
    </header>
    {error && <p role="alert" className="fr-feedback is-error">{error} <button type="button" disabled={busy} onClick={() => void load()}>重新读取经营研究</button></p>}
    {(view?.findings?.length ?? 0) > 0 && <div className="fr-business-findings">{view!.findings.map(finding => <article key={finding.aspect}>
      <div className="fr-finding-heading"><h5>{finding.label}</h5><span data-support={finding.support}>{supportLabels[finding.support]}</span></div>
      <p>{finding.content}</p>
      <ul>{finding.sources.map(source => <li key={source.evidenceRef}>{fundamentalSource(source.url) && <a href={fundamentalSource(source.url)} target="_blank" rel="noreferrer">{source.title || source.evidenceRef} ↗</a>}{source.publishedAt && <time>{source.publishedAt.slice(0, 10)}</time>}{source.contentOrigin?.includes('SNIPPET') && <small>检索摘要</small>}</li>)}</ul>
      <footer><small>{finding.runId !== view?.latestRunId ? '上次可用研究' : '整理于'} {finding.generatedAt?.replace('T', ' ').slice(0, 16) || '时间未记录'}</small>
        <button type="button" disabled={disabled} onClick={() => onAdopt(finding)}>{finding.support === 'CONTRADICTED' ? '补入反向线索' : '补入经营笔记'} →</button></footer>
    </article>)}</div>}
    <p className="fr-source">自动整理会同步更新这只股票的学习卡；选取内容后仍可编辑，再保存为自己的经营笔记。</p>
  </section>;
}
