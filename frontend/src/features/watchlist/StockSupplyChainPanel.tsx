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

export function StockSupplyChainPanel({ code, name }: { code: string; name?: string }) {
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
      if (!stopped.current) setError(messageOf(refreshError, '产业链证据更新失败，请稍后重试'));
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
    <section className="stock-chain-panel" aria-label={`${name || code} 产业链证据图谱`}>
      <div className="stock-chain-toolbar">
        <div>
          <span>SUPPLY CHAIN · EVIDENCE MAP</span>
          <p>基于公司披露与公开资料建立，关系强度取决于可核验的证据。</p>
        </div>
        <button type="button" onClick={() => void refresh()} disabled={running} aria-label="更新产业链证据">
          <span aria-hidden="true">↻</span>{running ? '更新中' : '更新证据'}
        </button>
      </div>

      {running && (
        <div className="stock-chain-status" role="status">
          <span aria-hidden="true" />
          {snapshot ? '正在更新公开证据，当前快照仍可正常查看。' : '正在建立产业链证据图谱，通常需要几十秒。'}
        </div>
      )}
      {error && <div className="stock-chain-status is-error" role="alert">{error}</div>}

      {snapshot ? <SupplyChainSnapshotView snapshot={snapshot} /> : failed ? (
        <div className="stock-chain-empty is-failed" role="alert">
          <strong>产业链生成失败</strong>
          <p>{run.message || '公开证据或模型服务暂时不可用，未生成不可靠的产业链结果。'}</p>
          <button type="button" aria-label="重新生成产业链" onClick={() => void refresh()}>重新生成</button>
        </div>
      ) : !running && (
        <div className="stock-chain-empty">
          <strong>暂时没有可用的产业链快照</strong>
          <p>更新证据后，系统只会展示能被公开资料支持的上下游关系。</p>
          <button type="button" onClick={() => void refresh()}>开始建立图谱</button>
        </div>
      )}
    </section>
  );
}

function SupplyChainSnapshotView({ snapshot }: { snapshot: StockSupplyChainSnapshot }) {
  const evidenceByCode = new Map(snapshot.evidence.map((item) => [item.evidenceCode, item]));
  return (
    <>
      <header className="stock-chain-thesis">
        <div>
          <span>产业位置</span>
          <strong>{snapshot.position}</strong>
        </div>
        <p>{snapshot.summary}</p>
        <time dateTime={snapshot.evidenceAsOf}>证据截至 {snapshot.evidenceAsOf || '未标注'}</time>
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
                    <div className="stock-chain-evidence-refs" aria-label={`${node.name} 证据`}>
                      {node.evidenceRefs.map((reference) => {
                        const evidence = evidenceByCode.get(reference);
                        return evidence?.url ? (
                          <a key={reference} href={evidence.url} target="_blank" rel="noreferrer" title={evidence.title}>{reference}</a>
                        ) : <span key={reference}>{reference}</span>;
                      })}
                    </div>
                  </article>
                ))}
              </div>
            </section>
          );
        })}
      </div>

      <section className="stock-chain-sources" aria-labelledby="stock-chain-sources-title">
        <header><span>VERIFIABLE SOURCES</span><h3 id="stock-chain-sources-title">证据索引</h3></header>
        <ol>
          {snapshot.evidence.map((evidence) => (
            <li key={evidence.evidenceCode}>
              <span>{evidence.evidenceCode}</span>
              <div>
                {evidence.url
                  ? <a href={evidence.url} target="_blank" rel="noreferrer">{evidence.title}<i aria-hidden="true">↗</i></a>
                  : <strong>{evidence.title}</strong>}
                <p>{[evidence.sourceTier, evidence.source, evidence.publishedAt].filter(Boolean).join(' · ')}</p>
                {evidence.excerpt && <blockquote>{evidence.excerpt}</blockquote>}
              </div>
            </li>
          ))}
        </ol>
      </section>

      {snapshot.limitations && (
        <aside className="stock-chain-limitations">
          <strong>证据边界</strong><p>{snapshot.limitations}</p>
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

function relationLabel(value: string) {
  const labels: Record<string, string> = {
    SUPPLY: '供应环节', CORE_BUSINESS: '核心业务', CUSTOMER_INDUSTRY: '客户行业',
    RAW_MATERIAL: '原材料', COMPONENT: '核心部件', TECHNOLOGY: '技术供给', PRODUCT: '产品', APPLICATION: '应用市场'
  };
  return labels[value] || value.replace(/_/g, ' ');
}
