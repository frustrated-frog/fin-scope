import { render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { RadarRankHistory } from './RadarRankHistory';
import { radarRankPath } from './radarRankPath';
vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

test('leaves a gap across missing production intervals', () => {
  const path = radarRankPath([
    { observedAt: '2026-09-21T10:00:00', rankPosition: 3 },
    { observedAt: '2026-09-21T10:30:00', rankPosition: 2 },
    { observedAt: '2026-09-21T12:00:00', rankPosition: 1 },
  ]);
  expect(path.match(/M/g)).toHaveLength(2);
  expect(path.match(/L/g)).toHaveLength(1);
});

test('labels the first observed rank without fabricating a rising baseline', async () => {
  vi.mocked(api).mockResolvedValue([
    { observedAt: '2026-09-21T10:00:00', rankPosition: 1, reportCount: 4, sourceCount: 2 },
  ]);
  render(<RadarRankHistory eventId="9223372036854775800" />);
  expect(await screen.findByText('首次记录，尚无可比较的历史基线。')).toBeInTheDocument();
  expect(screen.getByText('#1')).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/research-radar/events/9223372036854775800/rank-history');
});
