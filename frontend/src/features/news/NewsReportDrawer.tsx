import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { ReactionChart } from '../investment-observation/ReactionChart';
import { beforeEventReturn, dateTime, signed, sourceHref, statusLabels } from '../investment-observation/reactionTypes';
import type { NewsDetail, NewsReport } from './newsWindowTypes';

export function NewsReportDrawer({
  item,
  categories,
  onClose,
  onRead,
  onChanged,
  onOpen,
  addToast,
}: {
  item: NewsReport;
  categories: Array<{ code: string; name: string }>;
  onClose: () => void;
  onRead: (id: string, version: number) => void;
  onChanged: () => void;
  onOpen: (report: NewsReport) => void;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [detail, setDetail] = useState<NewsDetail>();
  const [error, setError] = useState('');
  const [tab, setTab] = useState<'source' | 'versions' | 'reaction'>('source');
  const [category, setCategory] = useState(item.categoryCode ?? '');
  const [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  useEffect(() => {
    const element = dialog.current;
    const previous = document.activeElement as HTMLElement | null;
    if (element?.showModal) {
      element.showModal();
    } else {
      element?.setAttribute('open', '');
    }
    return () => {
      element?.close?.();
      previous?.focus();
    };
  }, []);
  useEffect(() => {
    let active = true;
    setError('');
    void api<NewsDetail>(`/api/news/window/detail?id=${encodeURIComponent(item.id)}`)
      .then(async (next) => {
        if (!active) {
          return;
        }
        setDetail(next);
        setCategory(next.report.categoryCode ?? '');
        await api(`/api/news/window/read?id=${encodeURIComponent(item.id)}&version=${next.report.contentVersion}`, {
          method: 'POST',
        });
        if (active) {
          onRead(item.id, next.report.contentVersion);
        }
      })
      .catch((cause) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : '详情读取失败');
        }
      });
    return () => {
      active = false;
    };
  }, [item.id, retry]);

  async function review() {
    setBusy(true);
    try {
      await api(`/api/news/window/review?${new URLSearchParams({ id: item.id, category })}`, { method: 'POST' });
      addToast('分类已保存，后续规则更新会保留你的选择', 'success');
      onChanged();
      setRetry((value) => value + 1);
    } catch (cause) {
      addToast(cause instanceof Error ? cause.message : '分类保存失败', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function saveJournal() {
    setBusy(true);
    try {
      await api('/api/major-events', {
        method: 'POST',
        body: JSON.stringify({ originType: 'NEWS_ITEM', originKey: item.id }),
      });
      setSaved(true);
      addToast('已记入大事记', 'success');
    } catch (cause) {
      addToast(cause instanceof Error ? cause.message : '保存失败', 'error');
    } finally {
      setBusy(false);
    }
  }

  const report = detail?.report ?? item;
  const href = sourceHref(report.url);
  return (
    <dialog
      ref={dialog}
      className="news-report-dialog"
      aria-labelledby="news-report-title"
      onCancel={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div className="news-report-sheet">
        <header>
          <div>
            <span>
              {report.sourceName} · {dateTime(report.publishedAt)}
            </span>
            <h2 id="news-report-title">{report.title}</h2>
          </div>
          <button type="button" aria-label="关闭新闻详情" onClick={onClose}>
            ×
          </button>
        </header>
        <nav aria-label="新闻详情栏目">
          {(
            [
              ['source', '新闻原文'],
              ['versions', '内容变化'],
              ['reaction', '股票反应'],
            ] as const
          ).map(([value, label]) => (
            <button type="button" key={value} aria-pressed={tab === value} onClick={() => setTab(value)}>
              {label}
              {value === 'versions' && report.contentVersion > 1 ? <span>{report.contentVersion - 1}</span> : null}
            </button>
          ))}
        </nav>
        <div className="news-report-scroll">
          {error ? (
            <p className="news-window-error" role="alert">
              {error}
              <button type="button" onClick={() => setRetry((value) => value + 1)}>
                重试
              </button>
            </p>
          ) : null}
          {!detail && !error ? <p role="status">正在读取新闻与观察记录…</p> : null}
          {tab === 'source' ? (
            <>
              <div className="news-report-meta">
                <span>首次收录 {dateTime(report.firstSeenAt)}</span>
                <span>正文版本 v{report.contentVersion}</span>
                {report.historicalBackfill ? <span>补充收录</span> : null}
              </div>
              <p className="news-report-body">{report.content}</p>
              <div className="news-report-actions">
                {href ? (
                  <a href={href} target="_blank" rel="noreferrer">
                    打开原始来源 ↗
                  </a>
                ) : null}
                <button type="button" disabled={busy || saved} onClick={() => void saveJournal()}>
                  {saved ? '已记入大事记' : '记入大事记'}
                </button>
              </div>
              <section className="news-report-rule">
                <h3>分类依据</h3>
                <p>{report.classificationReason ?? '未命中明确规则，保留原文'}</p>
                {report.manuallyReviewed ? <p>已人工确认 · {report.categoryName}</p> : null}
                <label>
                  调整分类
                  <select value={category} onChange={(event) => setCategory(event.target.value)}>
                    <option value="">选择分类</option>
                    {categories.map((value) => (
                      <option key={value.code} value={value.code}>
                        {value.name}
                      </option>
                    ))}
                  </select>
                </label>
                <button type="button" disabled={busy || !category} onClick={() => void review()}>
                  保存分类
                </button>
              </section>
            </>
          ) : null}
          {tab === 'versions' ? (
            <section className="news-version-history">
              <h3>报道与内容变化</h3>
              <p>记录采集到的原文版本。文本不同不等于事件事实发生变化。</p>
              {detail && detail.versions.length === 1 ? (
                <p className="news-window-empty">目前只有首次收录版本，没有发现正文更新。</p>
              ) : null}
              {detail?.relatedReports?.length ? (
                <div className="news-related-reports">
                  <h4>同一事件的其他报道</h4>
                  {detail.relatedReports.map((related) => (
                    <button type="button" key={related.id} onClick={() => onOpen(related)}>
                      <span>
                        {related.sourceName} · {dateTime(related.publishedAt)}
                      </span>
                      {related.title}
                    </button>
                  ))}
                </div>
              ) : null}
              {detail?.versions.map((version, index) => (
                <article key={version.version}>
                  <header>
                    <strong>
                      v{version.version} · {version.version === 1 ? '首次收录' : '原文更新'}
                    </strong>
                    <time>{dateTime(version.detectedAt)}</time>
                  </header>
                  <h4>{version.title}</h4>
                  <p>{version.content}</p>
                  {detail.versions[index + 1] ? (
                    <details>
                      <summary>与上一版本对照</summary>
                      <div className="news-version-comparison">
                        <div>
                          <strong>更新前 · v{detail.versions[index + 1].version}</strong>
                          <p>{detail.versions[index + 1].title}</p>
                          <p>{detail.versions[index + 1].content}</p>
                        </div>
                        <div>
                          <strong>更新后 · v{version.version}</strong>
                          <p>{version.title}</p>
                          <p>{version.content}</p>
                        </div>
                      </div>
                    </details>
                  ) : null}
                </article>
              ))}
            </section>
          ) : null}
          {tab === 'reaction' ? (
            <section className="news-stock-reactions">
              <h3>新闻前后的股票表现</h3>
              <p>沿用投资观察的自动关联与交易日口径，走势本身不证明新闻导致涨跌。</p>
              {detail && !detail.reactions.length ? (
                <div className="news-window-empty">
                  尚未建立可靠的股票关联。系统会自动识别支持的业绩与合同事件，未确认的关联不会用于计算。
                </div>
              ) : null}
              {detail?.reactions.map((sample) => (
                <article key={sample.id}>
                  <header>
                    <h4>
                      {sample.instrumentName || '待关联公司'} <small>{sample.instrumentCode}</small>
                    </h4>
                    <span>
                      {sample.state === 'ARCHIVED' ? '已归档' : sample.state === 'DRAFT' ? '等待确认关联' : '持续观察'}
                    </span>
                  </header>
                  {sample.discoveryIssue ? <p>{sample.discoveryIssue}</p> : null}
                  {sample.relationNote ? <p>{sample.relationNote}</p> : null}
                  {sample.calculation ? (
                    <>
                      <div className="news-reaction-metrics">
                        <div>
                          <span>新闻前5日</span>
                          <strong>{signed(beforeEventReturn(sample), '%')}</strong>
                        </div>
                        {sample.calculation.windows.map((window) => (
                          <div key={window.sessions}>
                            <span>新闻后{window.sessions}日</span>
                            <strong>
                              {window.status === 'READY'
                                ? signed(window.stockReturnPct, '%')
                                : statusLabels[window.status]}
                            </strong>
                            <small>{window.endDate}</small>
                          </div>
                        ))}
                      </div>
                      <ReactionChart
                        series={[
                          {
                            label: sample.instrumentName || sample.instrumentCode,
                            points: sample.calculation.points,
                            metric: 'stockReturnPct',
                            color: 'var(--nw-accent)',
                          },
                          {
                            label: sample.calculation.benchmarkName,
                            points: sample.calculation.points,
                            metric: 'benchmarkReturnPct',
                            color: 'var(--nw-muted)',
                          },
                        ]}
                      />
                      <p className="news-report-meta">
                        行情更新 {dateTime(sample.calculation.calculatedAt)} · {sample.calculation.stockSource}
                      </p>
                      {sample.calculation.warnings.map((warning, index) => (
                        <p key={index}>{warning}</p>
                      ))}
                    </>
                  ) : (
                    <p>正在等待可用行情，尚未到期的窗口不会填为零。</p>
                  )}
                  {sample.refreshError ? (
                    <p className="news-window-error">最近更新失败：{sample.refreshError}</p>
                  ) : null}
                </article>
              ))}
            </section>
          ) : null}
        </div>
      </div>
    </dialog>
  );
}
