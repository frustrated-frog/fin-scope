import { useState } from 'react';
import { defaultThemes, loadThemes, parseThemeMembers, pct, ratio, shanghaiDate, stockReturn, summarizeTheme, THEME_STORAGE_KEY } from './marketResearch';
import { ResearchStockActions } from './ResearchStockActions';
import type { ResearchPeriod, ResearchStock, ResearchTheme } from './marketResearchTypes';

type Props = { businessDate: string; stocks: ResearchStock[]; onOpenStock?: (code: string) => void };
export function ThemeResearchPanel({ businessDate, stocks, onOpenStock }: Props) {
  const [initial] = useState(() => {
    try {
      return { themes: loadThemes(), error: '' };
    } catch {
      return { themes: defaultThemes(), error: '本地主题读取失败。原配置尚未覆盖；请检查浏览器存储，或编辑主题后重新保存。' };
    }
  });
  const [themes, setThemes] = useState(initial.themes);
  const [error, setError] = useState(initial.error);
  const [period, setPeriod] = useState<ResearchPeriod>(5);
  const [editing, setEditing] = useState<string>();
  const [name, setName] = useState('');
  const [membersText, setMembersText] = useState('');
  const stockMap = new Map(stocks.map(stock => [stock.instrumentCode, stock]));
  function edit(theme: ResearchTheme) {
    setEditing(theme.id);
    setName(theme.name);
    setMembersText(theme.members.map(member => `${member.instrumentCode},${member.name},${member.segment},${member.evidence}`).join('\n'));
    setError('');
  }
  function save() {
    try {
      if (!name.trim() || name.trim().length > 40) {
        throw new Error('主题名称需为1至40字');
      }
      const members = parseThemeMembers(membersText);
      const next = themes.map(theme => theme.id === editing ? { ...theme, name: name.trim(), members, effectiveDate: shanghaiDate() } : theme);
      localStorage.setItem(THEME_STORAGE_KEY, JSON.stringify(next));
      setThemes(next);
      setEditing(undefined);
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '保存失败，请检查浏览器存储是否可用');
    }
  }
  return <section className="mp-research-section" aria-label="主线与题材地图">
    <header className="mp-research-heading"><div><span>用自己的产业关系组织研究</span><h3>主线与题材地图</h3></div><label>观察周期<select value={period} onChange={event => setPeriod(Number(event.target.value) as ResearchPeriod)}><option value={1}>今日</option><option value={5}>近5日</option><option value={20}>近20日</option></select></label></header>
    <p className="mp-research-note">成员由你维护，保存在当前浏览器。模板不预设公司归属。展示当前篮子的等权区间表现；有效成员至少3只且覆盖率≥80%才输出汇总，不代表历史成分策略收益。</p>
    {error && <p role="alert" className="mp-research-error">{error}</p>}
    {editing && <form className="mp-theme-editor" onSubmit={event => { event.preventDefault(); save(); }}>
      <label>主题名称<input maxLength={40} value={name} onChange={event => setName(event.target.value)} /></label>
      <label>主题成员<textarea rows={6} value={membersText} onChange={event => setMembersText(event.target.value)} placeholder="每行填写：代码,名称,产业环节,归属依据" /></label>
      <p className="mp-research-note">每行：600519.SH,贵州茅台,白酒,你的研究笔记或来源。最多50只；这是输入格式示例。保存后成员关系自 {shanghaiDate()} 生效，早于该日期的历史截面不计算。</p>
      <div className="mp-stock-actions"><button type="submit">保存主题</button><button type="button" onClick={() => setEditing(undefined)}>取消编辑</button></div>
    </form>}
    <div className="mp-theme-grid">{themes.map(theme => {
      const summary = summarizeTheme(theme, stocks, businessDate, period);
      const segments = [...new Set(theme.members.map(member => member.segment))];
      return <article className="mp-theme-card" key={theme.id}><header><h4>{theme.name}</h4><button type="button" onClick={() => edit(theme)}>维护成员</button></header>
        <dl><div><dt>{period === 1 ? '今日' : `${period}日`}等权表现</dt><dd>{pct(summary.returnPct)}</dd></div><div><dt>有效成员</dt><dd>{summary.validCount} / {theme.members.length}</dd></div></dl>
        {!theme.members.length ? <p className="mp-research-note">添加你关注的公司、产业环节和归属依据，建立主题地图。</p> : <>
          <p className="mp-research-note">成员关系自 {theme.effectiveDate} 生效 · 覆盖 {ratio(summary.coverage)} · 区间上涨比例 {ratio(summary.advanceRatio)}</p>
          {!summary.effective && <p className="mp-research-note">该历史截面早于成员关系生效日期。</p>}
          {summary.effective && summary.returnPct == null && <p className="mp-research-note">样本覆盖不足。可先在自选中加载成员日K，再重试样本加载。</p>}
          <div className="mp-theme-segments">{segments.map(segment => {
            const members = theme.members.filter(member => member.segment === segment);
            const metrics = summarizeTheme({ ...theme, members }, stocks, businessDate, period);
            return <details key={segment}><summary><strong>{segment}</strong><span>{pct(metrics.returnPct)} · {metrics.validCount}/{members.length}只</span></summary>
              <p className="mp-research-note">区间上涨比例 {ratio(metrics.advanceRatio)} · 今日成交额 {metrics.amount == null ? '—' : `${(metrics.amount / 100000000).toFixed(2)}亿元`}</p>
              <ul className="mp-research-members">{members.map(member => <li key={member.instrumentCode}><div><strong>{member.name}</strong><span>{summary.effective ? pct(stockReturn(stockMap.get(member.instrumentCode), period)) : '—'}</span></div><small>{member.instrumentCode} · 归属依据：{member.evidence}</small><ResearchStockActions code={member.instrumentCode} onOpenStock={onOpenStock} /></li>)}</ul>
            </details>;
          })}</div>
        </>}
      </article>;
    })}</div>
  </section>;
}
