import { useEffect, useMemo, useRef } from 'react';

import './AgentView.css';

type AgentViewProps = {
  agentUrl?: string;
};

type AgentMessage = {
  type?: unknown;
  version?: unknown;
};

const WEAVRIA_WIDGET_ASSET_VERSION = 3;
const WORKSPACE_NAVIGATION_CONTEXT = {
  schema_version: 1,
  surface: 'finscope_workspace',
  current_view: 'agent',
  navigation: {
    view: 'agent',
    label: 'Agent',
    hint: '全域问答'
  }
} as const;

export function AgentView({
  agentUrl = import.meta.env.VITE_WEAVRIA_AGENT_URL
}: AgentViewProps) {
  const endpoint = useMemo(() => parseEndpoint(agentUrl), [agentUrl]);
  const frameRef = useRef<HTMLIFrameElement>(null);

  useEffect(() => {
    if (!endpoint) {
      return undefined;
    }
    const receive = (event: MessageEvent<AgentMessage>) => {
      const frameWindow = frameRef.current?.contentWindow;
      if (!frameWindow
        || event.origin !== endpoint.origin
        || event.source !== frameWindow
        || event.data?.type !== 'weavria:ready'
        || event.data.version !== 1) {
        return;
      }
      frameWindow.postMessage(
        { type: 'weavria:context', context: WORKSPACE_NAVIGATION_CONTEXT },
        endpoint.origin
      );
    };
    window.addEventListener('message', receive);
    return () => window.removeEventListener('message', receive);
  }, [endpoint]);

  if (!endpoint) {
    const hasInvalidUrl = Boolean(agentUrl?.trim());
    return (
      <section className="agent-workspace agent-workspace--setup" aria-labelledby="agent-setup-title">
        <div className="agent-setup-state">
          <span className="agent-setup-code">AI / WORKSPACE</span>
          <h3 id="agent-setup-title">{hasInvalidUrl ? 'Agent 地址无效' : '配置 FinScope Agent'}</h3>
          <p>
            {hasInvalidUrl
              ? 'VITE_WEAVRIA_AGENT_URL 必须是有效的 HTTP(S) 地址。'
              : '设置 Weavria 服务地址后重启前端，即可在这里进行全域问答。'}
          </p>
          <code>VITE_WEAVRIA_AGENT_URL=http://127.0.0.1:8765</code>
          <small>该页面只向 Agent 传递工作区导航信息。</small>
        </div>
      </section>
    );
  }

  return (
    <section className="agent-workspace" aria-label="FinScope Agent">
      <iframe
        ref={frameRef}
        className="agent-workspace-frame"
        src={`${endpoint.href}/widget?mode=full&asset-version=${WEAVRIA_WIDGET_ASSET_VERSION}`}
        title="FinScope Agent 对话"
        sandbox="allow-scripts allow-same-origin"
        referrerPolicy="no-referrer"
      />
    </section>
  );
}

function parseEndpoint(value?: string) {
  const normalized = value?.trim().replace(/\/+$/, '');
  if (!normalized) {
    return null;
  }
  try {
    const url = new URL(normalized);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return null;
    }
    return { href: url.href.replace(/\/+$/, ''), origin: url.origin };
  } catch {
    return null;
  }
}
