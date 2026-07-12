import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { createIngestTaskChannel } from '../articles/ingestTaskChannel';
import { IntakeView } from './IntakeView';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
vi.mock('../articles/ingestTaskChannel', () => ({ createIngestTaskChannel: vi.fn() }));

const candidate = {
  id: 7,
  batchId: 3,
  originalTitle: 'Candidate article',
  chineseTitle: '候选文章',
  originalUrl: 'https://example.com/article',
  agentScore: 7,
  agentRecommendation: 'SUCCESS',
  agentStatus: '市场测试',
  sourceName: '收录',
  humanStatus: 'PENDING',
  decisionSummary: '值得入库'
};

beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(createIngestTaskChannel).mockReset();
});

test('shows in-card progress immediately after promoting a candidate', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockResolvedValue({
    taskId: 'promotion-7', status: 'QUEUED', phase: 'QUEUED', message: '等待入库'
  } as never);
  vi.mocked(createIngestTaskChannel).mockReturnValue({
    completion: new Promise(() => undefined),
    dispose: vi.fn()
  });

  render(
    <IntakeView
      batches={[]}
      candidates={[candidate]}
      status="PENDING"
      onStatusChange={vi.fn().mockResolvedValue([])}
      onChanged={vi.fn().mockResolvedValue(undefined)}
      addToast={vi.fn()}
    />
  );

  await user.click(screen.getByRole('button', { name: '入文章库-7' }));

  expect(await screen.findByText('正在入文章库 · 等待入库')).toBeInTheDocument();
  expect(screen.getByLabelText('入文章库-7')).toHaveTextContent('正在入库…');
  expect(screen.getByLabelText('入文章库-7')).toBeDisabled();
});
