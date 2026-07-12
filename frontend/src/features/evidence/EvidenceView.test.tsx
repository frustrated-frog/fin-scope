import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { EvidenceView } from './EvidenceView';

test('filters evidence records by source tier in the judgement workbench', async () => {
  const user = userEvent.setup();
  const evidenceItems = Array.from({ length: 21 }, (_, index) => ({
    id: index + 1, eventId: 1, sourceTier: 'A', evidenceType: 'DATA', claim: `证据-${index + 1}`, confidence: 90
  }));
  render(<EvidenceView evidenceItems={evidenceItems} events={[{ id: 1, canonicalTitle: '事件', themeCode: 'MARKET' }]} onOpenEvent={vi.fn()} />);

  await user.selectOptions(screen.getByRole('combobox', { name: '证据来源层级' }), 'A');
  expect(screen.getAllByText('证据-1').length).toBeGreaterThan(0);
  expect(screen.getAllByText('证据-21').length).toBeGreaterThan(0);
});

test('presents evidence as a judgement workbench instead of a flat ledger', () => {
  render(<EvidenceView evidenceItems={[{
    id: 1,
    eventId: 1,
    sourceTier: 'REGULATOR',
    evidenceType: 'FACT',
    claim: '监管部门公布规则已经生效。',
    confidence: 90,
    articleTitle: '正式文件',
    articleUrl: 'https://example.com/rule'
  }]} events={[{ id: 1, canonicalTitle: '出口管制', themeCode: 'ai_startup' }]} onOpenEvent={vi.fn()} />);

  expect(screen.getByText('当前可以形成的判断')).toBeInTheDocument();
  expect(screen.getByText('已证实')).toBeInTheDocument();
  expect(screen.getByText('证据缺口')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '查看原文' })).toHaveAttribute('href', 'https://example.com/rule');
});

test('groups multiple evidence items from one event into one judgement card', () => {
  render(<EvidenceView evidenceItems={[
    { id: 1, eventId: 1, sourceTier: 'REGULATOR', evidenceType: 'FACT', claim: '规则已经公开。', confidence: 100 },
    { id: 2, eventId: 1, sourceTier: 'REGULATOR', evidenceType: 'FACT', claim: '模型权重已经公开。', confidence: 95 }
  ]} events={[{ id: 1, canonicalTitle: 'DeepSeek 研究进展', themeCode: 'ai_startup' }]} onOpenEvent={vi.fn()} />);

  expect(screen.getAllByRole('button', { name: 'DeepSeek 研究进展' })).toHaveLength(1);
  expect(screen.getByText('规则已经公开。')).toBeInTheDocument();
  expect(screen.getByText('模型权重已经公开。')).toBeInTheDocument();
});
