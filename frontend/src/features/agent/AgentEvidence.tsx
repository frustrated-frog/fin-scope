import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { AgentSource } from './agentClient';

const LABELS: Record<string, string> = {
  date: '交易日',
  asOf: '数据截至',
  priceProvider: '行情来源',
  flowProvider: '资金来源',
  fromTime: '区间开始',
  toTime: '区间结束',
  amount: '成交额',
  large: '大单净额',
  superLarge: '超大单净额',
  largeNet: '大额净额',
  startPrice: '起始价格',
  endPrice: '结束价格',
  followups: '后续表现',
  directionChanges: '分钟净流向变号',
  keyTurns: '关键转折',
  changePct: '区间涨跌幅（%）',
  amountRatio: '区间成交额比',
  flowCount: '资金样本数',
  minuteCount: '应有资金分钟数',
  ruleVersion: '识别口径',
  minutes: '分钟',
  time: '时间',
  price: '价格',
  tradeAmount: '成交额',
  superLargeNet: '超大单净额',
  returnPct: '收益率（%）',
  complete: '样本完整',
  covered: '已覆盖样本',
  expected: '应有样本',
  value: '数值',
  warnings: '数据缺口'
};
function fields(value: unknown, depth = 0): React.ReactNode {
  if (value === null || value === undefined) {
    return '暂无数据';
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否';
  }
  if (typeof value !== 'object') {
    return String(value);
  }
  if (Array.isArray(value)) {
    return value.length ? (
      <ol>
        {value.map((item, index) => (
          <li key={index}>{fields(item, depth + 1)}</li>
        ))}
      </ol>
    ) : (
      '无'
    );
  }
  return (
    <dl className="agent-evidence-fields">
      {Object.entries(value).map(([key, item]) => (
        <div key={key}>
          <dt>{LABELS[key] ?? key}</dt>
          <dd>
            {depth > 2 && typeof item === 'object' ? (
              <details>
                <summary>展开明细</summary>
                {fields(item, 0)}
              </details>
            ) : (
              fields(item, depth + 1)
            )}
          </dd>
        </div>
      ))}
    </dl>
  );
}
export function AgentEvidence({
  source,
  data,
  error,
  onClose
}: {
  source: AgentSource;
  data: unknown;
  error: string;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    dialog.current?.querySelector<HTMLButtonElement>('button')?.focus();
    return () => previous?.focus();
  }, []);
  const payload = data && typeof data === 'object' && 'data' in data ? data.data : data;
  const record =
    payload && typeof payload === 'object' && !Array.isArray(payload)
      ? (payload as Record<string, unknown>)
      : null;
  const body =
    record &&
    [record.body, record.content, record.markdown, record.summary].find((item) => typeof item === 'string');
  return createPortal(
    <section
      className="agent-source-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="证据详情"
      ref={dialog}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          onClose();
        }
        if (event.key === 'Tab') {
          const elements = dialog.current?.querySelectorAll<HTMLElement>('button, a, summary');
          if (!elements?.length) {
            return;
          }
          const first = elements[0],
            last = elements[elements.length - 1];
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
          }
        }
      }}
    >
      <div className="agent-source-document">
        <button type="button" onClick={onClose}>
          关闭证据
        </button>
        <h3>{source.label}</h3>
        <p>本次运行读取的证据快照</p>
        {source.href && /^\?agentView=(marketIntel|financials)&/.test(source.href) && (
          <a href={source.href} target="_blank" rel="noopener noreferrer">
            在业务页面查看
          </a>
        )}
        {error ? (
          <p role="alert">{error}</p>
        ) : data === null ? (
          <p role="status">正在读取证据…</p>
        ) : typeof body === 'string' ? (
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{body}</ReactMarkdown>
        ) : (
          fields(payload)
        )}
        {data !== null && (
          <details>
            <summary>查看原始证据数据</summary>
            <pre>{JSON.stringify(payload, null, 2)}</pre>
          </details>
        )}
      </div>
    </section>,
    document.querySelector('.app-shell') ?? document.body
  );
}
