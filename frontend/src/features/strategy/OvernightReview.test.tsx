import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { OvernightReview } from './OvernightReview';
import type { OvernightReport } from './overnightTypes';

const record: OvernightReport = { id: 'a', instrumentCode: '000001.SZ', signalDate: '2026-10-08', cutoff: '14:30',
  mode: 'TAIL_ENTRY', evidenceKind: 'FORWARD', status: 'WATCH', generatedAt: '', dataThrough: '', costBps: 20,
  inputFingerprint: '', modelVersion: 'local', targets: [], warnings: [],
  closeDirection: { protocol: '', target: 'CLOSE', status: 'AVAILABLE', reason: '', upProbability: .6 },
  outcome: { status: 'SETTLED', targets: [], closeDirection: { status: 'SETTLED', actualReturn: -.03, actualUp: false, correct: false } } };

test('filters mode, evidence and date without turning unavailable outcomes into successes', async () => {
  const onSelect = vi.fn();
  render(<OvernightReview records={[record, { ...record, id: 'b', instrumentCode: '600000.SH', mode: 'AFTER_CLOSE_HOLDING' },
    { ...record, id: 'c', instrumentCode: '600001.SH', evidenceKind: 'RETROSPECTIVE' },
    { ...record, id: 'd', instrumentCode: '600002.SH', signalDate: '2026-10-09', outcome: undefined }]}
    mode="TAIL_ENTRY" busy={false} onSettle={vi.fn()} onSelect={onSelect} />);
  expect(screen.queryByText('600000.SH')).not.toBeInTheDocument();
  expect(screen.queryByText('600001.SH')).not.toBeInTheDocument();
  expect(screen.getByText('方向未命中')).toBeVisible();
  expect(screen.getByText('待核验')).toBeVisible();
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText('信号日期'), '2026-10-08');
  expect(screen.queryByText('600002.SH')).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: /000001.SZ/ }));
  expect(onSelect).toHaveBeenCalledWith(record);
  await user.selectOptions(screen.getByLabelText('记录范围'), 'RETROSPECTIVE');
  expect(screen.getByText('600001.SH')).toBeVisible();
  expect(screen.queryByText('000001.SZ')).not.toBeInTheDocument();
});

test('preserves missing direction in legacy reports without substituting trading profit', () => {
  render(<OvernightReview records={[{ ...record, closeDirection: undefined,
    targets: [{ target: '10:00', status: 'WATCH', sampleCount: 80, upProbability: .3, probabilitySource: 'HISTORICAL_BASELINE' }],
    outcome: { status: 'SETTLED', targets: [{ target: '10:00', actualNetReturn: -.02 }] } }]}
    mode="TAIL_ENTRY" busy={false} onSettle={vi.fn()} onSelect={vi.fn()} />);
  expect(screen.getByText('未记录涨跌预测')).toBeVisible();
  expect(screen.getByText('预测缺失')).toBeVisible();
  expect(screen.queryByText('-2.0%')).not.toBeInTheDocument();
  expect(screen.queryByText('方向命中')).not.toBeInTheDocument();
});
