import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AgentView } from './AgentView';

describe('AgentView', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([])));
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
    installSidecar(fetchMock);
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
    expect(fetchMock.mock.calls[1][0]).toBe('https://agent.example/api/sessions/session-1/requests');
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

  it('hides internal source markers in answers while preserving Markdown and failed tool traces', async () => {
    const user = userEvent.setup();
    installSidecar(vi.fn()
      .mockResolvedValueOnce(jsonResponse({ session_id: 'session-1' }, 201))
      .mockResolvedValueOnce(jsonResponse({
        session_id: 'session-1',
        run_id: 'run-1',
        status: 'completed',
        task_outcome: 'unverifiable',
        answer: '## 数据汇总\n\n已确认 **公司身份** [来源:finscope:search:reaction_events:abc]。\n\n'
          + '| 指标 | 值 |\n| --- | --- |\n| 行情 | 48.08 [来源：market-pulse:2026-10-27] |\n\n'
          + '[查看报道](https://example.org/report)\n\n代码示例：`[来源:finscope:template]`',
        error: null,
        completion_reason: 'finalization_incomplete_evidence',
        tools: [{ call_id: 'call-1', tool_name: 'read_finscope_resource', status: 'failed' }],
        clarification_id: null
      })));
    const { container } = render(<AgentView agentUrl="https://agent.example" />);

    const question = '请汇总数据，保留这个提问文本 [来源:finscope:question]';
    await user.type(screen.getByRole('textbox'), question.replace(/\[/g, '[['));
    await user.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByRole('heading', { name: '数据汇总' })).toBeInTheDocument();
    const answer = container.querySelector('.agent-message-markdown');
    expect(answer).not.toHaveTextContent('finscope:search:reaction_events:abc');
    expect(answer).not.toHaveTextContent('market-pulse:2026-10-27');
    expect(answer?.querySelector('strong')).toHaveTextContent('公司身份');
    expect(screen.getByRole('cell', { name: '48.08' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看报道' })).toHaveAttribute('href', 'https://example.org/report');
    expect(answer?.querySelector('code')).toHaveTextContent('[来源:finscope:template]');
    expect(screen.getByText(question)).toBeInTheDocument();
    expect(screen.getByText('read_finscope_resource · 失败')).toBeInTheDocument();
  });

  it('shows a Sidecar error inside the native conversation', async () => {
    const user = userEvent.setup();
    installSidecar(vi.fn()
      .mockResolvedValueOnce(jsonResponse({ session_id: 'session-1' }, 201))
      .mockResolvedValueOnce(jsonResponse({ detail: '上游模型暂不可用' }, 502)));
    render(<AgentView agentUrl="https://agent.example" />);

    await user.type(screen.getByRole('textbox'), '检查连接');
    await user.click(screen.getByRole('button', { name: '发送' }));

    expect(await screen.findByText('上游模型暂不可用')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('上次运行失败')).toBeInTheDocument());
  });
});

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

// 独立模拟 Sidecar 的 GET 投影与异步 POST, 保留原测试对消息内容和 Markdown 的断言。
function installSidecar(posts: ReturnType<typeof vi.fn>) {
  let lastText = '', lastInput = '', result: Record<string, unknown> | null = null;
  vi.stubGlobal('fetch', vi.fn(async (url: string, options?: RequestInit) => {
    if (options?.method === 'POST') {
      const response: Response = await posts(url, options);
      if (!url.endsWith('/requests') || !response.ok) {
        return response;
      }
      const input = JSON.parse(String(options.body));
      lastText = input.text; lastInput = input.input_id;
      result = await response.json();
      return jsonResponse({ input_id: lastInput, status: 'finished', result, tools: [], phase: '完成' }, 202);
    }
    if (url.endsWith('/history')) {
      return jsonResponse({ turns: [], requests: result ? [{
      input_id: lastInput, text: lastText, status: 'finished', result
    }] : [], before: null });
    }
    return jsonResponse([]);
  }));
}

describe('Agent execution and history', () => {
  beforeEach(() => { localStorage.clear(); });
  it('restores prior messages and opens the actual stored evidence without rerunning a tool', async () => {
    localStorage.setItem('weavria.finscope.sessionId:https://agent.example', 'session-old');
    const source = { call_id: 'read-1', run_id: 'run-old', label: '澳弘电子资金复盘', href: '?agentView=marketIntel&instrument=40&replayDate=2026-11-12&replayFrom=14%3A16&replayTo=14%3A20' };
    const result = { session_id: 'session-old', run_id: 'run-old', status: 'completed', task_outcome: 'unverified', answer: '这里是旧回答。', error: null, completion_reason: null, tools: [], clarification_id: null, sources: [source] };
    const fetchMock = vi.fn(async (url: string) => {
      if (url.endsWith('/sources/run-old/read-1')) {
        return jsonResponse({ data: { date: '2026-11-12', largeNet: 10000 } });
      }
      if (url.endsWith('/history')) {
        return jsonResponse({ turns: [], requests: [{ input_id: 'old', text: '分析澳弘电子', status: 'finished', result }], before: null });
      }
      return jsonResponse([{ session_id: 'session-old', title: '资金复盘', updated_at: '' }]);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<AgentView agentUrl="https://agent.example" />);
    expect(await screen.findByText('这里是旧回答。')).toBeInTheDocument();
    expect(screen.getByText('分析澳弘电子')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /澳弘电子资金复盘/ }));
    expect(await screen.findByRole('dialog', { name: '证据详情' })).toBeInTheDocument();
    expect(await screen.findByText('10000')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '在业务页面查看' })).toHaveAttribute('href', source.href);
    await userEvent.click(screen.getByRole('button', { name: '关闭证据' }));
    await userEvent.click(screen.getByRole('button', { name: '新建对话' }));
    expect(screen.queryByText('这里是旧回答。')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/requests'))).toBe(false);
  });

  it('reattaches to an active request and sends cancellation to the server', async () => {
    localStorage.setItem('weavria.finscope.sessionId:https://agent.example', 'session-active');
    let stopped = false;
    const fetchMock = vi.fn(async (url: string) => {
      if (url.endsWith('/history')) {
        return jsonResponse({ turns: [], requests: [{ input_id: 'pending', text: '分析资金', status: 'running', result: null }], before: null });
      }
      if (url.includes('/requests/pending')) {
        if (url.endsWith('/cancel')) {
          stopped = true;
        }
        return jsonResponse({ input_id: 'pending', status: stopped ? 'cancelled' : 'running', phase: '正在读取财报', tools: [{ call_id: 'r', tool_name: 'read_finscope_resource', status: 'running' }], result: null });
      }
      return jsonResponse([]);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<AgentView agentUrl="https://agent.example" />);
    expect(await screen.findByText('正在读取财报')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '停止运行' }));
    expect(await screen.findByText('运行已停止。', {}, { timeout: 2000 })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/requests/pending/cancel'))).toBe(true);
    expect(screen.getByRole('textbox')).toBeEnabled();
  });
});
