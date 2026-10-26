import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { AgentView } from './AgentView';

describe('AgentView', () => {
  it('shows setup guidance when the Agent URL is absent', () => {
    render(<AgentView agentUrl="" />);

    expect(screen.getByRole('heading', { name: '配置 FinScope Agent' })).toBeInTheDocument();
    expect(screen.getByText('VITE_WEAVRIA_AGENT_URL=http://127.0.0.1:8765')).toBeInTheDocument();
    expect(screen.queryByTitle('FinScope Agent 对话')).not.toBeInTheDocument();
  });

  it('rejects a configured URL outside HTTP(S)', () => {
    render(<AgentView agentUrl="javascript:alert(1)" />);

    expect(screen.getByRole('heading', { name: 'Agent 地址无效' })).toBeInTheDocument();
    expect(screen.getByText(/HTTP\(S\) 地址/)).toBeInTheDocument();
    expect(screen.queryByTitle('FinScope Agent 对话')).not.toBeInTheDocument();
  });

  it('hosts the full widget in an isolated iframe', () => {
    render(<AgentView agentUrl="http://127.0.0.1:8765/" />);

    const frame = screen.getByTitle('FinScope Agent 对话') as HTMLIFrameElement;
    expect(frame).toHaveAttribute(
      'src',
      'http://127.0.0.1:8765/widget?mode=full&asset-version=4'
    );
    expect(frame).toHaveAttribute('sandbox', 'allow-scripts allow-same-origin');
    expect(frame).toHaveAttribute('referrerpolicy', 'no-referrer');
  });

  it('sends navigation-only context after a trusted ready message', () => {
    render(<AgentView agentUrl="https://agent.example" />);
    const frame = screen.getByTitle('FinScope Agent 对话') as HTMLIFrameElement;
    const postMessage = vi.spyOn(frame.contentWindow!, 'postMessage');

    window.dispatchEvent(new MessageEvent('message', {
      origin: 'https://untrusted.example',
      source: frame.contentWindow,
      data: { type: 'weavria:ready', version: 1 }
    }));
    window.dispatchEvent(new MessageEvent('message', {
      origin: 'https://agent.example',
      source: window,
      data: { type: 'weavria:ready', version: 1 }
    }));
    window.dispatchEvent(new MessageEvent('message', {
      origin: 'https://agent.example',
      source: frame.contentWindow,
      data: { type: 'weavria:ready', version: 2 }
    }));
    window.dispatchEvent(new MessageEvent('message', {
      origin: 'https://agent.example',
      source: frame.contentWindow,
      data: { type: 'weavria:context', version: 1 }
    }));

    expect(postMessage).not.toHaveBeenCalled();

    window.dispatchEvent(new MessageEvent('message', {
      origin: 'https://agent.example',
      source: frame.contentWindow,
      data: { type: 'weavria:ready', version: 1 }
    }));

    expect(postMessage).toHaveBeenCalledOnce();
    expect(postMessage).toHaveBeenCalledWith({
      type: 'weavria:context',
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
    }, 'https://agent.example');
  });
});
