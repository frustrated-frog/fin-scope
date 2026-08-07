import { FormEvent, useState } from 'react';
import { api } from '../../shared/api/client';
import { SingleStockForecast } from './quantTypes';

type Toast = (message: string, type?: 'success' | 'error' | 'info') => void;

const statusCopy: Record<string, { label: string; tone: string }> = {
  INSUFFICIENT_DATA: { label: '历史样本不足', tone: 'muted' },
  LOW_CONFIDENCE: { label: '低置信度观察', tone: 'caution' },
  NO_OBSERVED_EDGE: { label: '未发现稳定优势', tone: 'neutral' },
  CONDITIONAL_EDGE: { label: '条件性预测优势', tone: 'caution' },
  EVIDENCE_SUPPORTED: { label: '样本外证据支持', tone: 'supported' },
  MODEL_UNAVAILABLE: { label: '模型暂不可用', tone: 'muted' }
};

function percent(value?: number, digits = 1) {
  return value == null ? '—' : `${(value * 100).toFixed(digits)}%`;
}

function signedPercent(value?: number) {
  if (value == null) return '—';
  return `${value >= 0 ? '+' : ''}${(value * 100).toFixed(1)}%`;
}

export function SingleStockForecastPanel({ addToast, setMessage }: {
  addToast: Toast;
  setMessage: (message: string) => void;
}) {
  const [code, setCode] = useState('');
  const [result, setResult] = useState<SingleStockForecast>();
  const [busy, setBusy] = useState(false);

  async function run(event: FormEvent) {
    event.preventDefault();
    const normalized = code.trim();
    if (!/^\d{6}$/.test(normalized)) {
      addToast('请输入六位 A 股代码', 'error');
      return;
    }
    setBusy(true);
    try {
      const value = await api<SingleStockForecast>('/api/quant/single-stock-forecasts', {
        method: 'POST', body: JSON.stringify({ code: normalized })
      });
      setResult(value);
      setMessage(`${value.instrumentCode} · 20 日预测已完成`);
      addToast(value.status === 'INSUFFICIENT_DATA' ? '历史覆盖不足，未生成概率' : '滚动样本外预测已完成',
        value.status === 'EVIDENCE_SUPPORTED' ? 'success' : 'info');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '单股预测运行失败', 'error');
    } finally { setBusy(false); }
  }

  const status = result ? (statusCopy[result.status] ?? { label: result.status, tone: 'neutral' }) : undefined;
  const probabilityPosition = Math.max(2, Math.min(98, (result?.upProbability ?? 0.5) * 100));

  return <div className="single-forecast">
    <section className="single-forecast-intro">
      <div>
        <p className="single-forecast-kicker">Single-name probability lab</p>
        <h4>不要猜涨跌，<em>检验概率。</em></h4>
        <p>读取服务端完整前复权历史，在每个过去时点重新训练，只用当时已经成熟的数据预测未来 20 个交易日。</p>
      </div>
      <form className="single-forecast-form" onSubmit={run}>
        <label htmlFor="single-stock-code">股票代码</label>
        <div><input id="single-stock-code" inputMode="numeric" maxLength={6} placeholder="例如 600519"
          value={code} onChange={event => setCode(event.target.value.replace(/\s/g, ''))} />
          <button type="submit" disabled={busy}>{busy ? '正在回看历史…' : '运行 20 日预测'}</button></div>
        <small>T 日收盘生成预测 · T+1 开盘模拟进入 · 已计固定成本</small>
      </form>
    </section>

    {!result && <section className="single-forecast-empty">
      <span>RESEARCH GATE</span>
      <strong>一次只研究一个问题</strong>
      <p>模型不会挑选股票，也不会自动调参。它只回答：对这只股票，当前历史形态是否提供了高于基础上涨率的证据。</p>
      <div><i />完整历史缓存 <i />滚动样本外验证 <i />允许拒绝预测</div>
    </section>}

    {result?.status === 'INSUFFICIENT_DATA' && <section className="single-forecast-gate" role="status">
      <span>DATA GATE / BLOCKED</span>
      <h4>历史样本不足</h4>
      <p>{result.conclusion}</p>
      <strong>已取得 {result.barCount} 根日线，正式预测至少需要 750 根。</strong>
      <small>截止 {result.asOfDate} · {result.sourceCode}</small>
    </section>}

    {result && result.status !== 'INSUFFICIENT_DATA' && result.upProbability != null && <>
      <section className="single-forecast-board" data-tone={status?.tone}>
        <header>
          <div><span>{result.instrumentCode}</span><small>数据截止 {result.asOfDate}</small></div>
          <b>{status?.label}</b>
        </header>
        <div className="single-forecast-thesis">
          <div className="single-forecast-probability" aria-label="20日预测结果">
            <span>未来 20 日净收益为正的概率</span>
            <strong>{percent(result.upProbability)}</strong>
            <small>不是确定性涨跌判断</small>
          </div>
          <div className="single-forecast-verdict">
            <span>RESEARCH VERDICT</span>
            <p>{result.conclusion}</p>
          </div>
        </div>
        <div className="forecast-probability-tape" aria-label={`上涨概率 ${percent(result.upProbability)}`}>
          <div className="forecast-tape-labels"><span>下行证据</span><b>无条件边界</b><span>上行证据</span></div>
          <div className="forecast-tape-track"><i /><i /><i /><b style={{ left: `${probabilityPosition}%` }} /></div>
          <small style={{ left: `${probabilityPosition}%` }}>{percent(result.upProbability)}</small>
        </div>
        <div className="forecast-return-band">
          <div><span>偏弱情景 · P20</span><strong>{signedPercent(result.lowerNetReturn)}</strong></div>
          <div><span>相近信号平均</span><strong>{signedPercent(result.expectedNetReturn)}</strong></div>
          <div><span>偏强情景 · P80</span><strong>{signedPercent(result.upperNetReturn)}</strong></div>
        </div>
      </section>

      {result.validation && <section className="forecast-evidence">
        <header><div><span>OUT-OF-SAMPLE LEDGER</span><h4>样本外证据</h4></div><b>{result.validation.independentSampleCount} 个独立样本</b></header>
        <div className="forecast-evidence-grid">
          <article><span>独立命中率</span><strong>{percent(result.validation.accuracy)}</strong><small>每 20 日取一个非重叠锚点</small></article>
          <article><span>模型 Brier</span><strong>{result.validation.brierScore.toFixed(3)}</strong><small>越低越好</small></article>
          <article><span>基准 Brier</span><strong>{result.validation.baselineBrierScore.toFixed(3)}</strong><small>该股历史上涨率基准</small></article>
          <article><span>历史上涨率</span><strong>{percent(result.validation.observedUpRate)}</strong><small>{result.validation.outOfSampleCount} 次顺序预测</small></article>
        </div>
        {result.recentObservations.length > 0 && <div className="forecast-observation-table">
          <table aria-label="最近样本外预测"><thead><tr><th>信号日</th><th>当时概率</th><th>实际净收益</th><th>结果</th></tr></thead>
            <tbody>{result.recentObservations.map(item => <tr key={item.signalDate}><td>{item.signalDate}</td><td>{percent(item.probability)}</td><td data-return={item.actualNetReturn >= 0 ? 'positive' : 'negative'}>{signedPercent(item.actualNetReturn)}</td><td><i data-correct={item.correct}>{item.correct ? '命中' : '偏离'}</i></td></tr>)}</tbody>
          </table>
        </div>}
      </section>}

      <footer className="forecast-provenance"><div><span>DATA TRACE</span><b>{result.barCount} 根日线 · {result.labeledSampleCount} 个可标注样本 · {result.sourceCode}</b></div><code>{result.dataFingerprint.slice(0, 20)}</code>{result.warnings.map(warning => <p key={warning}>{warning}</p>)}</footer>
    </>}
  </div>;
}
