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

const WORKSPACE_SCOPE = ['文章', '研究', '新闻', '市场', '财务', '知识'] as const;
const WORKSPACE_SUGGESTION_LABELS = ['线索聚合', '主题补证', '公司研究'] as const;
const MARKET_SUGGESTION_LABELS = ['市场参与', '转折信号', '证据追踪'] as const;

type AnswerMarkdownNode = {
  type: string;
  value?: string;
  children?: AnswerMarkdownNode[];
};

function hideInternalSourceMarkers() {
  function visit(node: AnswerMarkdownNode): void {
    if (node.type === 'link' || node.type === 'linkReference') {
      return;
    }
    if (node.type === 'text' && node.value) {
      node.value = node.value.replace(
        /[ \t]*\[来源[ \t]*[:：][ \t]*(?:finscope:|market-pulse:)[^\]\r\n]+\]/g,
        ''
      );
    }
    node.children?.forEach(visit);
  }
  return visit;
}

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
      : '就绪';

  return (
    <section
      className={`agent-conversation agent-conversation--${mode}`}
      aria-label="FinScope Agent 对话"
    >
      <header className="agent-conversation-status">
        <div className="agent-context-identity">
          <span className={`agent-status-dot is-${connectionState}`} aria-hidden="true" />
          <span className="agent-context-copy">
            <small>{mode === 'compact' ? 'MARKET CONTEXT' : 'EVIDENCE NAVIGATOR'}</small>
            <strong>{contextLabel}</strong>
          </span>
        </div>
        <div className="agent-status-cluster">
          <span className="agent-readonly-badge">
            <svg viewBox="0 0 16 16" aria-hidden="true">
              <rect x="3.5" y="7" width="9" height="6.5" rx="2" />
              <path d="M5.5 7V5.2a2.5 2.5 0 0 1 5 0V7" />
            </svg>
            只读
          </span>
          <span className={`agent-status-label is-${connectionState}`}>{statusLabel}</span>
        </div>
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
            <div className="agent-empty-intro">
              <div className="agent-empty-visual" aria-hidden="true">
                <span className="agent-empty-mark">AI</span>
                <i className="agent-evidence-orbit agent-evidence-orbit--outer" />
                <i className="agent-evidence-orbit agent-evidence-orbit--inner" />
                <i className="agent-evidence-node agent-evidence-node--one" />
                <i className="agent-evidence-node agent-evidence-node--two" />
                <i className="agent-evidence-node agent-evidence-node--three" />
              </div>
              <div className="agent-empty-copy">
                <span>{mode === 'compact' ? 'MARKET EVIDENCE' : 'FIN//SCOPE EVIDENCE ORBIT'}</span>
                <h3>
                  {mode === 'compact'
                    ? '读取这一刻的市场证据'
                    : '从问题出发，沿证据抵达结论。'}
                </h3>
                <p>
                  {mode === 'compact'
                    ? '围绕当前日期与视图，只读检索市场状态、变化和证据边界。'
                    : '按需检索 FinScope 中的研究材料，串联事实、变化与判断，并在回答中保留证据边界。'}
                </p>
                {mode === 'workspace' ? (
                  <div className="agent-evidence-scope" aria-label="可检索范围">
                    {WORKSPACE_SCOPE.map((item) => <span key={item}>{item}</span>)}
                  </div>
                ) : null}
              </div>
            </div>
            <div className="agent-suggestions" aria-label="建议问题">
              {suggestions.map((suggestion, index) => (
                <button type="button" key={suggestion} onClick={() => chooseSuggestion(suggestion)}>
                  <small>{presentSuggestionLabel(mode, index)}</small>
                  <strong>{suggestion}</strong>
                  <span aria-hidden="true">↗</span>
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
                  remarkPlugins={[remarkGfm, hideInternalSourceMarkers]}
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
        <div className="agent-composer-heading">
          <label htmlFor={`agent-question-${mode}`}>
            {mode === 'compact' ? '向市场状态提问' : '向整个研究工作区提问'}
          </label>
          <span>Enter 发送 · Shift + Enter 换行</span>
        </div>
        <div className="agent-composer-shell">
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
            <span>{busy ? '分析中' : '发送'}</span>
            <svg viewBox="0 0 18 18" aria-hidden="true">
              <path d="M3 9h11M10 5l4 4-4 4" />
            </svg>
          </button>
        </div>
        <small>
          <svg viewBox="0 0 16 16" aria-hidden="true">
            <rect x="3.5" y="7" width="9" height="6.5" rx="2" />
            <path d="M5.5 7V5.2a2.5 2.5 0 0 1 5 0V7" />
          </svg>
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

function presentSuggestionLabel(mode: 'workspace' | 'compact', index: number): string {
  const labels = mode === 'compact' ? MARKET_SUGGESTION_LABELS : WORKSPACE_SUGGESTION_LABELS;
  return labels[index] ?? '研究路径';
}
