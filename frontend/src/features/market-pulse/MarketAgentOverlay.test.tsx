import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { MarketAgentOverlay } from './MarketAgentOverlay';

describe('MarketAgentOverlay', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('stays absent when the optional sidecar URL is not configured', () => {
    const { container } = render(
      <MarketAgentOverlay currentView="panorama" agentUrl="" />
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('opens a native FinScope conversation instead of an iframe', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <MarketAgentOverlay
        agentUrl="http://127.0.0.1:8765/"
        businessDate="2026-10-15"
        currentView="breadth"
        qualityStatus="PARTIAL"
        generatedAt="2026-10-15T15:40:00"
      />
    );

    await user.click(screen.getByRole('button', { name: '问市场 Agent' }));

    expect(screen.getByRole('textbox', { name: '向市场状态提问' })).toBeInTheDocument();
    expect(screen.getByText('2026-10-15 · 市场宽度')).toBeInTheDocument();
    expect(container.querySelector('iframe')).not.toBeInTheDocument();
  });

  it('sends bounded market context through the Sidecar API', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ session_id: 'market-session' }, 201))
      .mockResolvedValueOnce(jsonResponse({
        session_id: 'market-session',
        run_id: 'run-2',
        status: 'completed',
        task_outcome: 'answered',
        answer: '市场宽度处于中性区间。',
        error: null,
        completion_reason: 'completed',
        tools: [],
        clarification_id: null
      }));
    vi.stubGlobal('fetch', fetchMock);
    render(
      <MarketAgentOverlay
        agentUrl="http://127.0.0.1:8765"
        businessDate="2026-10-15"
        currentView="breadth"
        qualityStatus="PARTIAL"
        generatedAt="2026-10-15T15:40:00"
      />
    );

    await user.click(screen.getByRole('button', { name: '问市场 Agent' }));
    await user.type(screen.getByRole('textbox', { name: '向市场状态提问' }), '今天的参与度如何？');
    await user.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText('市场宽度处于中性区间。')).toBeInTheDocument();
    const [, messageOptions] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(JSON.parse(String(messageOptions.body))).toEqual(expect.objectContaining({
      text: '今天的参与度如何？',
      context: {
        schema_version: 1,
        surface: 'market_pulse',
        business_date: '2026-10-15',
        current_view: 'breadth',
        quality_status: 'PARTIAL',
        generated_at: '2026-10-15T15:40:00'
      }
    }));
  });
});

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}
