import { beforeEach, describe, expect, it, vi } from 'vitest';

import { parseAgentEndpoint, sendAgentMessage } from './agentClient';

describe('agentClient', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('normalizes only HTTP(S) Sidecar endpoints', () => {
    expect(parseAgentEndpoint(' https://agent.example/// ')).toEqual({ href: 'https://agent.example' });
    expect(parseAgentEndpoint('file:///tmp/agent')).toBeNull();
    expect(parseAgentEndpoint('not a url')).toBeNull();
    expect(parseAgentEndpoint()).toBeNull();
  });

  it('replaces an expired stored session and retries the message once', async () => {
    window.localStorage.setItem('weavria.finscope.sessionId', 'expired-session');
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ detail: 'session not found' }, 404))
      .mockResolvedValueOnce(jsonResponse({ session_id: 'new-session' }, 201))
      .mockResolvedValueOnce(jsonResponse(messageResult('new-session')));
    vi.stubGlobal('fetch', fetchMock);

    const result = await sendAgentMessage(
      { href: 'http://127.0.0.1:8766' },
      {
        text: '继续研究',
        context: {
          schema_version: 1,
          surface: 'finscope_workspace',
          current_view: 'agent'
        }
      }
    );

    expect(result.session_id).toBe('new-session');
    expect(window.localStorage.getItem('weavria.finscope.sessionId')).toBe('new-session');
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      'http://127.0.0.1:8766/api/sessions/expired-session/messages',
      'http://127.0.0.1:8766/api/sessions',
      'http://127.0.0.1:8766/api/sessions/new-session/messages'
    ]);
  });
});

function messageResult(sessionId: string) {
  return {
    session_id: sessionId,
    run_id: 'run-1',
    status: 'completed',
    task_outcome: 'answered',
    answer: '完成',
    error: null,
    completion_reason: 'completed',
    tools: [],
    clarification_id: null
  };
}

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}
