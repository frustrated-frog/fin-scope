import { useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import { QuantDataset, QuantDataSyncRun } from './quantTypes';

interface Props {
  dataset?: QuantDataset;
  suggestedIndex: number;
  onDatasetChanged: (dataset: QuantDataset) => void | Promise<void>;
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}

type ImportKind = 'bars' | 'universe';

function parseSummary(value?: string) {
  try { return value ? JSON.parse(value) as Record<string, unknown> : {}; } catch { return {}; }
}

async function readRows(file: File | undefined, kind: ImportKind) {
  if (!file) throw new Error(`请选择${kind === 'bars' ? '日线行情' : '股票池'} JSON 文件`);
  if (file.size > 8 * 1024 * 1024) throw new Error('单个导入文件不能超过 8 MB');
  const value = JSON.parse(await readFileText(file)) as unknown;
  if (!Array.isArray(value) || value.length === 0 || value.length > 100_000) {
    throw new Error('导入内容必须是 1 到 100000 条记录的 JSON 数组');
  }
  const required = kind === 'bars'
    ? ['tradeDate', 'instrumentCode', 'open', 'high', 'low', 'close', 'adjustedClose', 'volume', 'amount']
    : ['tradeDate', 'instrumentCode'];
  value.forEach((row, index) => {
    if (!row || typeof row !== 'object' || required.some(field => !(field in row))) {
      throw new Error(`第 ${index + 1} 条记录缺少必需字段：${required.join('、')}`);
    }
    if (kind === 'universe' && (row as Record<string, unknown>).sourceKind !== 'POINT_IN_TIME') {
      throw new Error(`第 ${index + 1} 条股票池记录必须明确 sourceKind=POINT_IN_TIME`);
    }
  });
  return value;
}

function readFileText(file: File): Promise<string> {
  if (typeof file.text === 'function') return file.text();
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(new Error('无法读取导入文件'));
    reader.readAsText(file, 'UTF-8');
  });
}

export function ResearchDatasetWizard({ dataset, suggestedIndex, onDatasetChanged, addToast }: Props) {
  const [name, setName] = useState(`资金行为研究集 ${suggestedIndex}`);
  const [barsFile, setBarsFile] = useState<File>();
  const [universeFile, setUniverseFile] = useState<File>();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [asOfTime, setAsOfTime] = useState('');
  const [busy, setBusy] = useState<string>();
  const [latestSync, setLatestSync] = useState<QuantDataSyncRun>();
  const realDataset = dataset?.dataKind === 'REAL' ? dataset : undefined;
  const quality = useMemo(() => parseSummary(realDataset?.qualitySummary), [realDataset?.qualitySummary]);
  const hasPitUniverse = Number(quality.universeEventCount ?? 0) > 0;

  useEffect(() => {
    let active = true;
    if (!realDataset) {
      setLatestSync(undefined);
      return () => { active = false; };
    }
    api<QuantDataSyncRun[]>(`/api/quant/datasets/${realDataset.id}/market-data-sync-runs`)
      .then(values => { if (active) setLatestSync(values[0]); })
      .catch(() => { /* History is helpful, but must not block dataset editing. */ });
    return () => { active = false; };
  }, [realDataset?.id]);

  async function create() {
    setBusy('create');
    try {
      const value = await api<QuantDataset>('/api/quant/datasets', {
        method: 'POST', body: JSON.stringify({ name: name.trim(), dataKind: 'REAL' })
      });
      await onDatasetChanged(value);
      addToast('真实研究集容器已建立，请导入真实行情与点时股票池', 'success');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '真实研究集创建失败', 'error');
    } finally { setBusy(undefined); }
  }

  async function importPartition(kind: ImportKind) {
    if (!realDataset) return;
    setBusy(kind);
    try {
      const rows = await readRows(kind === 'bars' ? barsFile : universeFile, kind);
      const value = await api<QuantDataset>(`/api/quant/datasets/${realDataset.id}/${kind}`, {
        method: 'POST', body: JSON.stringify(rows)
      });
      await onDatasetChanged(value);
      addToast(kind === 'bars' ? '真实日线已通过结构校验并导入' : '点时股票池已导入', 'success');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '研究分区导入失败', 'error');
    } finally { setBusy(undefined); }
  }

  async function freezeCapital() {
    if (!realDataset || !from || !to || !asOfTime) return;
    setBusy('freeze');
    try {
      const value = await api<QuantDataset>(`/api/factor-research/datasets/${realDataset.id}/capital-flow-freeze`, {
        method: 'POST', body: JSON.stringify({ from, to, asOfTime })
      });
      await onDatasetChanged(value);
      addToast(value.status === 'READY' ? '资金分区冻结完成，数据集质量通过' : '冻结完成，但质量门禁发现阻断项', value.status === 'READY' ? 'success' : 'info');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '资金分区冻结失败', 'error');
    } finally { setBusy(undefined); }
  }

  async function syncMarketData() {
    if (!realDataset || !hasPitUniverse) return;
    setBusy('sync');
    try {
      const run = await api<QuantDataSyncRun>(
        `/api/quant/datasets/${realDataset.id}/market-data-sync`, { method: 'POST' }
      );
      setLatestSync(run);
      addToast(run.status === 'SUCCESS'
        ? `市场日线同步完成，新增 ${run.insertedRows} 条`
        : `同步保留了 ${run.succeededInstruments} 只股票的可用结果，请查看降级说明`,
      run.status === 'SUCCESS' ? 'success' : 'info');
      const refreshed = await api<QuantDataset>(`/api/quant/datasets/${realDataset.id}`);
      await onDatasetChanged(refreshed);
    } catch (error) {
      addToast(error instanceof Error ? error.message : '市场日线同步失败', 'error');
    } finally { setBusy(undefined); }
  }

  return <details className="quant-dataset-wizard" open={Boolean(realDataset)}>
    <summary>真实资金研究集</summary>
    {!realDataset ? <div className="quant-dataset-create">
      <label><span>数据集名称</span><input value={name} onChange={event => setName(event.target.value)} /></label>
      <p>这里只建立空容器。真实行情、point-in-time 股票池和资金历史都必须分别通过质量门禁。</p>
      <button type="button" className="quant-action" disabled={!name.trim() || busy === 'create'} onClick={create}>建立真实研究集</button>
    </div> : <div className="quant-dataset-steps">
      <div data-state={hasPitUniverse ? 'done' : 'pending'}>
        <span>01</span><strong>导入点时股票池</strong><small>{hasPitUniverse ? `${quality.universeEventCount} 条成员事件` : '先确定每个历史时点可投资的股票，避免未来数据穿越'}</small>
        {realDataset.status !== 'READY' && <><input type="file" accept="application/json,.json" aria-label="点时股票池 JSON" onChange={event => setUniverseFile(event.target.files?.[0])}/><button type="button" disabled={!universeFile || busy === 'universe'} onClick={() => importPartition('universe')}>校验并导入股票池</button></>}
      </div>
      <div data-state={Number(quality.barCount ?? 0) > 0 ? 'done' : 'pending'}>
        <span>02</span><strong>同步前复权真实日线</strong><small>{Number(quality.barCount ?? 0) > 0 ? `${quality.barCount} 条已登记` : '系统自动选源、断点续传并校验 OHLCV'}</small>
        {realDataset.status !== 'READY' && <>
          <button type="button" className="quant-sync-action" disabled={!hasPitUniverse || busy === 'sync'} onClick={syncMarketData}>
            {busy === 'sync' ? '正在逐只同步…' : '从市场数据同步日线'}
          </button>
          {!hasPitUniverse && <em className="quant-step-hint">完成第 01 步后即可自动同步</em>}
          <details className="quant-manual-import">
            <summary>导入真实日线（离线备用）</summary>
            <input type="file" accept="application/json,.json" aria-label="真实日线 JSON" onChange={event => setBarsFile(event.target.files?.[0])}/>
            <button type="button" disabled={!barsFile || busy === 'bars'} onClick={() => importPartition('bars')}>校验并导入日线</button>
          </details>
        </>}
        {latestSync && <div className="quant-sync-status" data-status={latestSync.status}>
          <strong>成功 {latestSync.succeededInstruments} / {latestSync.requestedInstruments} 只 · 新增 {latestSync.insertedRows} 条</strong>
          {latestSync.degradedInstruments > 0 && <span>部分数据源降级，已保留可用结果</span>}
          {latestSync.sourceSummary && <small>实际来源：{latestSync.sourceSummary}</small>}
          {latestSync.warningSummary && <small>{latestSync.warningSummary}</small>}
        </div>}
      </div>
      <div data-state={realDataset.status === 'READY' ? 'done' : 'pending'}>
        <span>03</span><strong>冻结资金分区</strong><small>只读取信息截止时间前已入库的资金事实</small>
        {realDataset.status !== 'READY' && <div className="quant-freeze-fields">
          <label><span>研究开始日</span><input type="date" aria-label="研究开始日" value={from} onChange={event => setFrom(event.target.value)} /></label>
          <label><span>研究结束日</span><input type="date" aria-label="研究结束日" value={to} onChange={event => setTo(event.target.value)} /></label>
          <label><span>信息截止时间</span><input type="datetime-local" aria-label="信息截止时间" value={asOfTime} onChange={event => setAsOfTime(event.target.value)} /></label>
          <button type="button" disabled={!from || !to || !asOfTime || busy === 'freeze'} onClick={freezeCapital}>冻结资金分区</button>
        </div>}
      </div>
      <p className="quant-dataset-gate" data-status={realDataset.status}><strong>{realDataset.status === 'READY' ? '质量通过，可以运行横截面诊断' : '尚未通过质量门禁'}</strong><small>{realDataset.qualitySummary || '等待导入服务端事实'}</small></p>
    </div>}
  </details>;
}
