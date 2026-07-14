type QuoteLike = { quoteValid: boolean };

export function preserveValidQuotes<T extends QuoteLike>(
  next: T[],
  previous: T[],
  keyOf: (item: T) => string
) {
  const previousByKey = new Map(previous.map((item) => [keyOf(item), item]));
  let degradedCount = 0;
  const items = next.map((item) => {
    const prior = previousByKey.get(keyOf(item));
    if (!item.quoteValid && prior?.quoteValid) {
      degradedCount += 1;
      return prior;
    }
    return item;
  });
  return { items, degradedCount };
}

export function formatPct(value?: number) {
  if (value === undefined || value === null) return '--';
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}

export function formatPrice(value?: number, digits = 2) {
  if (value === undefined || value === null) return '--';
  return value.toFixed(digits);
}

export function formatTurnover(value?: number) {
  if (value === undefined || value === null || value <= 0) return null;
  if (value >= 1e8) return `${(value / 1e8).toFixed(2)}亿`;
  if (value >= 1e4) return `${(value / 1e4).toFixed(0)}万`;
  return value.toFixed(0);
}

export function changeClass(value?: number) {
  if (value === undefined || value === null || value === 0) return 'watchlist-flat';
  return value > 0 ? 'watchlist-up' : 'watchlist-down';
}
