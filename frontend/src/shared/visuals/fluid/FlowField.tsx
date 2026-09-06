import { ReactNode, useEffect, useRef } from 'react';
import type { FlowController, FlowFactory, FlowMode } from './renderer';

const loadDefaultRenderer = async (): Promise<FlowFactory> => {
  if (typeof WebGL2RenderingContext === 'undefined') {
    throw new Error('WebGL2 unavailable');
  }
  return (await import('./renderer')).createFlowRenderer;
};

export function FlowField({ mode, className = '', children, loadRenderer = loadDefaultRenderer }: {
  mode: FlowMode;
  className?: string;
  children?: ReactNode;
  loadRenderer?: () => Promise<FlowFactory>;
}) {
  const hostRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const host = hostRef.current;
    const canvas = canvasRef.current;
    if (!host || !canvas) {
      return;
    }
    let disposed = false;
    let engine: FlowController | undefined;
    let inView = true;
    const motion = window.matchMedia?.('(prefers-reduced-motion: reduce)');
    const syncVisibility = () => engine?.setActive(!document.hidden && inView);
    const syncMotion = () => engine?.setMotion(!motion?.matches);
    const refresh = () => engine?.refresh();
    const fallback = () => {
      host.dataset.flowReady = 'false';
      engine?.destroy();
      engine = undefined;
    };
    const intersection = typeof IntersectionObserver === 'undefined' ? undefined : new IntersectionObserver(entries => {
      inView = entries.some(entry => entry.isIntersecting);
      syncVisibility();
    });
    const resize = typeof ResizeObserver === 'undefined' ? undefined : new ResizeObserver(refresh);
    const theme = new MutationObserver(refresh);
    const shell = host.closest('[data-theme]');
    if (shell) {
      theme.observe(shell, { attributes: true, attributeFilter: ['data-theme'] });
    }
    intersection?.observe(host);
    resize?.observe(host);
    host.querySelectorAll('[data-flow-surface]').forEach(surface => resize?.observe(surface));
    document.addEventListener('visibilitychange', syncVisibility);
    motion?.addEventListener?.('change', syncMotion);
    window.addEventListener('resize', refresh);
    // Scroll also refreshes the frozen reduced-motion frame and clipped card positions.
    document.addEventListener('scroll', refresh, { capture: true, passive: true });
    void loadRenderer().then(create => {
      if (disposed) {
        return;
      }
      engine = create(canvas, host, { mode, motion: !motion?.matches, onFailure: fallback });
      host.dataset.flowReady = 'true';
      syncMotion();
      syncVisibility();
    }).catch(() => {
      if (!disposed) {
        fallback();
      }
    });

    return () => {
      disposed = true;
      intersection?.disconnect();
      resize?.disconnect();
      theme.disconnect();
      document.removeEventListener('visibilitychange', syncVisibility);
      document.removeEventListener('scroll', refresh, true);
      motion?.removeEventListener?.('change', syncMotion);
      window.removeEventListener('resize', refresh);
      engine?.destroy();
    };
  }, [loadRenderer, mode]);

  return (
    <div
      ref={hostRef}
      className={`flow-field flow-field--${mode} ${className}`}
      role={mode === 'ambient' ? undefined : 'group'}
      aria-label={mode === 'cards' ? '研究队列总览' : mode === 'panels' ? '热点动态榜单' : undefined}
    >
      <canvas ref={canvasRef} className="flow-canvas" aria-hidden="true" />
      {children}
    </div>
  );
}
