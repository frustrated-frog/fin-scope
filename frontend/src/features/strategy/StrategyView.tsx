import { useEffect, useState } from 'react';
import { LongTermStrategyView } from './LongTermStrategyView';
import { QuantWorkspace } from './QuantWorkspace';
import { StockLearningCardPanel } from './StockLearningCardPanel';
import { QuantResearchEntryIntent } from './quantTypes';
import type { StockDiscoveryMarketContext } from '../../shared/types/marketContext';

interface Props {
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage: (message: string) => void;
  entryIntent?: QuantResearchEntryIntent;
  onEntryIntentConsumed?: () => void;
  marketContext?: StockDiscoveryMarketContext;
}

export function StrategyView(props: Props) {
  const [mode, setMode] = useState<'quant' | 'long-term' | 'learning'>('quant');
  useEffect(() => { if (props.entryIntent) setMode('quant'); }, [props.entryIntent]);
  return <div className="strategy-mode-shell">
    <nav className="strategy-mode-switch" aria-label="策略工作台模式">
      <button type="button" aria-current={mode === 'quant' ? 'page' : undefined} className={mode === 'quant' ? 'active' : ''} onClick={() => setMode('quant')}><span>Quant</span>量化策略平台</button>
      <button type="button" aria-current={mode === 'long-term' ? 'page' : undefined} className={mode === 'long-term' ? 'active' : ''} onClick={() => setMode('long-term')}><span>Compound</span>长期投资工作台</button>
      <button type="button" aria-current={mode === 'learning' ? 'page' : undefined} className={mode === 'learning' ? 'active' : ''} onClick={() => setMode('learning')}><span>Agent</span>股票学习卡</button>
    </nav>
    {mode === 'quant' ? <QuantWorkspace {...props} /> : mode === 'long-term' ? <LongTermStrategyView addToast={props.addToast} setMessage={props.setMessage} /> : <StockLearningCardPanel addToast={props.addToast} setMessage={props.setMessage} />}
  </div>;
}
