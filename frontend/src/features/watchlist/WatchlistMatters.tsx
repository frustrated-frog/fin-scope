import { useEffect, useId, useMemo, useRef, useState } from 'react';
import type { WatchlistItem } from '../../shared/types';
import { acquireWatchlistOverlayScrollLock } from './watchlistOverlayScrollLock';
import { matterReviewDate, watchlistMatterEvidence } from './watchlistMatterEvidence';
import type { WatchlistMatter } from './watchlistMatterEvidence';
import './watchlistMatters.css';

type MatterEntry = WatchlistMatter & { displayName: string };

function latestStep(matter: WatchlistMatter) {
  return matter.steps[matter.steps.length - 1];
}

function Status({ matter }: { matter: WatchlistMatter }) {
  return <span className="matter-status" data-tone={matter.tone}>{matter.status}</span>;
}

function Metrics({ matter }: { matter: WatchlistMatter }) {
  return <dl className="matter-metrics">
    {matter.metrics.map(({ label, value }) => <div key={label}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>)}
  </dl>;
}

function MatterRow({ matter, onClick, selected }: {
  matter: MatterEntry;
  onClick: () => void;
  selected?: boolean;
}) {
  const date = latestStep(matter).publishedOn;
  return <button type="button" className="matter-row" aria-pressed={selected}
    aria-haspopup={selected === undefined ? 'dialog' : undefined} onClick={onClick}>
    <span className="matter-row-meta">
      <span>{matter.displayName}</span>
      <time dateTime={date} title={date}>{date.slice(5).replace('-', '.')}</time>
    </span>
    <strong>{matter.title}</strong>
    <Status matter={matter} />
    <span className="matter-row-arrow" aria-hidden="true">›</span>
  </button>;
}

function MatterDialog({ matters, initialMatter, onClose }: {
  matters: MatterEntry[];
  initialMatter?: MatterEntry;
  onClose: () => void;
}) {
  const titleId = useId();
  const dialog = useRef<HTMLDialogElement>(null);
  const reader = useRef<HTMLElement>(null);
  const backButton = useRef<HTMLButtonElement>(null);
  const listTitle = useRef<HTMLHeadingElement>(null);
  const detailTitle = useRef<HTMLHeadingElement>(null);
  const [selectedId, setSelectedId] = useState(initialMatter?.id || matters[0].id);
  const [isReading, setIsReading] = useState(Boolean(initialMatter));
  const selected = matters.find((matter) => matter.id === selectedId) ?? matters[0];

  useEffect(() => {
    const element = dialog.current!;
    const trigger = document.activeElement;
    element.showModal();
    const unlock = acquireWatchlistOverlayScrollLock();
    return () => {
      element.close();
      unlock();
      if (trigger instanceof HTMLElement && trigger.isConnected) {
        trigger.focus({ preventScroll: true });
      }
    };
  }, []);

  useEffect(() => {
    if (reader.current) {
      reader.current.scrollTop = 0;
    }
    if (isReading && backButton.current?.getClientRects().length) {
      detailTitle.current?.focus({ preventScroll: true });
    }
  }, [selected.id, isReading]);

  function showList() {
    setIsReading(false);
    requestAnimationFrame(() => listTitle.current?.focus({ preventScroll: true }));
  }

  return <dialog ref={dialog} className="matter-dialog" aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); onClose(); }}
    onClick={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <div className="matter-dialog-shell">
      <header className="matter-dialog-head">
        <button ref={backButton} className="matter-back" type="button" hidden={!isReading} onClick={showList}>
          <span aria-hidden="true">←</span> 事项列表
        </button>
        <h2 id={titleId}>重大事项进展</h2>
        <button className="matter-close" type="button" aria-label="关闭重大事项进展" onClick={onClose}>×</button>
      </header>
      <div className={`matter-dialog-body${isReading ? ' is-reading' : ''}`}>
        <nav className="matter-dialog-nav" aria-label="自选事项列表">
          <h3 ref={listTitle} tabIndex={-1}>自选事项 <span>{matters.length}</span></h3>
          <ul>
            {matters.map((matter) => <li key={matter.id}>
              <MatterRow matter={matter} selected={matter.id === selected.id} onClick={() => {
                setSelectedId(matter.id);
                setIsReading(true);
              }} />
            </li>)}
          </ul>
        </nav>
        <article className="matter-reader" ref={reader} aria-labelledby={`${titleId}-matter`}>
          <div className="matter-reader-heading">
            <div className="matter-identity">
              <span>{selected.displayName} <small>{selected.stockCode}</small></span>
              <span>{selected.category}</span>
              <Status matter={selected} />
            </div>
            <h3 ref={detailTitle} tabIndex={-1} id={`${titleId}-matter`}>{selected.title}</h3>
            <p>{selected.summary}</p>
            <Metrics matter={selected} />
          </div>
          <div className="matter-timeline-heading">
            <h4>披露时间线</h4>
            <span>按公告披露日排列</span>
          </div>
          <ol className="matter-timeline">
            {selected.steps.map((step) => <li key={step.publishedOn}>
              <time dateTime={step.publishedOn}>{step.publishedOn.replace(/-/g, '.')}</time>
              <div className="matter-step-copy">
                <h5>{step.title}</h5>
                <p>{step.detail}</p>
                <a href={step.document.url} target="_blank" rel="noopener noreferrer">
                  {step.document.label} <span aria-hidden="true">↗</span>
                </a>
              </div>
            </li>)}
          </ol>
          <p className="matter-reading-note">{selected.readingNote}</p>
        </article>
      </div>
      <footer className="matter-dialog-foot">
        <span>首批核对样本 · 样本核对日 <time dateTime={matterReviewDate}>{matterReviewDate}</time></span>
        <span>来源：上市公司公告与财报</span>
      </footer>
    </div>
  </dialog>;
}

export function WatchlistMatters({ items }: { items: WatchlistItem[] }) {
  const headingId = useId();
  const [dialogTarget, setDialogTarget] = useState<{ matter?: MatterEntry }>();
  const { matters, stockCount } = useMemo(() => {
    const stocks = new Map(items.filter((item) => item.type === 'STOCK').map((item) => [item.code, item.name]));
    const matching = watchlistMatterEvidence.flatMap((matter) => stocks.has(matter.stockCode)
      ? [{ ...matter, displayName: stocks.get(matter.stockCode) || matter.stockName }]
      : []);
    matching.sort((left, right) => latestStep(right).publishedOn.localeCompare(latestStep(left).publishedOn));
    return { matters: matching, stockCount: stocks.size };
  }, [items]);

  if (!stockCount) {
    return null;
  }
  const featured = matters.find((matter) => matter.featured) ?? matters[0];
  const otherMatters = matters.filter((matter) => matter.id !== featured?.id).slice(0, 3);
  const companyCount = new Set(matters.map((matter) => matter.stockCode)).size;

  return <section className="watchlist-matters" aria-labelledby={headingId}>
    <header className="matter-overview-head">
      <div>
        <div className="matter-overview-title">
          <h3 id={headingId}>重大事项进展</h3>
          <span>首批核对样本</span>
        </div>
        <p>把同一件事的计划、变更和结果串起来。</p>
      </div>
      {featured && <button type="button" className="matter-view-all" aria-haspopup="dialog" onClick={() => setDialogTarget({})}>
        查看全部 <span>{matters.length}</span> <span aria-hidden="true">↗</span>
      </button>}
    </header>
    {featured ? <>
      <div className={`matter-overview-grid${otherMatters.length ? ' has-others' : ''}`}>
        <button type="button" className="matter-feature" aria-haspopup="dialog" onClick={() => setDialogTarget({ matter: featured })}>
          <span className="matter-feature-meta">
            <span>{featured.displayName} <small>{featured.stockCode}</small></span>
            <Status matter={featured} />
          </span>
          <strong className="matter-feature-title">{featured.title}</strong>
          <span className="matter-stage-strip" aria-hidden="true">
            {featured.steps.map((step) => <span key={step.publishedOn}>{step.title}</span>)}
          </span>
          <span className="matter-feature-bottom">
            <span className="matter-feature-metrics">
              {featured.metrics.map(({ label, value }) => <span key={label}>
                <strong>{value}</strong><small>{label}</small>
              </span>)}
            </span>
            <span className="matter-feature-cta" aria-hidden="true">时间线 <span>↗</span></span>
          </span>
        </button>
        {otherMatters.length > 0 && <ul className="matter-overview-list" aria-label="更多已核对事项">
          {otherMatters.map((matter) => <li key={matter.id}>
            <MatterRow matter={matter} onClick={() => setDialogTarget({ matter })} />
          </li>)}
        </ul>}
      </div>
      <p className="matter-overview-note">{companyCount} 家自选公司 · 每一步均可打开披露原文</p>
    </> : <p className="matter-empty">首批核对样本暂未覆盖当前自选股票。</p>}
    {dialogTarget && matters.length > 0 && <MatterDialog matters={matters} initialMatter={dialogTarget.matter}
      onClose={() => setDialogTarget(undefined)} />}
  </section>;
}
