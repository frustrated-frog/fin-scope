import { FormEvent, KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

import {
  AgentApiError,
  AgentEndpoint,
  AgentPageContext,
  AgentToolTrace,
  sendAgentMessage
} from './agentClient';
import './AgentConversation.css';

type AgentConversationProps = {
  endpoint: AgentEndpoint;
  context: AgentPageContext;
  mode?: 'workspace' | 'compact';
  suggestions?: readonly string[];
};

type ConversationMessage = {
  id: number;
  role: 'user' | 'agent';
  content: string;
  tone?: 'error' | 'warning';
  tools?: AgentToolTrace[];
  clarificationId?: string | null;
};

const WORKSPACE_SUGGESTIONS = [
  '概括最近进入工作区的文章与研究线索',
  '结合自选、新闻和证据，哪些主题最值得继续补证？',
  '查找一家公司的财务、产业链与已有研究材料'
] as const;

export function AgentConversation({
  endpoint,
  context,
  mode = 'workspace',
  suggestions = WORKSPACE_SUGGESTIONS
}: AgentConversationProps) {
  const [draft, setDraft] = useState('');
  const [messages, setMessages] = useState<ConversationMessage[]>([]);
  const [busy, setBusy] = useState(false);
  const [connectionState, setConnectionState] = useState<'ready' | 'busy' | 'error'>('ready');
  const [activeClarificationId, setActiveClarificationId] = useState<string | null>(null);
  const nextMessageId = useRef(1);
  const abortRef = useRef<AbortController | null>(null);
  const questionRef = useRef<HTMLTextAreaElement>(null);
  const conversationRef = useRef<HTMLDivElement>(null);
  const contextLabel = useMemo(() => presentContext(context), [context]);

  useEffect(() => () => abortRef.current?.abort(), []);

  useEffect(() => {
    const target = conversationRef.current;
    if (target) {
      target.scrollTop = target.scrollHeight;
    }
  }, [busy, messages]);

  async function submitQuestion(event?: FormEvent) {
    event?.preventDefault();
    const text = draft.trim();
    if (!text || busy) {
      return;
    }
    const userMessage = createMessage('user', text);
    setMessages((current) => [...current, userMessage]);
    setDraft('');
    setBusy(true);
    setConnectionState('busy');
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const result = await sendAgentMessage(endpoint, {
        text,
        context,
        clarificationId: activeClarificationId,
        signal: controller.signal
      });
      if (controller.signal.aborted) {
        return;
      }
      setActiveClarificationId(null);
      const error = result.error?.trim();
      const answer = result.answer?.trim();
      const content = answer
        || (error ? `任务未完成：${error}` : result.completion_reason?.trim())
        || '本次运行没有返回可展示的回答。';
      const tone = error
        ? 'error'
        : result.task_outcome === 'unverified'
          ? 'warning'
          : undefined;
      setMessages((current) => [...current, createMessage(
        'agent',
        content,
        tone,
        result.tools,
        result.clarification_id
      )]);
      setConnectionState(error ? 'error' : 'ready');
    } catch (error) {
      if (controller.signal.aborted) {
        return;
      }
      const detail = error instanceof AgentApiError
        ? error.message
        : 'Agent 请求失败，请稍后重试';
      setMessages((current) => [...current, createMessage(
        'agent',
        `任务未完成：${detail}`,
        'error'
      )]);
      setConnectionState('error');
    } finally {
      if (!controller.signal.aborted) {
        setBusy(false);
      }
      if (abortRef.current === controller) {
        abortRef.current = null;
      }
    }
  }

  function createMessage(
    role: ConversationMessage['role'],
    content: string,
    tone?: ConversationMessage['tone'],
    tools?: AgentToolTrace[],
    clarificationId?: string | null
  ): ConversationMessage {
    const message = {
      id: nextMessageId.current,
      role,
      content,
      tone,
      tools,
      clarificationId
    };
    nextMessageId.current += 1;
    return message;
  }

  function chooseSuggestion(suggestion: string) {
    setDraft(suggestion);
    questionRef.current?.focus();
  }

  function answerClarification(clarificationId: string) {
    setActiveClarificationId(clarificationId);
    questionRef.current?.focus();
  }

  function handleQuestionKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void submitQuestion();
    }
  }

  const statusLabel = connectionState === 'busy'
    ? '正在分析'
    : connectionState === 'error'
      ? '上次运行失败'
      : '只读就绪';

  return (
    <section
      className={`agent-conversation agent-conversation--${mode}`}
      aria-label="FinScope Agent 对话"
    >
      <header className="agent-conversation-status">
        <div>
          <span className={`agent-status-dot is-${connectionState}`} aria-hidden="true" />
          <strong>{contextLabel}</strong>
        </div>
        <span className={`agent-status-label is-${connectionState}`}>{statusLabel}</span>
      </header>

      <div
        className="agent-conversation-log"
        ref={conversationRef}
        role="log"
        aria-live="polite"
        aria-busy={busy}
      >
        {messages.length === 0 ? (
          <div className="agent-empty-state">
            <span className="agent-empty-mark" aria-hidden="true">AI</span>
            <div>
              <h3>{mode === 'compact' ? '询问当前市场状态' : '从一个研究问题开始'}</h3>
              <p>
                {mode === 'compact'
                  ? 'Agent 会按当前日期和视图读取只读证据。'
                  : 'Agent 会按需读取 FinScope 中的文章、研究、市场与知识数据，并明确证据边界。'}
              </p>
            </div>
            <div className="agent-suggestions" aria-label="建议问题">
              {suggestions.map((suggestion) => (
                <button type="button" key={suggestion} onClick={() => chooseSuggestion(suggestion)}>
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        ) : null}

        {messages.map((message) => (
          <article
            className={`agent-message is-${message.role}${message.tone ? ` is-${message.tone}` : ''}`}
            key={message.id}
          >
            <span>{message.role === 'user' ? '你的问题' : 'Agent 回答'}</span>
            {message.role === 'agent' ? (
              <div className="agent-message-markdown">
                <ReactMarkdown
                  remarkPlugins={[remarkGfm]}
                  components={{
                    a: ({ children, href }) => (
                      <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
                    )
                  }}
                >
                  {message.content}
                </ReactMarkdown>
              </div>
            ) : <p>{message.content}</p>}
            {message.tools?.length ? (
              <div className="agent-tool-trace" aria-label="本次只读工具调用">
                {message.tools.map((tool) => (
                  <span className={`is-${tool.status}`} key={tool.call_id}>
                    {tool.tool_name} · {tool.status === 'succeeded' ? '完成' : '失败'}
                  </span>
                ))}
              </div>
            ) : null}
            {message.clarificationId ? (
              <button
                className="agent-clarification-action"
                type="button"
                onClick={() => answerClarification(message.clarificationId!)}
              >
                回答这个追问
              </button>
            ) : null}
          </article>
        ))}

        {busy ? (
          <article className="agent-message is-agent is-pending" aria-label="Agent 正在分析">
            <span>Agent 回答</span>
            <p><i /><i /><i />正在读取相关证据并组织回答</p>
          </article>
        ) : null}
      </div>

      <form className="agent-composer" onSubmit={submitQuestion}>
        <label htmlFor={`agent-question-${mode}`}>
          {mode === 'compact' ? '向市场状态提问' : '向整个研究工作区提问'}
        </label>
        <div>
          <textarea
            id={`agent-question-${mode}`}
            ref={questionRef}
            value={draft}
            disabled={busy}
            maxLength={4000}
            rows={mode === 'compact' ? 2 : 3}
            placeholder={mode === 'compact'
              ? '例如：今天的市场参与度如何？'
              : '例如：最近有哪些证据正在改变我对某个主题的判断？'}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={handleQuestionKeyDown}
          />
          <button type="submit" disabled={busy || !draft.trim()}>
            {busy ? '分析中' : '发送'}
          </button>
        </div>
        <small>
          {activeClarificationId ? '本次输入将作为上一条追问的回答。' : '只读研究 · 不写入、不刷新、不生成业务数据'}
        </small>
      </form>
    </section>
  );
}

function presentContext(context: AgentPageContext): string {
  if (context.surface === 'market_pulse') {
    return `${context.business_date ?? '最新交易日'} · ${presentMarketView(context.current_view)}`;
  }
  const label = context.navigation?.label ?? 'Agent';
  return context.navigation?.hint ? `${label} · ${context.navigation.hint}` : label;
}

function presentMarketView(view: string): string {
  const labels: Record<string, string> = {
    panorama: '市场全景',
    review: '今日雷达',
    transition: '转折与情景',
    breadth: '市场宽度',
    history: '历史演变'
  };
  return labels[view] ?? '市场状态';
}
