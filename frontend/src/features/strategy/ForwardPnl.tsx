export interface ObservedPnl {
  fromAt: string | null;
  toAt: string;
  opening: number;
  closing: number;
  net: number;
  fees: number;
  beforeFees: number;
  stocks: { code: string; net: number; fees: number }[] | null;
  receivableChange: number | null;
  residual: number | null;
  missedBars: number;
}
const amount = (v: number) =>
  v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const moment = (at: string) => at.slice(5, 16).replace('T', ' ');
export function ForwardPnl({ value: r, name }: { value: ObservedPnl; name: (code: string) => string }) {
  return (
    <section className="att-terminal forward-pnl">
      <div className="att-card">
        <header>
          <h4>模拟盈亏归因</h4>
          <small>
            {r.fromAt ? moment(r.fromAt) : '账户开始'} 至 {moment(r.toAt)}
          </small>
        </header>
        <div className="wf-kpis">
          <div>
            <span>扣费前盈亏（元）</span>
            <strong>{amount(r.beforeFees)}</strong>
          </div>
          <div>
            <span>成交费用（元）</span>
            <strong>{amount(r.fees)}</strong>
          </div>
          <div>
            <span>扣费后盈亏（元）</span>
            <strong>{amount(r.net)}</strong>
          </div>
        </div>
        <p>
          资产从 ¥ {amount(r.opening)} 变为 ¥ {amount(r.closing)}。费用按实际模拟成交计入。
          {r.missedBars > 0 && ' 本段记录到 ' + r.missedBars + ' 个缺失的盘中时点。'}
        </p>
        {r.stocks === null ? (
          <p>这一段的起止持仓市值记录不全，仅展示账户层面的盈亏。</p>
        ) : (
          <details>
            <summary>查看个股贡献与核对差额</summary>
            {r.stocks.length > 0 ? (
              <div className="att-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>股票名称</th>
                      <th>扣费后贡献（元）</th>
                      <th>其中费用（元）</th>
                    </tr>
                  </thead>
                  <tbody>
                    {r.stocks.map((stock) => (
                      <tr key={stock.code}>
                        <th>{name(stock.code)}</th>
                        <td>{amount(stock.net)}</td>
                        <td>{amount(stock.fees)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p>本段没有持仓或成交。</p>
            )}
            <p>
              应收权益变动 ¥ {amount(r.receivableChange ?? 0)} · 账本核对差额 ¥ {amount(r.residual ?? 0)}
            </p>
          </details>
        )}
      </div>
    </section>
  );
}
