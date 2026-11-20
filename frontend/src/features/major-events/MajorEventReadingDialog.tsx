import { useEffect, useRef } from 'react';
import type { MajorEvent } from '../../shared/types';
import { NewsInterpretationPanel } from '../news/NewsInterpretationPanel';
import { useNewsInterpretation } from '../news/useNewsReportInterpretation';
import '../news/newsWindow.css';

export function MajorEventReadingDialog({ event, onClose }: { event: MajorEvent; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const reading = useNewsInterpretation(`/api/major-events/${event.id}/interpretation`);
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

  return <dialog ref={dialog} className="news-report-dialog" aria-labelledby="archive-reading-title" onCancel={onClose}
    onClick={(click) => {
      if (click.target === click.currentTarget) {
        onClose();
      }
    }}>
    <div className="news-report-sheet">
      <header>
        <div><span>{[event.sourceName, event.occurredDate].filter(Boolean).join(' · ')}</span><h2 id="archive-reading-title">{event.title}</h2></div>
        <button type="button" aria-label="关闭新闻解读" onClick={onClose}>×</button>
      </header>
      <div className="news-report-scroll">
        {event.summary && <p className="news-report-body">{event.summary}</p>}
        <NewsInterpretationPanel interpretation={reading} heading="新闻 AI 解读" expandPrevious />
      </div>
    </div>
  </dialog>;
}
