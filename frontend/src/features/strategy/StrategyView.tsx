import { useState } from 'react';
import { LongTermStrategyView } from './LongTermStrategyView';
import { QuantWorkspace } from './QuantWorkspace';

interface Props { addToast: (message: string, type?: 'success' | 'error' | 'info') => void; setMessage: (message: string) => void }

export function StrategyView(props: Props) {
  const [mode, setMode] = useState<'quant' | 'long-term'>('quant');
  return <div className="strategy-mode-shell">
    <nav className="strategy-mode-switch" aria-label="策略工作台模式">
      <button type="button" className={mode === 'quant' ? 'active' : ''} onClick={() => setMode('quant')}><span>Quant</span>量化策略平台</button>
      <button type="button" className={mode === 'long-term' ? 'active' : ''} onClick={() => setMode('long-term')}><span>Compound</span>长期投资工作台</button>
    </nav>
    {mode === 'quant' ? <QuantWorkspace {...props} /> : <LongTermStrategyView {...props} />}
  </div>;
}
