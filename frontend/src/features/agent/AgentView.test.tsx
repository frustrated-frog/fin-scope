import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AgentView } from './AgentView';

describe('AgentView', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('shows setup guidance when the Agent URL is absent', () => {
    render(<AgentView agentUrl="" />);

    expect(screen.getByRole('heading', { name: '配置 FinScope Agent' })).toBeInTheDocument();
    expect(screen.getByText('VITE_WEAVRIA_AGENT_URL=http://127.0.0.1:8765')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('rejects a configured URL outside HTTP(S)', () => {
    render(<AgentView agentUrl="javascript:alert(1)" />);

    expect(screen.getByRole('heading', { name: 'Agent 地址无效' })).toBeInTheDocument();
    expect(screen.getByText(/HTTP\(S\) 地址/)).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('renders a native FinScope conversation instead of an embedded page', () => {
    const { container } = render(<AgentView agentUrl="http://127.0.0.1:8765/" />);

    expect(screen.getByRole('region', { name: 'FinScope Agent 对话' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '向整个研究工作区提问' })).toBeInTheDocument();
    expect(screen.getByText('从问题出发，沿证据抵达结论。')).toBeInTheDocument();
    expect(screen.getByLabelText('可检索范围')).toHaveTextContent('文章研究新闻市场财务知识');
    expect(container.querySelector('.agent-space-field')).toBeInTheDocument();
    expect(container.querySelector('iframe')).not.toBeInTheDocument();
  });

  it('creates a session and sends workspace context through the Sidecar API', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ session_id: 'session-1' }, 201))
      .mockResolvedValueOnce(jsonResponse({
        session_id: 'session-1',
        run_id: 'run-1',
        status: 'completed',
        task_outcome: 'answered',
        answer: '**结论**：已有两条可验证线索。',
        error: null,
        completion_reason: 'completed',
        tools: [{ call_id: 'call-1', tool_name: 'finscope.research.list', status: 'succeeded' }],
        clarification_id: null
      }));
    vi.stubGlobal('fetch', fetchMock);
    render(<AgentView agentUrl="https://agent.example/" />);

    await user.type(
      screen.getByRole('textbox', { name: '向整个研究工作区提问' }),
      '概括最近的研究线索'
    );
    await user.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText(/已有两条可验证线索/)).toBeInTheDocument();
    expect(screen.getByText('finscope.research.list · 完成')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenNthCalledWith(1, 'https://agent.example/api/sessions', expect.objectContaining({
      method: 'POST'
    }));
    const [, messageOptions] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(fetchMock.mock.calls[1][0]).toBe('https://agent.example/api/sessions/session-1/messages');
    expect(JSON.parse(String(messageOptions.body))).toEqual(expect.objectContaining({
      text: '概括最近的研究线索',
      clarification_id: null,
      context: {
        schema_version: 1,
        surface: 'finscope_workspace',
        current_view: 'agent',
        navigation: {
          view: 'agent',
          label: 'Agent',
          hint: '全域问答'
        }
      }
    }));
  });

  it('shows a Sidecar error inside the native conversation', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse({ session_id: 'session-1' }, 201))
      .mockResolvedValueOnce(jsonResponse({ detail: '上游模型暂不可用' }, 502)));
    render(<AgentView agentUrl="https://agent.example" />);

    await user.type(screen.getByRole('textbox'), '检查连接');
    await user.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText('任务未完成：上游模型暂不可用')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('上次运行失败')).toBeInTheDocument());
  });
});

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}
