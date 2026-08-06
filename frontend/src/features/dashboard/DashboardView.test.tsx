import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { DashboardView } from './DashboardView';
import type { Dashboard, DashboardHotspotRanking } from '../../shared/types';

const dashboard: Dashboard = {
  sourceCount: 3,
  articleCount: 15,
  briefCount: 1,
  latestFetchRuns: []
};

const hotspotRankings: DashboardHotspotRanking[] = [
    {
      categoryCode: 'FINANCE',
      label: '金融',
      items: [
        {
          id: 11,
          title: '央行宣布下调存款准备金率',
          summary: '本次调整预计释放长期流动性约一万亿元，市场资金面受到关注。',
          hotspotScore: 91,
          lifecycleState: 'RISING',
          sourceCount: 3,
          signalCount: 5,
          lastSeenAt: '2026-08-06T09:30:00'
        }
      ]
    },
    { categoryCode: 'TECHNOLOGY', label: '科技', items: [] },
    { categoryCode: 'POLITICS', label: '政治', items: [] }
];

test('renders three hotspot boards with readable summaries and ranking metadata', () => {
  renderDashboard();

  expect(screen.getByRole('heading', { name: '今日热点' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '金融' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '科技' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '政治' })).toBeInTheDocument();
  expect(screen.getByText('本次调整预计释放长期流动性约一万亿元，市场资金面受到关注。')).toBeInTheDocument();
  expect(screen.getByText(/热点 91/)).toBeInTheDocument();
  expect(screen.getByText(/正在升温/)).toBeInTheDocument();
  expect(screen.getByText(/3 个独立来源/)).toBeInTheDocument();
});

test('opens the selected radar event from a ranking item', async () => {
  const onOpenRadarEvent = vi.fn();
  renderDashboard(onOpenRadarEvent);

  await userEvent.click(screen.getByRole('button', { name: /央行宣布下调存款准备金率/ }));

  expect(onOpenRadarEvent).toHaveBeenCalledWith(11);
});

test('frames the research pulse as a ready command surface while retaining metric navigation', () => {
  renderDashboard();

  const focus = screen.getByText('当前焦点').parentElement;
  expect(focus).not.toBeNull();
  expect(within(focus as HTMLElement).getByRole('status')).toHaveTextContent('研究队列已就绪');
  expect(screen.getByRole('button', { name: /新信息.*打开文章工作区/ })).toHaveClass('dashboard-pulse-item');
  expect(screen.getByRole('button', { name: /运行中.*打开研究工作区/ })).toHaveClass('dashboard-pulse-item');
});

function renderDashboard(onOpenRadarEvent = vi.fn()) {
  render(
    <DashboardView
      dashboard={dashboard}
      hotspotRankings={hotspotRankings}
      articles={[]}
      events={[]}
      learningTasks={[]}
      contentIdeas={[]}
      researchRuns={[]}
      researchTheses={[]}
      agentRuns={[]}
      intakeCandidates={[]}
      knowledgeOverview={null}
      onChangeView={vi.fn()}
      onOpenRadarEvent={onOpenRadarEvent}
    />
  );
}
