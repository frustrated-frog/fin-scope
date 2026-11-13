import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

import {
  AgentApiError, AgentEndpoint, AgentPageContext, AgentSession, AgentSource, AgentJob,
  selectedAgentSession, selectAgentSession, newAgentSession, listAgentSessions, loadAgentHistory,
  newAgentInputId, startAgentRequest, readAgentRequest, cancelAgentRequest, readAgentSource
} from './agentClient';
import { agentAnswer, historyMessages, ConversationMessage } from './agentHistory';
import { AgentEvidence } from './AgentEvidence';
import './AgentConversation.css';

type AgentConversationProps = {
  endpoint: AgentEndpoint;
  context: AgentPageContext;
};

const WORKSPACE_SUGGESTIONS = [
  { label: '线索聚合', text: '概括最近进入工作区的文章与研究线索' },
  { label: '主题补证', text: '结合自选、新闻和证据，哪些主题最值得继续补证？' },
  { label: '公司研究', text: '查找一家公司的财务、产业链与已有研究材料' }
] as const;

const WORKSPACE_SCOPE = ['文章', '研究', '新闻', '市场', '财务', '知识'] as const;

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

export function AgentConversation({ endpoint, context }: AgentConversationProps) {
  const [draft, setDraft] = useState('');
  const [messages, setMessages] = useState<ConversationMessage[]>([]);
  const [busy, setBusy] = useState(false);
  const [connectionState, setConnectionState] = useState<'ready' | 'busy' | 'error'>('ready');
  const [activeClarificationId, setActiveClarificationId] = useState<string | null>(null);
  const [sessions, setSessions] = useState<AgentSession[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(() => selectedAgentSession(endpoint));
  const [restoring, setRestoring] = useState(false);
  const [historyRevision, setHistoryRevision] = useState(0);
  const [before, setBefore] = useState<number | null>(null);
  const [phase, setPhase] = useState('正在理解问题');
  const [job, setJob] = useState<AgentJob | null>(null);
  const [source, setSource] = useState<AgentSource | null>(null);
  const [sourceData, setSourceData] = useState<unknown>(null);
  const [sourceError, setSourceError] = useState('');
  const sourceRequest = useRef<AbortController | null>(null);
  const activeRequest = useRef<{ session: string; input: string } | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const questionRef = useRef<HTMLTextAreaElement>(null);
  const conversationRef = useRef<HTMLDivElement>(null);
  const { label = 'Agent', hint } = context.navigation ?? {};
  const contextLabel = hint ? `${label} · ${hint}` : label;

  useEffect(() => () => { abortRef.current?.abort(); sourceRequest.current?.abort(); }, []);

  // 离开页面只停止轮询; 服务端继续持有请求, 再进入时从历史恢复并接着查看进度。
  useEffect(() => {
    const controller = new AbortController();
    abortRef.current?.abort();
    abortRef.current = controller;
    setRestoring(true);
    setMessages([]);
    setBusy(false);
    setBefore(null);
    setActiveClarificationId(null);
    void listAgentSessions(endpoint, controller.signal).then(setSessions).catch(() => {});
    if (!sessionId) { setRestoring(false); return () => controller.abort(); }
    void loadAgentHistory(endpoint, sessionId, controller.signal).then((history) => {
      if (controller.signal.aborted) {
        return;
      }
      const restored = historyMessages(history);
      setMessages(restored);
      setBefore(history.before);
      setActiveClarificationId(restored[restored.length - 1]?.clarificationId ?? null);
      const pending = history.requests.find((item) => item.status === 'running' || item.status === 'cancelling');
      if (pending) {
        void watchRequest(sessionId, pending.input_id, controller);
      }
    }).catch((error) => {
      if (controller.signal.aborted) {
        return;
      }
      if (error instanceof AgentApiError && error.status === 404) {
        selectAgentSession(endpoint, null); setSessionId(null);
      } else {
        setMessages([{ id: 'restore-error', role: 'agent', content: '历史对话读取失败，请重新选择会话重试。', tone: 'error' }]);
      }
    }).finally(() => { if (!controller.signal.aborted) {
      setRestoring(false);
    } });
    return () => controller.abort();
  }, [endpoint.href, sessionId, historyRevision]);

  useEffect(() => {
    conversationRef.current?.scrollTo?.({ top: conversationRef.current.scrollHeight });
  }, [busy, messages, phase]);

  async function watchRequest(id: string, inputId: string, controller: AbortController, initial?: AgentJob) {
    activeRequest.current = { session: id, input: inputId };
    setBusy(true);
    setConnectionState('busy');
    try {
      let current = initial;
      while (!controller.signal.aborted) {
        current = current ?? await readAgentRequest(endpoint, id, inputId, controller.signal);
        if (controller.signal.aborted) {
          return;
        }
        setJob(current); setPhase(current.phase);
        if (current.status !== 'running' && current.status !== 'cancelling') {
          const answer: ConversationMessage = current.result ? agentAnswer(inputId, current.result) : {
            id: `${inputId}:agent`, role: 'agent', content: current.status === 'failed' ? '本次运行失败，请重试。' : '运行已停止。',
            tone: current.status === 'failed' ? 'error' : undefined
          };
          setMessages((items) => [...items.filter((item) => item.id !== answer.id), answer]);
          setActiveClarificationId(answer.clarificationId ?? null);
          setConnectionState(current.status === 'failed' ? 'error' : 'ready');
          void listAgentSessions(endpoint).then(setSessions).catch(() => {});
          return;
        }
        await new Promise<void>((resolve) => {
          const done = () => { clearTimeout(timer); controller.signal.removeEventListener('abort', done); resolve(); };
          const timer = setTimeout(done, 800);
          controller.signal.addEventListener('abort', done, { once: true });
        });
        current = undefined;
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        setMessages((items) => [...items, { id: `${inputId}:poll-error`, role: 'agent',
          content: `执行状态读取失败，任务可能仍在运行。重新进入此会话可恢复。${error instanceof Error ? error.message : ''}`, tone: 'error' }]);
        setConnectionState('error');
      }
    } finally {
      if (!controller.signal.aborted) { setBusy(false); activeRequest.current = null; setJob(null); }
    }
  }

  async function submitQuestion(event?: FormEvent) {
    event?.preventDefault();
    const text = draft.trim();
    if (!text || busy || restoring) {
      return;
    }
    setBusy(true);
    const inputId = newAgentInputId();
    let id = sessionId;
    try {
      if (!id) {
        id = await newAgentSession(endpoint);
        // 首条输入由下面这次请求负责, 不触发一次新的历史读取覆盖乐观消息。
        selectAgentSession(endpoint, id);
      }
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      setMessages((items) => [...items, { id: `${inputId}:user`, role: 'user', content: text }]);
      setDraft('');
      activeRequest.current = { session: id, input: inputId };
      setPhase('正在理解问题'); setConnectionState('busy');
      const current = await startAgentRequest(endpoint, id, inputId, { text, context,
        clarificationId: activeClarificationId, signal: controller.signal });
      await watchRequest(id, inputId, controller, current);
      // 完成后再选择新会话, 历史读取会恢复服务端已保存的消息。
      if (sessionId !== id && !controller.signal.aborted) {
        setSessionId(id);
      }
    } catch (error) {
      if (!abortRef.current?.signal.aborted) {
        setMessages((items) => [...items, { id: `${inputId}:error`, role: 'agent', tone: 'error',
          content: error instanceof Error ? error.message : 'Agent 请求失败，请重试' }]);
        setBusy(false); setConnectionState('error');
      }
    }
  }

  async function stopRequest() {
    const active = activeRequest.current;
    if (!active) {
      return;
    }
    setPhase('正在停止并保存执行状态');
    try { await cancelAgentRequest(endpoint, active.session, active.input); }
    catch { setPhase('停止请求未成功，请重试'); }
  }

  async function showSource(item: AgentSource) {
    const id = sessionId ?? selectedAgentSession(endpoint);
    if (!id) {
      return;
    }
    sourceRequest.current?.abort();
    const controller = new AbortController();
    sourceRequest.current = controller;
    setSource(item); setSourceData(null); setSourceError('');
    try {
      const data = await readAgentSource(endpoint, id, item, controller.signal);
      if (!controller.signal.aborted) {
        setSourceData(data);
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        setSourceError(error instanceof Error ? error.message : '证据读取失败');
      }
    }
  }

  async function loadEarlier() {
    if (!sessionId || !before) {
      return;
    }
    setRestoring(true);
    try {
      const history = await loadAgentHistory(endpoint, sessionId, undefined, before);
      setMessages((items) => {
        const known = new Set(items.map((item) => item.id));
        return [...historyMessages({ ...history, requests: [] }).filter((item) => !known.has(item.id)), ...items];
      });
      setBefore(history.before);
    } catch {
      setMessages((items) => [...items, { id: 'history-error', role: 'agent',
        content: '更早的消息读取失败，请重试。', tone: 'error' }]);
    } finally { setRestoring(false); }
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
      className="agent-conversation agent-conversation--workspace"
      aria-label="FinScope Agent 对话"
    >
      <header className="agent-conversation-status">
        <div className="agent-context-identity">
          <span className={`agent-status-dot is-${connectionState}`} aria-hidden="true" />
          <span className="agent-context-copy">
            <small>EVIDENCE NAVIGATOR</small>
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

      <div className="agent-session-controls">
        <label>历史对话 <select aria-label="历史对话" disabled={busy || restoring} value={sessionId ?? ''}
          onChange={(event) => { const id = event.target.value || null; selectAgentSession(endpoint, id); setSessionId(id); }}>
          <option value="">新对话</option>
          {sessionId && !sessions.some((item) => item.session_id === sessionId) && <option value={sessionId}>当前对话</option>}
          {sessions.map((item) => <option key={item.session_id} value={item.session_id}>{item.title}</option>)}
        </select></label>
        <button type="button" disabled={busy || restoring} onClick={() => {
          selectAgentSession(endpoint, null); setSessionId(null); setMessages([]); setActiveClarificationId(null); setDraft('');
        }}>新建对话</button>
        {!busy && <button type="button" disabled={restoring} onClick={() => setHistoryRevision((value) => value + 1)}>重新连接</button>}
        {busy && <button type="button" onClick={() => void stopRequest()}>停止运行</button>}
      </div>
      {restoring && <p role="status">正在恢复对话…</p>}
      {before && <button type="button" disabled={restoring || busy} onClick={() => void loadEarlier()}>加载更早的消息</button>}
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
                <span>FIN//SCOPE EVIDENCE ORBIT</span>
                <h3>从问题出发，沿证据抵达结论。</h3>
                <p>按需检索 FinScope 中的研究材料，串联事实、变化与判断，并在回答中保留证据边界。</p>
                <div className="agent-evidence-scope" aria-label="可检索范围">
                  {WORKSPACE_SCOPE.map((item) => <span key={item}>{item}</span>)}
                </div>
              </div>
            </div>
            <div className="agent-suggestions" aria-label="建议问题">
              {WORKSPACE_SUGGESTIONS.map(({ label, text }) => (
                <button type="button" key={text} onClick={() => chooseSuggestion(text)}>
                  <small>{label}</small>
                  <strong>{text}</strong>
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
            {message.sources?.length ? <div className="agent-source-cards" aria-label="本次读取的证据">
              {message.sources.map((item) => <button type="button" key={`${item.run_id}:${item.call_id}`} onClick={() => void showSource(item)}>
                <small>查看证据</small><strong>{item.label}</strong>
              </button>)}
            </div> : null}
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
            <p role="status"><i /><i /><i />{phase}</p>
            {job?.tools.map((tool) => <small key={tool.call_id}>{tool.tool_name} · {tool.status === 'running' ? '读取中' : tool.status === 'succeeded' ? '完成' : '失败'} </small>)}
          </article>
        ) : null}
      </div>

      {source && <AgentEvidence source={source} data={sourceData} error={sourceError} onClose={() => setSource(null)} />}
      <form className="agent-composer" onSubmit={submitQuestion}>
        <div className="agent-composer-heading">
          <label htmlFor="agent-question-workspace">向整个研究工作区提问</label>
          <span>Enter 发送 · Shift + Enter 换行</span>
        </div>
        <div className="agent-composer-shell">
          <textarea
            id="agent-question-workspace"
            ref={questionRef}
            value={draft}
            disabled={busy || restoring}
            maxLength={4000}
            rows={3}
            placeholder="例如：最近有哪些证据正在改变我对某个主题的判断？"
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={handleQuestionKeyDown}
          />
          <button type="submit" disabled={busy || restoring || !draft.trim()}>
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
