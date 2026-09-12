import { useState } from 'react';
import { api } from '../../shared/api/client';

type Props = { code: string; onOpenStock?: (code: string) => void };
export function ResearchStockActions({ code, onOpenStock }: Props) {
  const [state, setState] = useState<'idle' | 'saving' | 'saved'>('idle');
  const [error, setError] = useState('');
  async function add() {
    setState('saving');
    setError('');
    try {
      await api('/api/watchlist', { method: 'POST', body: JSON.stringify({ code: code.split('.')[0], type: 'STOCK', groupName: '市场研究' }) });
      setState('saved');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '加入自选失败，请重试');
      setState('idle');
    }
  }
  return <div className="mp-stock-actions">
    {onOpenStock && <button type="button" onClick={() => onOpenStock(code.split('.')[0])}>产业链研究</button>}
    <button type="button" disabled={state !== 'idle'} onClick={() => void add()}>{state === 'saved' ? '已加入自选' : state === 'saving' ? '正在加入' : '加入自选'}</button>
    {error && <span role="alert">{error}</span>}
  </div>;
}
