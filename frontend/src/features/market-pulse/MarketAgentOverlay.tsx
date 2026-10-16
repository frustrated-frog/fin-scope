import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { MarketPulseQuality } from './marketPulseTypes';
import './MarketAgentOverlay.css';

type MarketPulseViewName = 'panorama' | 'review' | 'transition' | 'breadth' | 'history';

type MarketAgentOverlayProps = {
  businessDate?: string;
  currentView: MarketPulseViewName;
  qualityStatus?: MarketPulseQuality;
  generatedAt?: string;
  agentUrl?: string;
};

type AgentMessage = {
  type?: string;
  version?: number;
};

export function MarketAgentOverlay({
  businessDate,
  currentView,
  qualityStatus,
  generatedAt,
  agentUrl = import.meta.env.VITE_WEAVRIA_AGENT_URL
}: MarketAgentOverlayProps) {
  const endpoint = useMemo(() => parseEndpoint(agentUrl), [agentUrl]);
  const frameRef = useRef<HTMLIFrameElement>(null);
  const [open, setOpen] = useState(false);
  const context = useMemo(() => ({
    schema_version: 1,
    surface: 'market_pulse',
    business_date: businessDate ?? null,
    current_view: currentView,
    quality_status: qualityStatus ?? null,
    generated_at: generatedAt ?? null
  }), [businessDate, currentView, qualityStatus, generatedAt]);

  const postContext = useCallback(() => {
    if (!endpoint || !frameRef.current?.contentWindow) {
      return;
    }
    frameRef.current.contentWindow.postMessage(
      { type: 'weavria:context', context },
      endpoint.origin
    );
  }, [context, endpoint]);

  useEffect(() => {
    if (!endpoint) {
      return undefined;
    }
    const receive = (event: MessageEvent<AgentMessage>) => {
      if (event.origin !== endpoint.origin
        || event.source !== frameRef.current?.contentWindow
        || event.data?.type !== 'weavria:ready'
        || event.data.version !== 1) {
        return;
      }
      postContext();
    };
    window.addEventListener('message', receive);
    return () => window.removeEventListener('message', receive);
  }, [endpoint, postContext]);

  useEffect(() => {
    if (open) {
      postContext();
    }
  }, [open, postContext]);

  if (!endpoint) {
    return null;
  }

  return (
    <aside className={`market-agent-overlay${open ? ' is-open' : ''}`} aria-label="市场状态 Agent">
      <section className="market-agent-frame" id="market-agent-frame" hidden={!open}>
        <header>
          <div><span>WEAVRIA LAB</span><strong>市场状态 Agent</strong></div>
          <button type="button" onClick={() => setOpen(false)} aria-label="收起市场状态 Agent">×</button>
        </header>
        <iframe
          ref={frameRef}
          src={`${endpoint.href}/widget`}
          title="市场状态 Agent 对话"
          sandbox="allow-scripts allow-same-origin"
          referrerPolicy="no-referrer"
        />
      </section>
      <button
        className="market-agent-launcher"
        type="button"
        aria-label={open ? '收起市场 Agent' : '问市场 Agent'}
        aria-expanded={open}
        aria-controls="market-agent-frame"
        onClick={() => setOpen(value => !value)}
      >
        <span aria-hidden="true"><i /><i /><i /></span>
        <strong>{open ? '收起 Agent' : '问市场 Agent'}</strong>
        <small>{businessDate ?? '最新截面'}</small>
      </button>
    </aside>
  );
}

function parseEndpoint(value?: string) {
  const normalized = value?.trim().replace(/\/+$/, '');
  if (!normalized) {
    return null;
  }
  try {
    const url = new URL(normalized);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return null;
    }
    return { href: url.href.replace(/\/+$/, ''), origin: url.origin };
  } catch {
    return null;
  }
}
