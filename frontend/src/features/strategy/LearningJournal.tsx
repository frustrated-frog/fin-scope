import { FormEvent, useState } from 'react';
import { api } from '../../shared/api/client';
import {
  LearningEvidence,
  periods,
  type Period,
  type StudyReport,
  type StudyEntry,
} from './LearningEvidence';
import type { PaperVersion } from './PaperLab';

interface Lesson {
  model: string;
  day: string;
  lesson: string;
  hypothesis: string;
  criterion: string;
}
const message = (error: unknown) => (error instanceof Error ? error.message : '读取失败，请重试');

function JournalRow({
  entry: e,
  endpoint,
  reload,
  versions,
  onCompare,
}: {
  entry: StudyEntry;
  endpoint: string;
  reload: () => void;
  versions: PaperVersion[];
  onCompare: (question: string) => void;
}) {
  const [outcome, setOutcome] = useState('证据不足'),
    [reason, setReason] = useState(''),
    [experiment, setExperiment] = useState('');
  const [saved, setSaved] = useState<{ initial: StudyReport; result: StudyReport | null }>();
  const [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await api(endpoint + '/entries/' + e.id, {
        method: 'POST',
        body: JSON.stringify({ outcome, reason, experiment: experiment ? Number(experiment) : null }),
      });
      setSaved(undefined);
      reload();
    } catch (ex) {
      setError(message(ex));
    } finally {
      setBusy(false);
    }
  }
  async function evidence() {
    setBusy(true);
    setError('');
    try {
      setSaved(await api(endpoint + '/entries/' + e.id));
    } catch (ex) {
      setError(message(ex));
    } finally {
      setBusy(false);
    }
  }
  return (
    <details className="learn-record">
      <summary>
        <time>
          {periods[e.period]} · {e.created.slice(0, 10)}
        </time>
        <b>{e.hypothesis}</b>
        <span>{e.verdict?.outcome ?? '待验证'}</span>
      </summary>
      <p>事前标准 · {e.criterion}</p>
      {e.source && <p>资料与出处 · {e.source}</p>}
      {e.verdict ? (
        <p>
          学员结论 · {e.verdict.reason} {e.verdict.experiment && ' · 对照 #' + e.verdict.experiment}
        </p>
      ) : (
        <form onSubmit={save}>
          <label>
            验证证据
            <select value={experiment} onChange={(ev) => setExperiment(ev.target.value)}>
              <option value="">本策略登记后的模拟记录</option>
              {versions
                .filter((v) => v.created && Date.parse(v.created) > Date.parse(e.created))
                .map((v) => (
                  <option key={v.id} value={v.id}>
                    #{v.id} · {v.spec.rule.name}
                  </option>
                ))}
            </select>
          </label>
          <label>
            我的判断
            <select value={outcome} onChange={(ev) => setOutcome(ev.target.value)}>
              {['证据不足', '支持', '推翻'].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
          <label>
            对照事前标准说明理由
            <input required maxLength={1200} value={reason} onChange={(ev) => setReason(ev.target.value)} />
          </label>
          <button disabled={busy || !reason.trim()}>保存并锁定结论</button>
        </form>
      )}
      {!e.verdict && (
        <button type="button" onClick={() => onCompare(e.hypothesis + '。验证标准：' + e.criterion)}>
          用这条假设设计对照
        </button>
      )}
      <button type="button" disabled={busy} onClick={saved ? () => setSaved(undefined) : evidence}>
        {saved ? '收起证据' : '查看登记时的证据'}
      </button>
      {saved && (
        <div className="learn-frozen">
          <h5>登记快照 · {saved.initial.day}</h5>
          <LearningEvidence period={e.period} report={{ ...saved.initial, entries: [] }} />
          {saved.result && (
            <>
              <h5>
                结论快照 · {saved.result.day} · #{saved.result.version}
              </h5>
              <LearningEvidence period={e.period} report={{ ...saved.result, entries: [] }} />
            </>
          )}
        </div>
      )}
      {error && <p role="alert">{error}</p>}
    </details>
  );
}

export function LearningJournal({
  report,
  period,
  versions,
  reload,
  onCompare,
  onRegistered,
}: {
  report: StudyReport;
  period: Period;
  versions: PaperVersion[];
  reload: () => void;
  onCompare: (question: string) => void;
  onRegistered: () => void;
}) {
  const [lesson, setLesson] = useState<Lesson>(),
    [writing, write] = useState(false);
  const [hypothesis, setHypothesis] = useState(''),
    [criterion, setCriterion] = useState(''),
    [source, setSource] = useState('');
  const [request, setRequest] = useState<{ token: string; body: string }>();
  const [busy, setBusy] = useState(''),
    [error, setError] = useState(''),
    [notice, setNotice] = useState('');
  const endpoint = '/api/quant/paper/versions/learning/' + report.version;
  async function teach() {
    setBusy('ai');
    setError('');
    try {
      setLesson(await api<Lesson>(endpoint + '/teach/' + period + '?day=' + report.day, { method: 'POST' }));
    } catch (ex) {
      setError(message(ex));
    } finally {
      setBusy('');
    }
  }
  async function register(event: FormEvent) {
    event.preventDefault();
    setError('');
    setBusy('save');
    const payload = {
      period,
      day: report.day,
      hypothesis: hypothesis.trim(),
      criterion: criterion.trim(),
      source: source.trim(),
    };
    const body = JSON.stringify(payload),
      token = request?.body === body ? request.token : crypto.randomUUID();
    setRequest({ body, token });
    try {
      await api(endpoint + '/entries', { method: 'POST', body: JSON.stringify({ ...payload, token }) });
      write(false);
      setHypothesis('');
      setCriterion('');
      setSource('');
      setRequest(undefined);
      setNotice('已按当前时间登记并锁定标准。');
      onRegistered();
      reload();
    } catch (ex) {
      setError(message(ex));
    } finally {
      setBusy('');
    }
  }
  return (
    <section className="learn-journal">
      <h5>读懂本期数据</h5>
      <p>
        {
          {
            daily: '信号代表策略想交易。到期时点是否运行、订单是否成交，是两个可以分别核对的问题。',
            weekly: '先对齐两周的覆盖日期。市场也下跌时，观察策略相对基准的变化，再决定要验证哪个原因。',
            monthly: '把每条结论和原先写下的标准放在一起看。只在已有明确结论的假设中计算支持率。',
            quarterly: '一段历史上的优胜版本，在之后的时间段还能领先吗？展开各折的选择和验证日期来检查。',
          }[period]
        }
      </p>
      <header>
        <h5>AI 学习助教</h5>
        <button disabled={!!busy} onClick={teach}>
          {busy === 'ai' ? '正在解读观测…' : lesson ? '重新解读' : '用 AI 讲解这组数据'}
        </button>
      </header>
      {lesson && (
        <article className="learn-lesson">
          <small>
            {lesson.model} · {lesson.day} · {periods[period]}
          </small>
          <p>{lesson.lesson}</p>
          <strong>下一步可以验证</strong>
          <p>{lesson.hypothesis}</p>
          <small>建议标准 · {lesson.criterion}</small>
          <button
            disabled={!!busy}
            onClick={() => {
              setHypothesis(lesson.hypothesis);
              setCriterion(lesson.criterion);
              write(true);
            }}
          >
            采用为研究草稿
          </button>
        </article>
      )}
      <header>
        <h5>
          我的迭代记录 <small>{report.entries.length}</small>
        </h5>
        <button type="button" onClick={() => write(!writing)}>
          {writing ? '收起草稿' : '记下观察与验证标准'}
        </button>
      </header>
      {writing && (
        <form onSubmit={register}>
          <label>
            假设或待解决的问题
            <textarea
              required
              maxLength={1200}
              value={hypothesis}
              onChange={(e) => setHypothesis(e.target.value)}
            />
          </label>
          <label>
            怎样算支持或推翻？登记后锁定
            <textarea
              required
              maxLength={1200}
              value={criterion}
              onChange={(e) => setCriterion(e.target.value)}
            />
          </label>
          <label>
            读到的论文、报告或其他出处（选填）
            <input maxLength={500} value={source} onChange={(e) => setSource(e.target.value)} />
          </label>
          <button disabled={!!busy || !hypothesis.trim() || !criterion.trim()}>锁定标准并保存证据</button>
        </form>
      )}
      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
      {report.entries.length === 0 && <p>截至所选日期，这个版本还没有学习记录。</p>}
      {report.entries.map((e) => (
        <JournalRow
          key={e.id}
          entry={e}
          versions={versions}
          endpoint={endpoint}
          reload={reload}
          onCompare={onCompare}
        />
      ))}
    </section>
  );
}
