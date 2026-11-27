export type Period = 'daily' | 'weekly' | 'monthly' | 'quarterly';
export const periods: Record<Period, string> = {
  daily: '每日检查',
  weekly: '每周实验',
  monthly: '月度复盘',
  quarterly: '季度体检',
};
export const learningQuestions: Record<Period, string> = {
  daily: '今天运行正常吗？成交和信号差在哪里？',
  weekly: '表现变化来自执行、市场，还是策略？',
  monthly: '哪些假设获得支持，下个月要验证什么？',
  quarterly: '当前证据足以保留、改进或暂停策略吗？',
};
export interface StudyEntry {
  id: number;
  day: string;
  created: string;
  closed: string | null;
  period: Period;
  hypothesis: string;
  criterion: string;
  source: string;
  verdict: { outcome: string; reason: string; experiment?: number } | null;
}
interface WindowStats {
  start: string;
  end: string;
  first?: string;
  last?: string;
  days: number;
  net: number | null;
  market: number | null;
  excess: number | null;
}
interface Check {
  version: number;
  name: string;
  points: number;
  health: { due: number; executed: number; missed: number; missingTimes: string[] } | null;
  remaining: number;
  signals: number;
  fills: number;
  rejected: number;
  unresolved: number;
  carried: number;
  fees: number;
  feeBps: number | null;
  priced: number;
  slippage: number | null;
  reasons: { reason: string; count: number }[];
}
interface RiskScan {
  at: string | null;
  sharpe: number | null;
  returnDays: number;
  drawdown: number | null;
  comparison?: WindowStats;
}
interface WeekScan extends WindowStats {
  risk: RiskScan;
  observations: number;
  execution: Pick<Check, 'fills' | 'rejected' | 'fees'>;
}
interface Fold {
  trainFrom: string;
  trainTo: string;
  testFrom: string;
  testTo: string;
  selected: number;
  testReturn: number;
  referenceReturn: number;
}
export interface StudyReport {
  day: string;
  version: number;
  name: string;
  accounts: Check[];
  daily: {
    preopen: RiskScan;
    archivedFills?: { fills: number; fees: number; matched: number | null } | null;
    feeBudget: number | null;
    at: string | null;
    sharpe: number | null;
    returnDays: number;
    drawdown: number | null;
    limit: number | null;
    comparison: WindowStats;
  };
  weekly: { current: WeekScan; previous: WeekScan };
  monthly: {
    refutations: { hypothesis: string; reason: string }[];
    closes: number;
    marketCloses: number;
    month: string;
    opened: number;
    pending: number;
    supported: number;
    refuted: number;
    insufficient: number;
  };
  quarterly: {
    forward: WindowStats;
    historyDays: number;
    years: WindowStats[];
    positive: number;
    negative: number;
    flat: number;
    distribution: { label: string; days: number }[];
    validation: { versions: number[]; commonDays: number; folds: Fold[] };
    backtest: {
      net: number | null;
      benchmark: number | null;
      fees: number | null;
      first: string | null;
      last: string | null;
    };
    pairing: { netReturn: number; fees: number } | null;
    comparison: number | null;
    holdout: string | null;
    settings: Record<string, unknown>;
  };
  entries: StudyEntry[];
}
export const pct = (v: number | null | undefined) => (v == null ? '待积累' : (v * 100).toFixed(2) + '%');
const money = (v: number) =>
  '¥ ' + v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
function Metrics({ rows }: { rows: [string, string | number][] }) {
  return (
    <dl className="learn-metrics">
      {rows.map(([key, value]) => (
        <div key={key}>
          <dt>{key}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}
function Windows({ rows }: { rows: [string, WindowStats][] }) {
  return (
    <div className="learn-scroll">
      <table>
        <thead>
          <tr>
            <th>观察区间</th>
            <th>覆盖日</th>
            <th>策略</th>
            <th>沪深 300</th>
            <th>收益差</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(([name, w]) => (
            <tr key={name}>
              <th>
                {name}
                <small>{w.first ? w.first + ' → ' + w.last : w.start + ' 起'}</small>
              </th>
              <td>{w.days}</td>
              <td>{pct(w.net)}</td>
              <td>{pct(w.market)}</td>
              <td>{w.excess == null ? '—' : (100 * w.excess).toFixed(2) + ' 个百分点'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function LearningEvidence({
  report: r,
  period,
  account,
  onChoose,
}: {
  report: StudyReport;
  period: Period;
  account?: (id: number, day: string) => void;
  onChoose?: (id: number) => void;
}) {
  const c = r.accounts.find((a) => a.version === r.version);
  if (period === 'daily') {
    const prior = r.daily.preopen;
    return (
      <>
        {c?.points ? (
          <div className="learn-callout">
            <strong>{c.health ? c.health.executed + ' / ' + c.health.due : c.points}</strong>
            <div>
              <b>{c.health ? '到期时点已运行' : '已记录的观测'}</b>
              <small>
                {c.rejected
                  ? '有 ' + c.rejected + ' 笔未成交。最常见原因是' + (c.reasons[0]?.reason ?? '待核对') + '。'
                  : '该日已记录 ' + c.fills + ' 笔成交。'}
              </small>
            </div>
          </div>
        ) : null}
        <h5>盘前 · 上一个可见收盘</h5>
        {prior?.at ? (
          <>
            <small>截至 {prior.at.slice(0, 10)} 收盘</small>
            <Metrics
              rows={[
                ['20 日滚动夏普', prior.sharpe?.toFixed(2) ?? '样本 ' + prior.returnDays + '/20'],
                ['相对高点回撤', pct(prior.drawdown)],
                ['策略回撤阈值', r.daily.limit ? pct(r.daily.limit) : '未设置'],
                [
                  '相对沪深 300',
                  prior.comparison?.excess != null
                    ? (prior.comparison.excess * 100).toFixed(2) + ' 个百分点'
                    : '待积累',
                ],
              ]}
            />
          </>
        ) : (
          <p>尚无更早的模拟收盘记录。</p>
        )}
        <h5>盘中 · 账户巡检</h5>
        <div className="learn-scroll">
          <table>
            <thead>
              <tr>
                <th>策略</th>
                <th>已运行 / 到期</th>
                <th>未成交</th>
                <th>检查结果</th>
              </tr>
            </thead>
            <tbody>
              {r.accounts.map((a) => (
                <tr key={a.version}>
                  <th>
                    {onChoose ? (
                      <button aria-pressed={a.version === r.version} onClick={() => onChoose(a.version)}>
                        {a.name}
                      </button>
                    ) : (
                      a.name
                    )}
                  </th>
                  <td>{a.health ? a.health.executed + ' / ' + a.health.due : '暂无到期记录'}</td>
                  <td>{a.points ? a.rejected + ' 笔' : '—'}</td>
                  <td>
                    {a.health?.missed
                      ? '漏跑 ' + a.health.missed + ' 次'
                      : a.rejected
                        ? '检查未成交原因'
                        : a.remaining
                          ? '调仓尚有差额'
                          : a.points
                            ? '已记录'
                            : '等待观测'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!r.accounts.length && <p>该日期没有可检查的模拟账户。</p>}
        <h5>盘后 · {r.name}</h5>
        {c?.points ? (
          <>
            <Metrics
              rows={[
                ['当日信号', c.signals + ' 笔'],
                ['实际成交', c.fills + ' 笔'],
                ['未成交', c.rejected + ' 笔'],
                ['佣金及税费', money(c.fees)],
              ]}
            />
            <p>
              按信号时间匹配，当日仍无回报 {c.unresolved} 笔，另有 {c.carried} 笔来自此前的信号。
            </p>
            <small>
              该策略单笔费率上限 {r.daily.feeBudget == null ? '未设置' : r.daily.feeBudget + ' 基点'}
              。当日已成交金额加权费率 {c.feeBps == null ? '—' : c.feeBps.toFixed(2) + ' 基点'}
              。模拟成交相对撮合开盘价的滑点{' '}
              {c.fills === 0
                ? '当日无成交'
                : c.slippage == null
                  ? '缺少参考价（覆盖 ' + c.priced + '/' + c.fills + ' 笔）'
                  : money(c.slippage)}
              。
            </small>
          </>
        ) : (
          <p>该版本在所选日期没有模拟观测。</p>
        )}
        <details className="learn-disclosure">
          <summary>与冻结回测的同日成交核对</summary>
          {r.daily.archivedFills ? (
            <>
              <Metrics
                rows={[
                  ['历史模拟成交', r.daily.archivedFills.fills + ' 笔'],
                  ['历史模拟费用', money(r.daily.archivedFills.fees)],
                  [
                    '与前向成交匹配',
                    r.daily.archivedFills.matched == null
                      ? '无当日模拟观测'
                      : r.daily.archivedFills.matched + ' 笔',
                  ],
                ]}
              />
              <small>按时点、股票、方向及股数匹配。两个账本的期初持仓可能不同。</small>
            </>
          ) : (
            <p>冻结回测中缺少这个交易日的逐笔账本，暂时只能核对当日信号与模拟成交。</p>
          )}
        </details>
        <details className="learn-disclosure">
          <summary>收盘风险指标与沪深 300 对照</summary>
          <Metrics
            rows={[
              ['20 日滚动夏普', r.daily.sharpe?.toFixed(2) ?? '样本不足'],
              ['有效日收益', r.daily.returnDays + ' / 20'],
              ['相对日末高点回撤', pct(r.daily.drawdown)],
              ['策略回撤阈值', r.daily.limit ? pct(r.daily.limit) : '未设置'],
            ]}
          />
          {r.daily.limit != null && r.daily.drawdown != null && r.daily.drawdown >= r.daily.limit && (
            <strong>观察回撤达到设定阈值，请核对当时的风控决定。</strong>
          )}
          <Windows rows={[['当日收盘', r.daily.comparison]]} />
        </details>
        {account && c && (
          <button className="learn-link" onClick={() => account(r.version, r.day)}>
            查看该日信号、成交与 AI 复盘 →
          </button>
        )}
      </>
    );
  }
  if (period === 'weekly') {
    return (
      <>
        <h5>两周表现 · 前向模拟</h5>
        <Windows
          rows={[
            ['本周', r.weekly.current],
            ['上周', r.weekly.previous],
          ]}
        />
        <p>取实际覆盖区间的收盘收益。缺少前一交易日时从首个观测收盘起算，覆盖很短时只作观察。</p>
        <div className="learn-scroll">
          <table>
            <thead>
              <tr>
                <th>周末指标</th>
                <th>滚动夏普</th>
                <th>收盘回撤</th>
                <th>成交 / 未成交</th>
                <th>费用</th>
              </tr>
            </thead>
            <tbody>
              {(
                [
                  ['本周', r.weekly.current],
                  ['上周', r.weekly.previous],
                ] as const
              ).map(([label, w]) => (
                <tr key={label}>
                  <th>{label}</th>
                  <td>{w.risk.sharpe?.toFixed(2) ?? '样本不足'}</td>
                  <td>{pct(w.risk.drawdown)}</td>
                  <td>{w.observations ? w.execution.fills + ' / ' + w.execution.rejected : '—'}</td>
                  <td>{w.observations ? money(w.execution.fees) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <ol className="learn-steps">
          <li>
            <b>先排查执行</b>本周
            {r.weekly.current.observations
              ? '有 ' + r.weekly.current.execution.rejected + ' 笔未成交，逐日查看未成交原因。'
              : '尚无执行样本。'}
          </li>
          <li>
            <b>再看市场</b>对齐同一首尾日期的沪深 300，观察下跌是否也出现在基准上。
          </li>
          <li>
            <b>最后验证策略</b>用 IC
            看因子排序与未来收益的关系，用分组检验是否稳定。改变一个条件并先写验收标准。
          </li>
        </ol>
      </>
    );
  }
  if (period === 'monthly') {
    const m = r.monthly,
      decided = m.supported + m.refuted;
    return (
      <>
        <h5>{m.month} · 研究决定</h5>
        <Metrics
          rows={[
            ['本月新登记', m.opened],
            ['本月支持 / 推翻', m.supported + ' / ' + m.refuted],
            ['本月证据不足', m.insufficient],
            ['累计待验证', m.pending],
          ]}
        />
        <p>
          已明确判定的假设中，支持率 {decided ? pct(m.supported / decided) : '暂无样本'}
          。按结论登记月统计，证据不足单列。
        </p>
        <h5>推翻了什么？</h5>
        {m.refutations.map((e, i) => (
          <p key={i}>
            {e.hypothesis} · {e.reason}
          </p>
        ))}
        {!m.refuted && <p>本月没有已登记的推翻结论。</p>}
        <h5>下月方向与工具升级</h5>
        <Metrics
          rows={[
            ['模拟收盘记录', m.closes + ' 日'],
            ['本地沪深 300 收盘', m.marketCloses + ' 日'],
          ]}
        />
        <p>
          回看未完成记录和数据覆盖，挑一个重复出现的问题。把需要补的行情、模板或因子写入下一条记录，并约定怎样验收。
        </p>
      </>
    );
  }
  const q = r.quarterly;
  return (
    <>
      <h5>本季度模拟 · 同期市场基准</h5>
      <Windows rows={[['本季', q.forward]]} />
      <h5>冻结回测 · 按年拆解</h5>
      {q.years.length ? (
        <Windows rows={q.years.map((w) => [w.start.slice(0, 4), w])} />
      ) : (
        <p>没有历史净值。</p>
      )}
      <Metrics
        rows={[
          ['回测扣费收益', pct(q.backtest.net)],
          ['回测内置基准', pct(q.backtest.benchmark)],
          ['回测费用', q.backtest.fees == null ? '—' : money(q.backtest.fees)],
        ]}
      />
      <h5>日收益分布</h5>
      <div className="learn-bars">
        {q.distribution.map((bin) => (
          <div key={bin.label}>
            <span>{bin.label}</span>
            <meter
              aria-label={bin.label}
              value={bin.days}
              min={0}
              max={Math.max(1, ...q.distribution.map((b) => b.days))}
            />
            <span>{bin.days} 日</span>
          </div>
        ))}
      </div>
      <Metrics
        rows={[
          ['回测观测日', q.historyDays],
          ['上涨日', q.positive],
          ['下跌日', q.negative],
          ['持平日', q.flat],
        ]}
      />
      <dl className="learn-checklist">
        <div>
          <dt>参数敏感性</dt>
          <dd>
            {q.comparison && q.pairing
              ? '同数据原实验 #' +
                q.comparison +
                ' 扣费收益 ' +
                pct(q.pairing.netReturn) +
                '，费用 ' +
                money(q.pairing.fees) +
                '。'
              : '该版本尚无已登记的同数据参数对照。'}
          </dd>
        </div>
        <div>
          <dt>样本外检验</dt>
          <dd>{q.holdout ? '冻结回测标记的验证段开始于 ' + q.holdout : '该版本无留出段标记。'}</dd>
        </div>
        <div>
          <dt>规则复杂度</dt>
          <dd>
            {Object.keys(q.settings).length
              ? '组件配置 ' + Object.keys(q.settings).length + ' 项，逐项检查为何保留。'
              : '打开原策略，检查因子数量、买卖条件与风险规则。'}
          </dd>
        </div>
        <div>
          <dt>阅读与决策</dt>
          <dd>选择一份论文或行业报告，将出处、想验证的变化及保留或暂停策略的理由记入台账。</dd>
        </div>
      </dl>
      <h5>已有回测版本的滚动选择</h5>
      {q.validation.folds.length ? (
        <>
          <p>
            {q.validation.versions.map((id) => '#' + id).join('、')}{' '}
            使用相同快照、股票池、本金与撮合引擎。每折用前段扣费收益选择版本，再观察后续五个收盘收益。
          </p>
          <div className="learn-scroll">
            <table>
              <thead>
                <tr>
                  <th>选择区间 → 验证区间</th>
                  <th>选中</th>
                  <th>验证收益</th>
                  <th>当前版本</th>
                </tr>
              </thead>
              <tbody>
                {q.validation.folds.map((f) => (
                  <tr key={f.testFrom}>
                    <th>
                      {f.trainFrom} 至 {f.trainTo}
                      <small>
                        {f.testFrom} 至 {f.testTo}
                      </small>
                    </th>
                    <td>#{f.selected}</td>
                    <td>{pct(f.testReturn)}</td>
                    <td>{pct(f.referenceReturn)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <small>
            各段沿用各版本原账本；候选版本来自已有研究。分折检验的是选版本的规则，不包含切换账户的成本或模型重新训练。
          </small>
        </>
      ) : (
        <p>
          需要至少两个同口径经典实验，且共同收盘数至少 16 个。现有 {q.validation.versions.length} 个可比版本。
        </p>
      )}
    </>
  );
}
