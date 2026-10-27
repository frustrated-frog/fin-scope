import { useMemo, useState } from 'react';

import { AgentConversation } from '../agent/AgentConversation';
import { parseAgentEndpoint } from '../agent/agentClient';
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

const MARKET_SUGGESTIONS = [
  '今天的市场参与度如何？',
  '当前最值得跟踪的市场转折是什么？'
] as const;

export function MarketAgentOverlay({
  businessDate,
  currentView,
  qualityStatus,
  generatedAt,
  agentUrl = import.meta.env.VITE_WEAVRIA_AGENT_URL
}: MarketAgentOverlayProps) {
  const endpoint = useMemo(() => parseAgentEndpoint(agentUrl), [agentUrl]);
  const [open, setOpen] = useState(false);
  const context = useMemo(() => ({
    schema_version: 1 as const,
    surface: 'market_pulse' as const,
    business_date: businessDate ?? null,
    current_view: currentView,
    quality_status: qualityStatus ?? null,
    generated_at: generatedAt ?? null
  }), [businessDate, currentView, qualityStatus, generatedAt]);

  if (!endpoint) {
    return null;
  }

  return (
    <aside className={`market-agent-overlay${open ? ' is-open' : ''}`} aria-label="市场状态 Agent">
      <section className="market-agent-frame" id="market-agent-frame" hidden={!open}>
        <header>
          <div><span>FIN/SCOPE</span><strong>市场状态 Agent</strong></div>
          <button type="button" onClick={() => setOpen(false)} aria-label="收起市场状态 Agent">×</button>
        </header>
        <div className="market-agent-conversation">
          <AgentConversation
            endpoint={endpoint}
            context={context}
            mode="compact"
            suggestions={MARKET_SUGGESTIONS}
          />
        </div>
      </section>
      <button
        className="market-agent-launcher"
        type="button"
        aria-label={open ? '收起市场 Agent' : '问市场 Agent'}
        aria-expanded={open}
        aria-controls="market-agent-frame"
        onClick={() => setOpen((value) => !value)}
      >
        <span aria-hidden="true"><i /><i /><i /></span>
        <strong>{open ? '收起 Agent' : '问市场 Agent'}</strong>
        <small>{businessDate ?? '最新截面'}</small>
      </button>
    </aside>
  );
}
