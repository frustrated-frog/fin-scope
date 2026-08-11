import { useCallback, useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import {
  StockSupplyChainLayer,
  StockSupplyChainRefreshRun,
  StockSupplyChainSnapshot,
  StockSupplyChainView
} from '../../shared/types';

const LAYERS: Array<{ key: StockSupplyChainLayer; index: string; title: string; subtitle: string }> = [
  { key: 'UPSTREAM', index: '01', title: '上游供给', subtitle: '原材料 · 核心零部件 · 技术供给' },
  { key: 'COMPANY', index: '02', title: '公司位置', subtitle: '主营业务 · 产品 · 产业卡位' },
  { key: 'DOWNSTREAM', index: '03', title: '下游需求', subtitle: '客户行业 · 应用市场 · 需求驱动' }
];

const POLL_INTERVAL_MS = 1_000;

export function StockSupplyChainPanel({ code, name, onOpenIndustryChain }: {
  code: string;
  name?: string;
  onOpenIndustryChain?: (code: string) => void;
}) {
  const [snapshot, setSnapshot] = useState<StockSupplyChainSnapshot | null>(null);
  const [run, setRun] = useState<StockSupplyChainRefreshRun | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const stopped = useRef(false);
  const pollTimer = useRef<number>();

  const schedulePoll = useCallback(() => {
    window.clearTimeout(pollTimer.current);
    pollTimer.current = window.setTimeout(async () => {
      try {
        const view = await api<StockSupplyChainView>(`/api/stocks/${code}/supply-chain`);
        if (stopped.current) return;
        setSnapshot(view.snapshot);
        setRun(view.refreshRun);
        setError('');
        if (view.refreshRun?.status === 'RUNNING') schedulePoll();
      } catch (pollError) {
        if (!stopped.current) setError(messageOf(pollError, '产业链刷新状态暂时不可用'));
      }
    }, POLL_INTERVAL_MS);
  }, [code]);

  const refresh = useCallback(async () => {
    setError('');
    try {
      const nextRun = await api<StockSupplyChainRefreshRun>(`/api/stocks/${code}/supply-chain/refresh`, { method: 'POST' });
      if (stopped.current) return;
      setRun(nextRun);
      schedulePoll();
    } catch (refreshError) {
      if (!stopped.current) setError(messageOf(refreshError, '产业链更新失败，请稍后重试'));
    }
  }, [code, schedulePoll]);

  useEffect(() => {
    stopped.current = false;
    setLoading(true);
    setError('');
    void api<StockSupplyChainView>(`/api/stocks/${code}/supply-chain`)
      .then((view) => {
        if (stopped.current) return;
        setSnapshot(view.snapshot);
        setRun(view.refreshRun);
        setLoading(false);
        if (view.refreshRun?.status === 'RUNNING') {
          schedulePoll();
        } else if (!view.snapshot && !view.refreshRun) {
          void refresh();
        }
      })
      .catch((loadError) => {
        if (!stopped.current) {
          setError(messageOf(loadError, '产业链图谱加载失败'));
          setLoading(false);
        }
      });
    return () => {
      stopped.current = true;
      window.clearTimeout(pollTimer.current);
    };
  }, [code, refresh, schedulePoll]);

  const running = run?.status === 'RUNNING';
  const failed = run?.status === 'FAILED';

  if (loading) {
    return <div className="stock-chain-pending" aria-live="polite"><span aria-hidden="true" /><strong>正在读取产业链快照…</strong></div>;
  }

  return (
    <section className="stock-chain-panel" aria-label={`${name || code} 产业链结论`}>
      <div className="stock-chain-toolbar">
        <div>
          <span>SUPPLY CHAIN · CONCLUSION</span>
          <p>聚焦公司所处环节、关键关系与结论边界。</p>
        </div>
        <div className="stock-chain-toolbar-actions">
          {onOpenIndustryChain && (
            <button type="button" onClick={() => onOpenIndustryChain(code)} aria-label="在完整产业图谱中查看">
              <span aria-hidden="true">↗</span>完整产业图谱
            </button>
          )}
          <button type="button" onClick={() => void refresh()} disabled={running} aria-label="更新产业链">
            <span aria-hidden="true">↻</span>{running ? '更新中' : '更新产业链'}
          </button>
        </div>
      </div>

      {running && (
        <div className="stock-chain-status" role="status">
          <span aria-hidden="true" />
          {snapshot ? '正在更新产业链结论，当前结果仍可正常查看。' : '正在建立产业链结论，通常需要几十秒。'}
        </div>
      )}
      {error && <div className="stock-chain-status is-error" role="alert">{error}</div>}

      {snapshot ? <SupplyChainSnapshotView snapshot={snapshot} /> : failed ? (
        <div className="stock-chain-empty is-failed" role="alert">
          <strong>产业链生成失败</strong>
          <p>{run.message || '公开信息或模型服务暂时不可用，未生成不可靠的产业链结果。'}</p>
          <button type="button" aria-label="重新生成产业链" onClick={() => void refresh()}>重新生成</button>
        </div>
      ) : !running && (
        <div className="stock-chain-empty">
          <strong>暂时没有可用的产业链快照</strong>
          <p>更新后，系统将呈现能够被公开信息支持的上下游关系。</p>
          <button type="button" onClick={() => void refresh()}>开始建立图谱</button>
        </div>
      )}
    </section>
  );
}

function SupplyChainSnapshotView({ snapshot }: { snapshot: StockSupplyChainSnapshot }) {
  const snapshotTime = snapshot.updatedAt || snapshot.generatedAt;
  return (
    <>
      <header className="stock-chain-thesis">
        <div>
          <span>产业位置</span>
          <strong>{snapshot.position}</strong>
        </div>
        <p>{snapshot.summary}</p>
        <time dateTime={snapshotTime}>更新于 {formatSnapshotTime(snapshotTime)}</time>
      </header>

      <div className="stock-chain-rail">
        {LAYERS.map((layer) => {
          const nodes = snapshot.nodes.filter((node) => node.layer === layer.key);
          return (
            <section key={layer.key} className={`stock-chain-layer is-${layer.key.toLowerCase()}`} aria-labelledby={`stock-chain-${layer.key}`}>
              <header>
                <span>{layer.index}</span>
                <div><h3 id={`stock-chain-${layer.key}`}>{layer.title}</h3><p>{layer.subtitle}</p></div>
              </header>
              <div className="stock-chain-node-list">
                {nodes.map((node, index) => (
                  <article key={`${node.name}-${index}`} className="stock-chain-node">
                    <div className="stock-chain-node-title">
                      <strong>{node.name}</strong>
                      <span className={`is-${node.confidence.toLowerCase()}`}>{confidenceLabel(node.confidence)}</span>
                    </div>
                    <small>{relationLabel(node.relationType)}</small>
                    <p>{node.description}</p>
                  </article>
                ))}
              </div>
            </section>
          );
        })}
      </div>

      {snapshot.limitations && (
        <aside className="stock-chain-limitations">
          <strong>结论边界</strong><p>{formatConclusionBoundary(snapshot.limitations)}</p>
        </aside>
      )}
    </>
  );
}

function messageOf(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function confidenceLabel(value: string) {
  return value === 'HIGH' ? '高可信' : value === 'MEDIUM' ? '中可信' : '待验证';
}

function formatSnapshotTime(value?: string) {
  if (!value) return '时间未标注';
  return value.replace('T', ' ').slice(0, 16);
}

function formatConclusionBoundary(value: string) {
  return value
    .replace(/[（(]?\s*E\d+(?:\s*[,，、]\s*E\d+)*\s*[）)]?/gi, '')
    .replace(/[（(]?\s*T\d(?:级)?\s*[）)]?/gi, '')
    .replace(/证据/g, '公开信息')
    .replace(/\s+([，。；：])/g, '$1')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

function relationLabel(value: string) {
  const labels: Record<string, string> = {
    SUPPLY: '供应环节', CORE_BUSINESS: '核心业务', CUSTOMER_INDUSTRY: '客户行业',
    RAW_MATERIAL: '原材料', COMPONENT: '核心部件', TECHNOLOGY: '技术供给', PRODUCT: '产品', APPLICATION: '应用市场'
  };
  return labels[value] || value.replace(/_/g, ' ');
}
