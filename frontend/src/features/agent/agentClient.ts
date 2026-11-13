export type AgentEndpoint = {
  href: string;
};

export type AgentPageContext = {
  schema_version: 1;
  surface: 'finscope_workspace';
  current_view: string;
  navigation?: {
    view: string;
    label: string;
    hint?: string;
  };
};

export type AgentToolTrace = {
  call_id: string;
  tool_name: string;
  status: 'succeeded' | 'failed';
};

export type AgentMessageResult = {
  session_id: string;
  run_id: string;
  status: string;
  task_outcome: string;
  answer: string | null;
  error: string | null;
  completion_reason: string | null;
  tools: AgentToolTrace[];
  clarification_id: string | null;
  sources?: AgentSource[];
};

type SendAgentMessageInput = {
  text: string;
  context: AgentPageContext;
  clarificationId?: string | null;
  signal?: AbortSignal;
};

type JsonRecord = Record<string, unknown>;

const SESSION_STORAGE_KEY = 'weavria.finscope.sessionId';
const LEGACY_SESSION_STORAGE_KEYS = [
  'weavria.marketPulse.sessionId',
  'weavria.finscopeWorkspace.sessionId'
] as const;

export class AgentApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'AgentApiError';
    this.status = status;
  }
}

export function parseAgentEndpoint(value?: string): AgentEndpoint | null {
  const normalized = value?.trim().replace(/\/+$/, '');
  if (!normalized) {
    return null;
  }
  try {
    const url = new URL(normalized);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return null;
    }
    return { href: url.href.replace(/\/+$/, '') };
  } catch {
    return null;
  }
}

export async function sendAgentMessage(
  endpoint: AgentEndpoint,
  input: SendAgentMessageInput
): Promise<AgentMessageResult> {
  let sessionId = readStoredSession();
  if (!sessionId) {
    sessionId = await createSession(endpoint, input.signal);
  }
  try {
    return await postSessionMessage(endpoint, sessionId, input);
  } catch (error) {
    if (!(error instanceof AgentApiError) || error.status !== 404) {
      throw error;
    }
    clearStoredSession();
    sessionId = await createSession(endpoint, input.signal);
    return postSessionMessage(endpoint, sessionId, input);
  }
}

export function clearAgentSession(): void {
  clearStoredSession();
}

async function createSession(endpoint: AgentEndpoint, signal?: AbortSignal): Promise<string> {
  const payload = await requestJson(`${endpoint.href}/api/sessions`, {
    method: 'POST',
    signal
  });
  if (!isRecord(payload) || typeof payload.session_id !== 'string' || !payload.session_id) {
    throw new AgentApiError(200, 'Agent 会话接口响应格式不正确');
  }
  storeSession(payload.session_id);
  return payload.session_id;
}

async function postSessionMessage(
  endpoint: AgentEndpoint,
  sessionId: string,
  input: SendAgentMessageInput
): Promise<AgentMessageResult> {
  const payload = await requestJson(
    `${endpoint.href}/api/sessions/${encodeURIComponent(sessionId)}/messages`,
    {
      method: 'POST',
      signal: input.signal,
      body: JSON.stringify({
        input_id: createInputId(),
        text: input.text,
        clarification_id: input.clarificationId ?? null,
        context: input.context
      })
    }
  );
  if (!isMessageResult(payload)) {
    throw new AgentApiError(200, 'Agent 消息接口响应格式不正确');
  }
  return payload;
}

async function requestJson(url: string, options: RequestInit): Promise<unknown> {
  let response: Response;
  try {
    response = await fetch(url, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers ?? {})
      }
    });
  } catch (error) {
    if (options.signal?.aborted) {
      throw error;
    }
    throw new AgentApiError(0, '无法连接 Weavria Sidecar，请确认本地服务正在运行');
  }

  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }
  if (!response.ok) {
    const detail = isRecord(payload) && typeof payload.detail === 'string'
      ? payload.detail
      : `请求失败（HTTP ${response.status}）`;
    throw new AgentApiError(response.status, detail);
  }
  return payload;
}

function isMessageResult(value: unknown): value is AgentMessageResult {
  if (!isRecord(value)
    || typeof value.session_id !== 'string'
    || typeof value.run_id !== 'string'
    || typeof value.status !== 'string'
    || typeof value.task_outcome !== 'string'
    || !isNullableString(value.answer)
    || !isNullableString(value.error)
    || !isNullableString(value.completion_reason)
    || !isNullableString(value.clarification_id)
    || !Array.isArray(value.tools)) {
    return false;
  }
  return value.tools.every((tool) => isRecord(tool)
    && typeof tool.call_id === 'string'
    && typeof tool.tool_name === 'string'
    && (tool.status === 'succeeded' || tool.status === 'failed'));
}

function isRecord(value: unknown): value is JsonRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function createInputId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `finscope-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function readStoredSession(): string | null {
  try {
    const current = window.localStorage.getItem(SESSION_STORAGE_KEY);
    if (current) {
      return current;
    }
    const legacy = LEGACY_SESSION_STORAGE_KEYS
      .map((key) => window.localStorage.getItem(key))
      .find((value) => Boolean(value));
    if (legacy) {
      storeSession(legacy);
      return legacy;
    }
  } catch {
    return null;
  }
  return null;
}

function storeSession(sessionId: string): void {
  try {
    window.localStorage.setItem(SESSION_STORAGE_KEY, sessionId);
    LEGACY_SESSION_STORAGE_KEYS.forEach((key) => window.localStorage.removeItem(key));
  } catch {
    // A blocked local store only disables persistence; the current request can still proceed.
  }
}

function clearStoredSession(): void {
  try {
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
    LEGACY_SESSION_STORAGE_KEYS.forEach((key) => window.localStorage.removeItem(key));
  } catch {
    // Nothing else is required when browser storage is unavailable.
  }
}

export type AgentSource = {
  call_id: string;
  run_id: string;
  label: string;
  source_id?: string;
  href?: string | null;
};
export type AgentSession = { session_id: string; title: string; updated_at: string };
export type AgentJob = {
  input_id: string;
  status: 'running' | 'cancelling' | 'finished' | 'cancelled' | 'interrupted' | 'failed';
  phase: string;
  tools: (Omit<AgentToolTrace, 'status'> & { status: 'running' | 'succeeded' | 'failed' })[];
  result: AgentMessageResult | null;
};
export type AgentHistory = {
  turns: { run_id: string; input_id: string; ordinal: number; user: string; assistant?: string;
    status: string; task_outcome: string; calls: { call_id: string; tool_name: string; artifact?: unknown; failure?: string }[] }[];
  requests: { input_id: string; text: string; status: AgentJob['status']; result: AgentMessageResult | null }[];
  before: number | null;
};

// 每个 Sidecar 各自保存会话, 切换服务不会把一个实例的会话发给另一个实例。
export function selectedAgentSession(endpoint: AgentEndpoint): string | null {
  try {
    return localStorage.getItem(`${SESSION_STORAGE_KEY}:${endpoint.href}`)
      ?? (localStorage.getItem('weavria.finscope.endpoint') === null ? localStorage.getItem(SESSION_STORAGE_KEY) : null);
  } catch { return null; }
}
export function selectAgentSession(endpoint: AgentEndpoint, id: string | null) {
  try {
    const key = `${SESSION_STORAGE_KEY}:${endpoint.href}`;
    if (id) {
      localStorage.setItem(key, id);
    }
    else {
      localStorage.removeItem(key);
    }
    localStorage.setItem('weavria.finscope.endpoint', endpoint.href);
  } catch { /* 浏览器禁用存储时仍允许当前页对话。 */ }
}
export async function newAgentSession(endpoint: AgentEndpoint): Promise<string> {
  const payload = await requestJson(`${endpoint.href}/api/sessions`, { method: 'POST' });
  if (!isRecord(payload) || typeof payload.session_id !== 'string') {
    throw new AgentApiError(200, '会话响应格式不正确');
  }
  selectAgentSession(endpoint, payload.session_id);
  return payload.session_id;
}
export async function listAgentSessions(endpoint: AgentEndpoint, signal?: AbortSignal): Promise<AgentSession[]> {
  const value = await requestJson(`${endpoint.href}/api/sessions`, { signal });
  if (!Array.isArray(value)) {
    throw new AgentApiError(200, '会话列表响应格式不正确');
  }
  return value as AgentSession[];
}
export async function loadAgentHistory(endpoint: AgentEndpoint, id: string, signal?: AbortSignal, before?: number): Promise<AgentHistory> {
  const value = await requestJson(`${endpoint.href}/api/sessions/${encodeURIComponent(id)}/history${before ? `?before=${before}` : ''}`, { signal });
  if (!isRecord(value) || !Array.isArray(value.turns) || !Array.isArray(value.requests)) {
    throw new AgentApiError(200, '历史响应格式不正确');
  }
  return value as AgentHistory;
}
export function newAgentInputId() { return createInputId(); }
export async function startAgentRequest(endpoint: AgentEndpoint, id: string, inputId: string, input: SendAgentMessageInput): Promise<AgentJob> {
  return jobRequest(endpoint, id, inputId, '', { method: 'POST', signal: input.signal,
    body: JSON.stringify({ input_id: inputId, text: input.text, context: input.context, clarification_id: input.clarificationId ?? null }) });
}
export async function readAgentRequest(endpoint: AgentEndpoint, id: string, inputId: string, signal?: AbortSignal) {
  return jobRequest(endpoint, id, inputId, `/${encodeURIComponent(inputId)}`, { signal });
}
export async function cancelAgentRequest(endpoint: AgentEndpoint, id: string, inputId: string) {
  return jobRequest(endpoint, id, inputId, `/${encodeURIComponent(inputId)}/cancel`, { method: 'POST' });
}
async function jobRequest(endpoint: AgentEndpoint, id: string, _inputId: string, suffix: string, options: RequestInit): Promise<AgentJob> {
  const value = await requestJson(`${endpoint.href}/api/sessions/${encodeURIComponent(id)}/requests${suffix}`, options);
  if (!isRecord(value) || typeof value.input_id !== 'string' || typeof value.status !== 'string'
    || !Array.isArray(value.tools) || value.result !== null && !isMessageResult(value.result)) {
    throw new AgentApiError(200, '执行状态响应格式不正确');
  }
  return value as AgentJob;
}
