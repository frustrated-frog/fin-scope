import { useEffect, useState } from 'react';
import { api } from '../../shared/api/client';

import { PaperResearchEvidence, type Trial } from './PaperResearchEvidence';

interface Job {
  id: number;
  created: string;
  state: string;
  version?: number;
  hypothesis: string;
  log: string[];
}

const endpoint = '/api/quant/paper/versions/jobs';
const stages: Record<string, string> = {
  QUEUED: '等待执行', DATA: '冻结数据', CODING: '编写策略', DEBUG: '修正代码',
  DEVELOPMENT: '完整开发段回测', ASSESS: '研究评议', VALIDATING: '留出段验证', CONCLUDED: '研究结束', WAITING_DATA: '等待新收盘行情',
  BACKTEST: '运行回测', DONE: '已完成', FAILED: '执行失败',
};

export function PaperCodingJobs({ onOpen }: { onOpen: (id: number) => void }) {
  const [evidence,setEvidence] = useState<Record<number, Trial[]>>({});
  const [jobs, setJobs] = useState<Job[]>([]);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [retrying, setRetrying] = useState<number>();
  useEffect(() => {
    let alive = true;
    let timer: number;
    async function load() {
      try {
        const result = await api<Job[]>(endpoint);
        if (alive) {
          setJobs(Array.isArray(result) ? result : []);
          setError('');
        }
      } catch (e) {
        if (alive) { setError(e instanceof Error ? e.message : '任务读取失败'); }
      } finally {
        if (alive) { timer = window.setTimeout(load, 4000); }
      }
    }
    void load();
    return () => { alive = false; window.clearTimeout(timer); };
  }, [revision]);

  async function retry(id: number) {
    setRetrying(id);
    try {
      await api(endpoint + '/' + id + '/retry', { method: 'POST' });
      setRevision(n => n + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : '重新提交失败');
    } finally {
      setRetrying(undefined);
    }
  }

  if (!jobs.length && !error) { return null; }
  return <section className="quant-readout paper-glass paper-jobs" aria-label="Codex 研究任务">
    <header><h4>Codex 研究任务</h4><small>后台执行</small></header>
    {error && <p role="alert">{error}</p>}
    {jobs.map(job => <div key={job.id} className="paper-job">
      <details>
        <summary><span className="paper-job-state" data-state={job.state}>{stages[job.state] ?? job.state}</span>
          <strong>{job.hypothesis}</strong><time>{job.created.slice(5, 16).replace('T', ' ')}</time></summary>
        {job.state === 'CONCLUDED' && (evidence[job.id] ?
          evidence[job.id].length > 0 ? <PaperResearchEvidence trials={evidence[job.id]}/> : <p>本次评议决定继续观察原账户。</p>
          : <button className="quant-record-link" onClick={async () => {
              try { const trials = await api<Trial[]>(endpoint + '/' + job.id + '/evidence'); setEvidence(old => ({...old,[job.id]:trials})); }
              catch (e) { setError(e instanceof Error ? e.message : '开发证据读取失败'); }
            }}>查看研究证据</button>)}
        <ol>{job.log.map((line, index) => <li key={index}>{line}</li>)}</ol>
      </details>
      {job.state === 'DONE' && job.version && <button className="quant-record-link" onClick={() => onOpen(job.version!)}>查看实验 ↗</button>}
      {job.state === 'FAILED' && <button className="quant-record-link" disabled={retrying === job.id} onClick={() => retry(job.id)}>重新执行</button>}
    </div>)}
  </section>;
}
