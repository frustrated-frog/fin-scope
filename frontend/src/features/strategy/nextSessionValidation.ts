import type { NextSessionPredictionRecord } from './quantTypes';

const average = (values: number[]) => values.reduce((sum, value) => sum + value, 0) / values.length;

export function summarizeNextSession(records: NextSessionPredictionRecord[]) {
  const groups = new Map<string, NextSessionPredictionRecord[]>();
  for (const record of records) {
    const version = record.prediction.modelVersion;
    groups.set(version, [...(groups.get(version) ?? []), record]);
  }
  return [...groups].map(([version, rows]) => {
    // Keep the earliest loaded freeze per stock/target/version, before checking its outcome.
    const unique = new Map<string, NextSessionPredictionRecord>();
    for (const row of [...rows].sort((a, b) => a.prediction.generatedAt.localeCompare(b.prediction.generatedAt) || a.id - b.id)) {
      const key = `${row.instrumentCode}/${row.prediction.targetDate}`;
      if (!unique.has(key)) {
        unique.set(key, row);
      }
    }
    const matured = [...unique.values()].filter(row => row.status === 'MATURED'
      && row.actualReturn != null && Number.isFinite(row.actualReturn)
      && row.prediction.upProbability != null && Number.isFinite(row.prediction.upProbability)
      && row.prediction.upProbability >= 0 && row.prediction.upProbability <= 1);
    const dates = [...new Set(matured.map(row => row.prediction.targetDate))];
    const daily = dates.map(day => {
      const observations = matured.filter(row => row.prediction.targetDate === day);
      return {
        accuracy: average(observations.map(row => Number((row.prediction.upProbability! >= .5) === (row.actualReturn! > 0)))),
        brier: average(observations.map(row => (row.prediction.upProbability! - Number(row.actualReturn! > 0)) ** 2)),
      };
    });
    const bins = [0, .4, .5, .6, .7].map((lower, index, bounds) => {
      const upper = bounds[index + 1] ?? 1;
      const selected = matured.filter(row => row.prediction.upProbability! >= lower
        && (row.prediction.upProbability! < upper || upper === 1));
      const binDates = [...new Set(selected.map(row => row.prediction.targetDate))];
      const perDay = binDates.map(day => {
        const values = selected.filter(row => row.prediction.targetDate === day);
        return { forecast: average(values.map(row => row.prediction.upProbability!)), actual: average(values.map(row => Number(row.actualReturn! > 0))) };
      });
      return { lower, upper, count: selected.length, days: binDates.length,
        forecast: perDay.length ? average(perDay.map(value => value.forecast)) : undefined,
        actual: perDay.length ? average(perDay.map(value => value.actual)) : undefined };
    });
    return { version, loaded: rows.length, duplicates: rows.length - unique.size,
      pending: [...unique.values()].filter(row => row.status === 'PENDING').length,
      unavailable: [...unique.values()].filter(row => row.status === 'UNAVAILABLE').length,
      count: matured.length, days: dates.length,
      accuracy: daily.length ? average(daily.map(day => day.accuracy)) : undefined,
      brier: daily.length ? average(daily.map(day => day.brier)) : undefined, bins };
  });
}
