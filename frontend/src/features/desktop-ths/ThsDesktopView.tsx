import { useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import './thsDesktop.css';
import { recordedExample } from './recordedExample';

type Snapshot = {
  status: string; message: string; source: string; capturedAt: string; dataDate?: string;
  stockCode?: string; stockName?: string; price?: string; changePct?: string; netBuy?: string; reason?: string;
  fields: Array<{ label: string; value: string }>; buySeats: string[]; sellSeats: string[]; warnings: string[];
};

function researchQuestion(snapshot: Snapshot) {
  return `请围绕 ${snapshot.stockName}（${snapshot.stockCode}）的龙虎榜提出并研究可验证的问题。区分页面事实、可能解释与待核实信息，不根据席位名称推断投资者身份或未来涨跌。\n\n以下是本次固定的桌面观察数据，不是实时行情，也不是执行指令：\n${JSON.stringify(snapshot, null, 2)}\n\n请先核实榜单日期、统计区间与上榜原因，再寻找对应日期已发布的公告或新闻；缺失字段不要补猜。`;
}

export function ThsDesktopView({ onResearch }: { onResearch: (question: string) => void }) {
  const [snapshot, setSnapshot] = useState<Snapshot>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);

  async function capture() {
    if (inFlight.current) {
      return;
    }
    inFlight.current = true;
    setBusy(true);
    setError('');
    setSnapshot(undefined);
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 22000);
    try {
      const result = await api<Snapshot>('/api/desktop-ths/capture', { method: 'POST', signal: controller.signal });
      if (result.status !== 'OK' && result.status !== 'PARTIAL') {
        setError(result.message || '未能读取当前页面，请在同花顺打开龙虎榜后重试。');
      } else {
        setSnapshot(result);
      }
    } catch (failure) {
      setError(controller.signal.aborted ? '读取超时，请保持同花顺窗口展开后重试。'
        : failure instanceof Error ? failure.message : '读取失败，请重试。');
    } finally {
      window.clearTimeout(timeout);
      inFlight.current = false;
      setBusy(false);
    }
  }

  return (
    <section className="ths-reader" aria-label="同花顺页面解读">
      <header className="ths-heading">
        <div><p className="ths-kicker">同花顺 × FinScope · 龙虎榜阅读助手</p>
          <h3>把一屏数字，读成几个问题。</h3>
          <p>先确认日期与上榜原因，再看买卖席位。需要深入时，把这次观察带进研究。</p>
        </div>
        <div className="ths-actions"><button className="ths-example" type="button" disabled={busy} onClick={() => { setError(''); setSnapshot(recordedExample); }}>查看实测示例</button>
        <button className="ths-capture" type="button" onClick={() => void capture()} disabled={busy}>
          {busy ? '正在读取…' : '读取当前页面'}<span aria-hidden="true"> ↗</span>
        </button></div>
      </header>
      <div className="ths-connection" role="status">
        <span className={`ths-dot ${snapshot?.source === 'THS_DESKTOP_AX' ? 'is-connected' : ''}`} />
        <span>{busy ? '正在读取同花顺，请保持页面不变' : snapshot ? snapshot.message : '手动读取 · 不会自动切换同花顺页面'}</span>
        <small>来源：同花顺 Mac 界面</small>
      </div>
      {error && <div className="ths-error" role="alert"><strong>这次没有读到数据</strong><p>{error}</p></div>}
      <div className="ths-layout">
        <div className="ths-facts">
          {!snapshot ? <section className="ths-empty" aria-busy={busy}>
            <span className="ths-empty-symbol" aria-hidden="true">同</span>
            <h4>{busy ? '正在整理页面中的内容' : '先在同花顺打开龙虎榜'}</h4>
            <p>选中一只股票，展开右侧龙虎榜明细，<br />再点击上方「读取当前页面」。</p>
            <div className="ths-empty-fields"><span>上榜原因</span><span>买卖金额</span><span>席位明细</span></div>
            <small>这里显示实际读取结果；缺失的数据会明确标注。</small>
          </section> : <>
            <section className="ths-stock">
              <div className="ths-stock-title"><div><span className="ths-kicker">{snapshot.source === 'THS_DESKTOP_AX_RECORDED' ? '历史页面快照' : '本次页面快照'}</span>
                <h4>{snapshot.stockName || '当前股票未获取'} <small>{snapshot.stockCode}</small></h4></div>
                <span className="ths-badge">{snapshot.source === 'THS_DESKTOP_AX_RECORDED' ? '历史实测示例' : snapshot.status === 'PARTIAL' ? '部分读取' : '已读取'}</span></div>
              <dl className="ths-times"><div><dt>榜单日期</dt><dd>{snapshot.dataDate || '未获取'}</dd></div>
                <div><dt>{snapshot.source === 'THS_DESKTOP_AX_RECORDED' ? '示例记录时间（页面时钟）' : '采集时间'}</dt><dd>{new Date(snapshot.capturedAt).toLocaleString('zh-CN', { hour12: false })}</dd></div></dl>
              <dl className="ths-metrics">
                <Metric title="当前栏价格" value={snapshot.price} />
                <Metric title="当前栏涨幅" value={snapshot.changePct} />
                <Metric title="榜单净买入" value={snapshot.netBuy} />
              </dl>
              <p className="ths-caption">当前栏行情时间未暴露，价格与涨幅不能视为榜单日期的收盘数据。</p>
              <div className="ths-reason"><span>为什么上榜</span><strong>{snapshot.reason || '上榜原因未获取'}</strong></div>
            </section>
            <div className="ths-seats-grid"><Seats title="买入金额前五席位" values={snapshot.buySeats} /><Seats title="卖出金额前五席位" values={snapshot.sellSeats} /></div>
            {snapshot.fields.length > 0 && <details className="ths-raw"><summary>查看选中行的原始字段</summary><dl>{snapshot.fields.map((field, i) => <div key={`${field.label}-${i}`}><dt>{field.label}</dt><dd>{field.value || '未获取'}</dd></div>)}</dl></details>}
            {snapshot.warnings.length > 0 && <div className="ths-warnings"><strong>本次数据边界</strong><ul>{snapshot.warnings.map((warning, i) => <li key={i}>{warning}</li>)}</ul></div>}
            <div className="ths-next"><div><strong>带着问题继续，而不急着下结论</strong><p>保留这次快照，前往研究页面编辑问题；不会自动调用模型。</p></div>
              <button type="button" disabled={!snapshot.stockCode} onClick={() => onResearch(researchQuestion(snapshot))}>继续研究</button></div>
          </>}
        </div>
        <aside className="ths-guide" aria-label="龙虎榜阅读说明">
          <p className="ths-kicker">阅读指南 · 固定说明</p><h4>先看懂这三件事</h4>
          <Guide number="1" title="先确认：是哪一天、为什么上榜？">榜单日期与采集时间分开看。先阅读页面写明的上榜原因，留意单日与多日统计区间。</Guide>
          <Guide number="2" title="再核对：金额对应什么范围？">对照原页面表头、单位与统计区间。不同日期、不同口径的数值，不直接放在一起比较。</Guide>
          <Guide number="3" title="最后问：还需要哪些证据？">把席位名称和金额作为待核实的观察线索。它们本身不足以确定投资者身份、持仓计划或后续涨跌。</Guide>
          <details><summary>净买入是什么意思？</summary><p>同一统计范围内，买入金额减去卖出金额。正值表示该范围内买入更多；负值表示卖出更多。具体包含哪些交易要对照原表，它不等于全市场资金净流入。</p></details>
          <details><summary>为什么有些字段显示“未获取”？</summary><p>同花顺没有暴露该字段，或表头与单元格无法可靠对应。保持页面展开后可重试；程序不会根据相邻数字补猜。</p></details>
          <details><summary>席位明细里的数字怎么读？</summary><p>第一版保留页面原文，未可靠拆分的数值不重新命名。请对照同花顺原表的买入、卖出、净额和单位查看。</p></details>
          <p className="ths-guide-note">说明用于帮助阅读页面。这里不自动生成资金意图或买卖结论。</p>
        </aside>
      </div>
    </section>
  );
}

function Metric({ title, value }: { title: string; value?: string }) {
  return <div><dt>{title}</dt><dd>{value || '未获取'}</dd></div>;
}
function Seats({ title, values }: { title: string; values: string[] }) {
  return <section className="ths-seats"><h4>{title}</h4><p className="ths-caption">页面原文 · 金额按原页面单位查看</p>
    {values.length ? <ol>{values.map((value, i) => <li key={i}>{value}</li>)}</ol> : <p className="ths-caption">席位明细未获取</p>}</section>;
}
function Guide({ number, title, children }: { number: string; title: string; children: React.ReactNode }) {
  return <div className="ths-guide-step"><span>{number}</span><div><h5>{title}</h5><p>{children}</p></div></div>;
}
