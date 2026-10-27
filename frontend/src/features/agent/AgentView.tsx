import { useMemo } from 'react';

import { AgentConversation } from './AgentConversation';
import { parseAgentEndpoint } from './agentClient';
import './AgentView.css';

type AgentViewProps = {
  agentUrl?: string;
};

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
  const endpoint = useMemo(() => parseAgentEndpoint(agentUrl), [agentUrl]);

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
              : '设置 Weavria Sidecar 地址后重启前端，即可在这里进行全域问答。'}
          </p>
          <code>VITE_WEAVRIA_AGENT_URL=http://127.0.0.1:8765</code>
          <small>FinScope 负责界面，Weavria 只通过 JSON 接口返回运行结果。</small>
        </div>
      </section>
    );
  }

  return (
    <section className="agent-workspace" aria-label="FinScope Agent">
      <AgentConversation endpoint={endpoint} context={WORKSPACE_NAVIGATION_CONTEXT} />
    </section>
  );
}
