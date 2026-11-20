import { useRef, useState } from 'react';
import type { MajorEvent } from '../../shared/types';
import { sourceHref } from '../investment-observation/reactionTypes';
import { DossierTabs, NewsDossierDialog } from '../news/NewsDossierDialog';
import { NewsInterpretationPanel, NewsReadingAction } from '../news/NewsInterpretationPanel';
import { useNewsInterpretation } from '../news/useNewsReportInterpretation';

const tabs = [['interpretation', '解读'], ['source', '归档原文']] as const;

export function MajorEventReadingDialog({ event, onClose }: { event: MajorEvent; onClose: () => void }) {
  const reading = useNewsInterpretation(`/api/major-events/${event.id}/interpretation`);
  const [tab, setTab] = useState<'interpretation' | 'source'>('interpretation');
  const section = useRef<HTMLElement>(null);
  const href = sourceHref(event.sourceUrl);
  return <NewsDossierDialog windowed title={event.title} recordId={event.id} closeLabel="关闭新闻解读" onClose={onClose}
    meta={<>
      <span>大事记</span>{event.sourceName && <span>{event.sourceName}</span>}
      <span>发生于 <time dateTime={event.occurredDate}>{event.occurredDate}</time></span>
    </>}
    railLabel="归档信息与操作" rail={<>
      <div className="radar-dossier-clues">
        <div className="radar-rail-label">归档信息</div>
        <ul><li>{event.originType === 'RADAR_EVENT' ? '雷达事件' : '实时资讯'}</li>
          <li>记入于 <time dateTime={event.createdAt}>{event.createdAt.slice(0, 10)}</time></li>
        </ul>
      </div>
      <div className="radar-dossier-actions">
        <NewsReadingAction interpretation={reading} section={section} onShow={() => setTab('interpretation')}/>
        {href && <div className="dossier-secondary-actions"><a href={href} target="_blank" rel="noreferrer">打开原始来源 ↗</a></div>}
      </div>
    </>}>
    <DossierTabs label="大事记详情栏目" tabs={tabs} current={tab} onSelect={setTab}/>
    {event.summary && <p className="radar-detail-overview dossier-news-text">{event.summary}</p>}
    {tab === 'interpretation' ? <NewsInterpretationPanel interpretation={reading} heading="新闻 AI 解读" section={section} expandPrevious/>
      : <>
        {!event.summary && <p className="radar-dossier-notice">此条归档仅保存了标题。</p>}
        {event.note && <section className="radar-detail-section">
          <div className="radar-detail-section-heading"><h3>当时判断</h3></div>
          <p className="dossier-news-text">{event.note}</p>
        </section>}
      </>}
  </NewsDossierDialog>;
}
