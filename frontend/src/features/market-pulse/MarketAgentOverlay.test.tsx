import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { MarketAgentOverlay } from './MarketAgentOverlay';

describe('MarketAgentOverlay', () => {
  it('stays absent when the optional sidecar URL is not configured', () => {
    const { container } = render(
      <MarketAgentOverlay currentView="panorama" agentUrl="" />
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('opens an isolated widget and sends only bounded page context', () => {
    render(
      <MarketAgentOverlay
        agentUrl="http://127.0.0.1:8765/"
        businessDate="2026-10-15"
        currentView="breadth"
        qualityStatus="PARTIAL"
        generatedAt="2026-10-15T15:40:00"
      />
    );
    fireEvent.click(screen.getByRole('button', { name: '问市场 Agent' }));
    const frame = screen.getByTitle('市场状态 Agent 对话') as HTMLIFrameElement;
    expect(frame).toHaveAttribute('src', 'http://127.0.0.1:8765/widget');
    expect(frame).toHaveAttribute('sandbox', 'allow-scripts allow-same-origin');
    const postMessage = vi.spyOn(frame.contentWindow!, 'postMessage');

    window.dispatchEvent(new MessageEvent('message', {
      origin: 'http://127.0.0.1:8765',
      source: frame.contentWindow,
      data: { type: 'weavria:ready', version: 1 }
    }));

    expect(postMessage).toHaveBeenLastCalledWith({
      type: 'weavria:context',
      context: {
        schema_version: 1,
        surface: 'market_pulse',
        business_date: '2026-10-15',
        current_view: 'breadth',
        quality_status: 'PARTIAL',
        generated_at: '2026-10-15T15:40:00'
      }
    }, 'http://127.0.0.1:8765');
  });

  it('ignores ready messages from another origin', () => {
    render(
      <MarketAgentOverlay agentUrl="http://127.0.0.1:8765" currentView="review" />
    );
    const frame = screen.getByTitle('市场状态 Agent 对话') as HTMLIFrameElement;
    const postMessage = vi.spyOn(frame.contentWindow!, 'postMessage');

    window.dispatchEvent(new MessageEvent('message', {
      origin: 'https://untrusted.example',
      source: frame.contentWindow,
      data: { type: 'weavria:ready', version: 1 }
    }));

    expect(postMessage).not.toHaveBeenCalled();
  });
});
