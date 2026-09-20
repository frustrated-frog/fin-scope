import { expect, test } from 'vitest';
import { summarizeNextSession } from './nextSessionValidation';
import type { NextSessionPredictionRecord } from './quantTypes';

function record(id: number, day: string, probability: number, actual: number, version = 'v2'): NextSessionPredictionRecord {
  return { id, instrumentCode: `${id}.SZ`, status: 'MATURED', actualReturn: actual,
    prediction: { modelVersion: version, targetDate: day, generatedAt: '2026-09-01T16:00:00', upProbability: probability } } as NextSessionPredictionRecord;
}

test('weights independent dates equally instead of counting a busy day as more evidence', () => {
  const result = summarizeNextSession([record(1, '2026-09-02', .8, .01), record(2, '2026-09-02', .8, .02), record(3, '2026-09-03', .8, -.01)])[0];
  expect(result.count).toBe(3);
  expect(result.days).toBe(2);
  expect(result.accuracy).toBe(.5);
  expect(result.brier).toBeCloseTo(.34);
  expect(result.bins[4].actual).toBe(.5);
});

test('separates versions and rejects later duplicates and pending outcomes', () => {
  const first = record(1, '2026-09-02', .6, -.01);
  const duplicate = { ...record(2, '2026-09-02', .9, .02), instrumentCode: first.instrumentCode };
  const pending = { ...record(3, '2026-09-03', .8, .02), status: 'PENDING' as const };
  const groups = summarizeNextSession([duplicate, pending, first, record(4, '2026-09-02', .9, .02, 'v1')]);
  expect(groups[0]).toMatchObject({ version: 'v2', count: 1, pending: 1, duplicates: 1, accuracy: 0 });
  expect(groups[1]).toMatchObject({ version: 'v1', count: 1, accuracy: 1 });
});

test('handles empty and unavailable outcomes without fabricating statistics', () => {
  expect(summarizeNextSession([])).toEqual([]);
  const values = summarizeNextSession([{ ...record(1, '2026-09-02', .7, 0), status: 'UNAVAILABLE' }, record(2, '2026-09-03', .7, NaN)])[0];
  expect(values.count).toBe(0);
  expect(values.accuracy).toBeUndefined();
  expect(values.bins.every(bin => bin.actual === undefined)).toBe(true);
});
