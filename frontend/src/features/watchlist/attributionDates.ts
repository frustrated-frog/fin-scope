/** 与服务端一致，以北京时间提供最近三个自然日，跨月/跨年也保持连续。 */
export function recentAttributionDates(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(now);
  const part = (type: string) => parts.find(value => value.type === type)!.value;
  const today = Date.UTC(Number(part('year')), Number(part('month')) - 1, Number(part('day')));
  return ['今天', '昨天', '前天'].map((label, index) => ({
    label, date: new Date(today - index * 86_400_000).toISOString().slice(0, 10)
  }));
}
