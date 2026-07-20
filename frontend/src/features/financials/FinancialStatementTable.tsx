import { useMemo, useState } from 'react';

import { FinancialLineItem, FinancialUnit } from './financialTypes';
import {
  formatFinancialValue,
  keyFinancialLines,
  originLabels
} from './financialPresentation';

export function FinancialStatementTable({
  items,
  unit,
  title,
  compact = false,
  periodMode = 'CUMULATIVE'
}: {
  items: FinancialLineItem[];
  unit: FinancialUnit;
  title: string;
  compact?: boolean;
  periodMode?: 'CUMULATIVE' | 'QUARTER';
}) {
  const [showAll, setShowAll] = useState(false);
  const periodItems = useMemo(() => {
    const hasFlowValues = items.some((item) => item.periodRole === 'CURRENT_YTD'
      || item.periodRole === 'CURRENT_QUARTER');
    if (!hasFlowValues) return items;
    const role = periodMode === 'QUARTER' ? 'CURRENT_QUARTER' : 'CURRENT_YTD';
    return items.filter((item) => item.periodRole === role);
  }, [items, periodMode]);
  const rows = useMemo(
    () => compact || !showAll ? keyFinancialLines(periodItems) : periodItems,
    [compact, periodItems, showAll]
  );

  return (
    <section className={compact ? 'financials-statement-card compact' : 'financials-statement-card'}>
      <header>
        <div>
          <p className="financials-section-kicker">Statement ledger</p>
          <h4>{title}</h4>
        </div>
        {!compact && periodItems.length > keyFinancialLines(periodItems).length && (
          <button className="ghost-button" type="button" onClick={() => setShowAll((value) => !value)}>
            {showAll ? '只看关键科目' : `查看全部 ${periodItems.length} 项`}
          </button>
        )}
      </header>
      {rows.length ? (
        <div className="financials-table-wrap">
          <table className="financials-table">
            <thead>
              <tr>
                <th>科目</th>
                <th>金额</th>
                <th>口径</th>
                <th>质量</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((item) => (
                <tr key={`${item.id}-${item.periodRole}`}>
                  <td>
                    <strong>{item.sourceLabel}</strong>
                    <small>{item.conceptCode || item.sourceField || '原始披露科目'}</small>
                  </td>
                  <td className="financials-number">
                    {formatFinancialValue(item.normalizedValue, unit)}
                  </td>
                  <td>
                    <span className={`financials-origin ${item.valueOrigin.toLowerCase()}`}>
                      {originLabels[item.valueOrigin]}
                    </span>
                  </td>
                  <td>
                    <span className={`financials-quality-dot ${item.qualityStatus.toLowerCase()}`} />
                    {item.qualityStatus === 'FRESH' ? '可核对' : '需关注'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="financials-empty-inline">本表暂无可展示的结构化科目。</div>
      )}
    </section>
  );
}
