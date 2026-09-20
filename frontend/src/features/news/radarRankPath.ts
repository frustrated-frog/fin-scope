/** Missing production intervals remain gaps rather than invented rank transitions. */
export function radarRankPath(points: Array<{ observedAt: string; rankPosition: number }>) {
  const max = Math.max(2, ...points.map((point) => point.rankPosition));
  return points
    .map((point, index) => {
      const gap =
        index === 0 ||
        new Date(point.observedAt).getTime() - new Date(points[index - 1].observedAt).getTime() > 30 * 60 * 1000;
      const x = 16 + (index / Math.max(1, points.length - 1)) * 468;
      const y = 12 + ((point.rankPosition - 1) / (max - 1)) * 64;
      return `${gap ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
}
