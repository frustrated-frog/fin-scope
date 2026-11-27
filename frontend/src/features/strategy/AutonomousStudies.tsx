import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { StudyEvidence, methodName, type Evidence, type Material } from './StudyEvidence';
import './AutonomousStudies.css';

const url = '/api/quant/research-center';
const states: Record<string, string> = {
  QUEUED: '排队中',
  PLANNING: 'AI 正在选题',
  COMPUTING: '正在计算',
  WRITING: '正在撰写报告',
  PUBLISHED: '已发表',
  FAILED: '执行失败',
  WAITING_DATA: '等待新材料',
};
const tools: Record<Material['tool'], string> = {
  ALLOCATION: '组合配置',
  CORRELATION: '市场与风险',
  EXECUTION: '交易行为',
};
const outcomes: Record<string, string> = {
  SUPPORTED: '样本支持假设',
  MIXED: '证据有限或混合',
  UNSUPPORTED: '假设未获支持',
};
type Plan = {
  title: string;
  concept: string;
  motivation: string;
  criterion: string;
  hypothesis: string;
  material: Material;
  parentId?: number;
  model: string;
};
type Report = {
  background: string;
  finding: string;
  theory: string;
  mathematics: string;
  method: string;
  interpretation: string;
  limits: string;
  nextQuestion: string;
  outcome: string;
  merits?: Record<string, number>;
  value: string;
  reason: string;
  model: string;
};
interface Study {
  id: number;
  state: string;
  created: number;
  updated: number;
  trigger_key: string;
  plan: Plan | null;
  report: Report | null;
  note: string | null;
  evidence?: Evidence | null;
}
interface Page {
  items: Study[];
  highlights?: Study[];
  hasMore: boolean;
  model: string;
}
const active = (s: Study) => ['QUEUED', 'PLANNING', 'COMPUTING', 'WRITING'].includes(s.state);
const date = (utc: number) =>
  new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(utc * 1000);
const errorText = (e: unknown) => (e instanceof Error ? e.message : '请求未完成，请重试');

const terms: Record<string, string> = {...tools, ...methodName, null: '暂无估计值'};
function studyText(text?: string) {
  return text?.replace(/\b(?:ALLOCATION|CORRELATION|EXECUTION|EQUAL|INVERSE_VOL|RISK_PARITY|null)\b/g, term => terms[term]);
}

function ReportBody({
  s,
  onSource,
  onStudy,
}: {
  s: Study;
  onSource: (m: Material) => void;
  onStudy: (id: number) => void;
}) {
  const [tab, setTab] = useState('计算证据'),
    [question, setQuestion] = useState(''),
    [answer, setAnswer] = useState(''),
    [asking, setAsking] = useState(false),
    [error, setError] = useState('');
  const r = s.report,
    p = s.plan,
    e = s.evidence;
  if (!p) {
    return <p role="status">{s.note || states[s.state]}</p>;
  }
  async function ask() {
    setAsking(true);
    setError('');
    setAnswer('');
    try {
      const result = await api<{ answer: string }>(url + '/' + s.id + '/question', {
        method: 'POST',
        body: JSON.stringify({ question }),
      });
      setAnswer(result.answer);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setAsking(false);
    }
  }
  return (
    <article className="qs-paper">
      <div className="qs-meta">
        <span className="qs-topic">{tools[p.material.tool]}</span>
        <span>研究 #{s.id}</span>
        <span>
          {p.material.first} — {p.material.last}
        </span>
      </div>
      <h3>{studyText(p.title)}</h3>
      <p className="qs-finding">{studyText(r?.finding ?? p.hypothesis)}</p>
      {r && (
        <div className="qs-verdict">
          <strong>{outcomes[r.outcome]}</strong>
          <span>
            {r.value} · {studyText(r.reason)}
          </span>
        </div>
      )}
      <nav className="qs-tabs" aria-label="研究报告章节">
        {['计算证据', '金融课堂', '实验设计', '追问助教'].map((t) => (
          <button aria-pressed={t === tab} key={t} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </nav>
      {tab === '计算证据' &&
        (e ? <StudyEvidence evidence={e} /> : <p role="status">{s.note || states[s.state]}</p>)}
      {tab === '实验设计' && (
        <div className="qs-prose">
          <h4>为什么研究这个问题</h4>
          <p>{studyText(p.motivation)}</p>
          <h4>事前假设与判断标准</h4>
          <p>{studyText(p.hypothesis)}</p>
          <p>{studyText(p.criterion)}</p>
          {e && (
            <>
              <h4>数据与方法</h4>
              <p>{e.metrics.design}</p>
              <p>{studyText(r?.method)}</p>
              <p>{e.metrics.limitation}</p>
              <small>
                {p.material.title} · {p.material.observations} 个来源观测 · 材料指纹{' '}
                {p.material.source.slice(0, 16)}
              </small>
            </>
          )}
          {p.parentId && <button onClick={() => onStudy(p.parentId!)}>阅读关联研究 #{p.parentId}</button>}
          <button onClick={() => onSource(p.material)}>
            {p.material.tool === 'EXECUTION' ? '查看来源模拟账户' : '查看行情来源实验'} #{p.material.version}{' '}
            ↗
          </button>
        </div>
      )}
      {tab === '金融课堂' &&
        (r ? (
          <div className="qs-prose">
            <h4>{studyText(p.concept)}</h4>
            <p>{studyText(r.background)}</p>
            <p>{studyText(r.theory)}</p>
            <div className="qs-formula">{e?.metrics.formula}</div>
            <p>{studyText(r.mathematics)}</p>
            <h4>这次实验说明了什么</h4>
            <p>{studyText(r.interpretation)}</p>
            <h4>结论的适用范围</h4>
            <p>{studyText(r.limits)}</p>
            <h4>下一道研究问题</h4>
            <p>{studyText(r.nextQuestion)}</p>
            {r.merits && (
              <details className="qs-disclosure">
                <summary>研究价值评议</summary>
                <div className="qs-merits">
                  {Object.entries(r.merits).map(([label, score]) => (
                    <p key={label}>
                      {label}
                      <b>{['缺少证据', '初步线索', '有样本支持', '证据充分'][score]}</b>
                    </p>
                  ))}
                </div>
              </details>
            )}
            <small>教学报告 · {r.model}</small>
          </div>
        ) : (
          <p>报告发表后可学习这一课。</p>
        ))}
      {tab === '追问助教' &&
        (r ? (
          <form
            className="qs-prose"
            onSubmit={(event) => {
              event.preventDefault();
              void ask();
            }}
          >
            <label htmlFor="qs-question">围绕本研究的公式、数据或结论提问</label>
            <textarea
              id="qs-question"
              value={question}
              disabled={asking}
              onChange={(ev) => setQuestion(ev.target.value)}
              maxLength={500}
              rows={3}
            />
            <button disabled={!question.trim() || asking}>
              {asking ? '正在读取本次研究…' : '请 AI 讲解'}
            </button>
            {answer && <p className="qs-answer">{studyText(answer)}</p>}
            {error && <p role="alert">{error}</p>}
          </form>
        ) : (
          <p>报告发表后可向助教提问。</p>
        ))}
    </article>
  );
}

export function AutonomousStudies({
  onStrategies,
  onSource,
}: {
  onStrategies: () => void;
  onSource: (m: Material) => void;
}) {
  const [page, setPage] = useState<Page>(),
    [offset, setOffset] = useState(0),
    [chosen, choose] = useState<number | undefined>(
      () => Number(new URLSearchParams(location.search).get('quantStudy')) || undefined,
    );
  const [detail, setDetail] = useState<Study>(),
    [filter, setFilter] = useState('全部'),
    [revision, reload] = useState(0),
    [error, setError] = useState(''),
    [detailError, setDetailError] = useState('');
  const [busy, setBusy] = useState(false),
    [actionError, setActionError] = useState('');
  const key = useRef<string>();
  const summary =
    page?.items.find((s) => s.id === chosen) ??
    (!chosen ? (page?.items.find((s) => s.state === 'PUBLISHED') ?? page?.items[0]) : undefined);
  const selected = chosen ?? summary?.id;
  useEffect(() => {
    const ctrl = new AbortController();
    let timer: number;
    async function read() {
      try {
        const result = await api<Page>(url + (offset ? '?offset=' + offset : ''), { signal: ctrl.signal });
        if (ctrl.signal.aborted) {
          return;
        }
        if (!Array.isArray(result.items)) {
          throw new Error('研究中心响应格式异常');
        }
        setPage(result);
        setError('');
        timer = window.setTimeout(read, result.items.some(active) ? 4000 : 30000);
      } catch (e) {
        if (!ctrl.signal.aborted) {
          setError(errorText(e));
          timer = window.setTimeout(read, 30000);
        }
      }
    }
    void read();
    return () => {
      ctrl.abort();
      window.clearTimeout(timer);
    };
  }, [offset, revision]);
  useEffect(() => {
    if (!selected) {
      return;
    }
    const ctrl = new AbortController();
    setDetailError('');
    api<Study>(url + '/' + selected, { signal: ctrl.signal })
      .then((v) => {
        if (!ctrl.signal.aborted) {
          setDetail(v);
        }
      })
      .catch((e) => {
        if (!ctrl.signal.aborted) {
          setDetailError(errorText(e));
        }
      });
    return () => ctrl.abort();
  }, [selected, summary?.updated, summary?.state, revision]);
  function select(id: number) {
    choose(id);
    setActionError('');
    const link = new URL(window.location.href);
    link.searchParams.set('quantStudy', String(id));
    window.history.replaceState(window.history.state, '', link);
  }
  async function submit(retry?: number) {
    setBusy(true);
    setActionError('');
    try {
      key.current ??= crypto.randomUUID();
      const item = await api<Study>(retry ? url + '/' + retry + '/retry' : url, {
        method: 'POST',
        headers: { 'Idempotency-Key': key.current },
      });
      key.current = undefined;
      setOffset(0);
      select(item.id);
      reload((v) => v + 1);
    } catch (e) {
      setActionError(errorText(e));
    } finally {
      setBusy(false);
    }
  }
  const current = detail?.id === selected ? detail : summary;
  const archive = (page?.items ?? []).filter(
    (s) => s.report && (filter === '全部' || s.plan?.material.tool === filter),
  );
  const pending = (page?.items ?? []).filter((s) => s.state !== 'PUBLISHED');
  return (
    <main className="qs-center">
      <header className="qs-heading">
        <div>
          <h2>AI 量化研究中心</h2>
          <p>由研究员选题、计算和写作的股票研究与金融课堂</p>
        </div>
        <div>
          <button onClick={onStrategies}>策略总览 ↗</button>
          <button className="qs-primary" disabled={busy} onClick={() => submit()}>
            {busy ? '正在提交…' : '安排下一项研究'}
          </button>
        </div>
      </header>
      {(error || actionError) && (
        <p role="alert">
          {error || actionError} <button onClick={() => reload((v) => v + 1)}>重新读取</button>
        </p>
      )}
      <div className="qs-layout">
        <section className="qs-focus" aria-label="当前研究">
          {detailError && (
            <p role="alert">
              {detailError} <button onClick={() => reload((v) => v + 1)}>重试</button>
            </p>
          )}
          {current ? (
            <ReportBody key={current.id} s={current} onSource={onSource} onStudy={select} />
          ) : (
            <div className="qs-empty">
              <h3>{page ? '等待首份研究' : '正在读取研究中心'}</h3>
              <p>本机服务运行时每天自动安排研究，也可以立即安排下一项。</p>
            </div>
          )}
        </section>
        <aside className="qs-aside">
          <header>
            <h3>研究动态</h3>
            <span>{page?.model}</span>
          </header>
          {pending.length ? (
            pending.slice(0, 5).map((s) => (
              <div key={s.id} className="qs-task">
                <button onClick={() => select(s.id)}>
                  <i data-active={active(s)} />
                  <span>
                    {states[s.state]}
                    <strong>{studyText(s.plan?.title) || '发现下一个研究问题'}</strong>
                  </span>
                </button>
                <small>
                  {date(s.created)} · {s.trigger_key.startsWith('daily:') ? '每日调度' : '手动追加'}
                </small>
                {s.note && <p>{s.note}</p>}
                {['FAILED', 'WAITING_DATA'].includes(s.state) && (
                  <button disabled={busy} onClick={() => submit(s.id)}>
                    重新排队
                  </button>
                )}
              </div>
            ))
          ) : (
            <p>{page ? '当前没有待处理任务。' : '读取任务中…'}</p>
          )}
          {!!page?.highlights?.length && (
            <section className="qs-picks">
              <h4>值得学习的研究</h4>
              {page.highlights.map((study) => (
                <button key={study.id} onClick={() => select(study.id)}>
                  <small>
                    {study.report?.value} · {tools[study.plan!.material.tool]}
                  </small>
                  <strong>{studyText(study.plan?.title)}</strong>
                  <span>{studyText(study.report?.reason)}</span>
                </button>
              ))}
            </section>
          )}
          {current?.plan && (
            <section className="qs-note">
              <small>本次研究的金融知识</small>
              <h4>{studyText(current.plan.concept)}</h4>
              <p>{studyText(current.plan.motivation)}</p>
            </section>
          )}
        </aside>
      </div>
      <section className="qs-library">
        <header>
          <h3>研究成果库</h3>
          <label>
            研究方向{' '}
            <select value={filter} onChange={(e) => setFilter(e.target.value)}>
              <option value="全部">全部方向</option>
              {Object.entries(tools).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
        </header>
        {archive.map((s) => (
          <button
            className="qs-library-row"
            key={s.id}
            aria-pressed={s.id === selected}
            onClick={() => select(s.id)}
          >
            <small>{tools[s.plan!.material.tool]}</small>
            <span>
              <strong>{studyText(s.plan?.title)}</strong>
              <span>{studyText(s.report?.reason)}</span>
            </span>
            <em>{s.report?.value}</em>
            <time>{date(s.created)}</time>
          </button>
        ))}
        {!archive.length && <p>{page ? '本页还没有这一方向的已发表研究。' : '正在读取成果…'}</p>}
        {(offset > 0 || page?.hasMore) && (
          <footer>
            <button disabled={!offset} onClick={() => setOffset((v) => v - 20)}>
              上一页
            </button>
            <span>第 {offset / 20 + 1} 页</span>
            <button disabled={!page?.hasMore} onClick={() => setOffset((v) => v + 20)}>
              下一页
            </button>
          </footer>
        )}
      </section>
    </main>
  );
}
