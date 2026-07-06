import { useEffect, useMemo, useState } from 'react';

import { AppShell } from './app/AppShell';
import { AgentRunsView } from './features/agents/AgentRunsView';
import { ArticleView } from './features/articles/ArticleView';
import { BriefReaderView } from './features/briefs/BriefReaderView';
import { BriefsView } from './features/briefs/BriefsView';
import { ContentStudioView } from './features/content-studio/ContentStudioView';
import { DashboardView } from './features/dashboard/DashboardView';
import { EvidenceView } from './features/evidence/EvidenceView';
import { EventsView } from './features/events/EventsView';
import { LearningView } from './features/learning/LearningView';
import { ResearchView } from './features/research/ResearchView';
import { SettingsView } from './features/settings/SettingsView';
import { TopicReaderView } from './features/topics/TopicReaderView';
import { SourcesView } from './features/sources/SourcesView';
import { TopicsView } from './features/topics/TopicsView';
import { api } from './shared/api/client';
import {
  AgentRun,
  Article,
  Brief,
  BriefResearchContext,
  ContentIdea,
  Dashboard,
  EvidenceItem,
  EventCluster,
  LearningTask,
  ResearchRun,
  ResearchRunDetail,
  Source,
  ToastItem,
  Topic,
  TopicDetail,
  View
} from './shared/types';

export default function App() {
  const [view, setView] = useState<View>('dashboard');
  const [theme, setTheme] = useState<'light' | 'dark'>('dark');
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [sources, setSources] = useState<Source[]>([]);
  const [articles, setArticles] = useState<Article[]>([]);
  const [briefs, setBriefs] = useState<Brief[]>([]);
  const [selectedBrief, setSelectedBrief] = useState<Brief | null>(null);
  const [selectedBriefContext, setSelectedBriefContext] = useState<BriefResearchContext | null>(null);
  const [events, setEvents] = useState<EventCluster[]>([]);
  const [evidenceItems, setEvidenceItems] = useState<EvidenceItem[]>([]);
  const [focusedEventId, setFocusedEventId] = useState<number | null>(null);
  const [topics, setTopics] = useState<Topic[]>([]);
  const [learningTasks, setLearningTasks] = useState<LearningTask[]>([]);
  const [contentIdeas, setContentIdeas] = useState<ContentIdea[]>([]);
  const [researchRuns, setResearchRuns] = useState<ResearchRun[]>([]);
  const [researchRunDetail, setResearchRunDetail] = useState<ResearchRunDetail | null>(null);
  const [researchBusy, setResearchBusy] = useState(false);
  const [topicDetail, setTopicDetail] = useState<TopicDetail | null>(null);
  const [topicDeleteTarget, setTopicDeleteTarget] = useState<Topic | null>(null);
  const [deletingTopicId, setDeletingTopicId] = useState<number | null>(null);
  const [agentRuns, setAgentRuns] = useState<AgentRun[]>([]);
  const [message, setMessage] = useState('准备就绪');
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const addToast = (toastMessage: string, type: 'success' | 'error' | 'info' = 'info') => {
    const id = Date.now();
    setToasts((current) => [...current, { id, message: toastMessage, type }]);
    setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id));
    }, 3000);
  };

  const refresh = async () => {
    const [
      dashboardData,
      sourceData,
      articleData,
      briefData,
      eventData,
      evidenceData,
      topicData,
      learningTaskData,
      contentIdeaData,
      researchRunData,
      agentData
    ] = await Promise.all([
      api<Dashboard>('/api/dashboard'),
      api<Source[]>('/api/sources'),
      api<Article[]>('/api/articles'),
      api<Brief[]>('/api/briefs'),
      api<EventCluster[]>('/api/events'),
      api<EvidenceItem[]>('/api/evidence'),
      api<Topic[]>('/api/topics'),
      api<LearningTask[]>('/api/learning-tasks'),
      api<ContentIdea[]>('/api/content-ideas'),
      api<ResearchRun[]>('/api/research/runs'),
      api<AgentRun[]>('/api/agent-runs')
    ]);
    setDashboard(dashboardData);
    setSources(sourceData);
    setArticles(articleData);
    setBriefs(briefData);
    setEvents(eventData);
    setEvidenceItems(evidenceData);
    setTopics(topicData);
    setLearningTasks(learningTaskData);
    setContentIdeas(contentIdeaData);
    setResearchRuns(researchRunData);
    setAgentRuns(agentData);
  };

  useEffect(() => {
    refresh().catch((error) => setMessage(error instanceof Error ? error.message : '初始化失败'));
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.body.dataset.theme = theme;

    return () => {
      delete document.documentElement.dataset.theme;
      delete document.body.dataset.theme;
    };
  }, [theme]);

  const currentTitle = useMemo(() => {
    if (view === 'briefReader') {
      return 'Brief Reader';
    }
    switch (view) {
      case 'dashboard':
        return 'Dashboard';
      case 'sources':
        return 'Sources';
      case 'article':
        return 'Article';
      case 'briefs':
        return 'Briefs';
      case 'research':
        return 'Research';
      case 'events':
        return 'Events';
      case 'evidence':
        return 'Evidence Ledger';
      case 'topics':
        return 'Topics';
      case 'topicReader':
        return 'Topic Reader';
      case 'learning':
        return 'Learning';
      case 'contentStudio':
        return 'Studio';
      case 'agents':
        return 'Agent Runs';
      case 'settings':
        return 'Settings';
      default:
        return 'Dashboard';
    }
  }, [view]);

  async function loadTopicDetail(topicId: number) {
    const detail = await api<TopicDetail>(`/api/topics/${topicId}`);
    setTopicDetail(detail);
    return detail;
  }

  async function openTopicReader(topicId: number) {
    await loadTopicDetail(topicId);
    setView('topicReader');
  }

  async function openTopicForLearning(topicId: number) {
    await loadTopicDetail(topicId);
    setView('learning');
  }

  async function deleteTopic(topic: Topic) {
    setMessage('正在删除主题');
    setDeletingTopicId(topic.id);
    try {
      await api<void>(`/api/topics/${topic.id}`, { method: 'DELETE' });
      if (topicDetail?.topic.id === topic.id) {
        setTopicDetail(null);
        setView('topics');
      }
      setTopicDeleteTarget(null);
      await refresh();
      setMessage('主题已删除');
      addToast('主题已删除', 'success');
    } catch (error) {
      const message = error instanceof Error ? error.message : '主题删除失败';
      setMessage(message);
      addToast(message, 'error');
    } finally {
      setDeletingTopicId(null);
    }
  }

  function openEvent(eventId: number) {
    setFocusedEventId(eventId);
    setView('events');
  }

  async function openBrief(date: string) {
    setMessage('正在打开简报');
    try {
      const [detail, context] = await Promise.all([
        api<Brief>(`/api/briefs/${date}`),
        api<BriefResearchContext>(`/api/briefs/${date}/research-context`)
      ]);
      setSelectedBrief(detail);
      setSelectedBriefContext(context);
      setView('briefReader');
      setMessage('简报已打开');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '简报打开失败');
    }
  }

  async function openResearchRun(id: number) {
    const detail = await api<ResearchRunDetail>(`/api/research/runs/${id}`);
    setResearchRunDetail(detail);
  }

  async function runResearch(input: {
    runDate: string;
    themeCodes: string[];
    maxSourcesPerTheme: number;
    includeDisabled: boolean;
  }) {
    setResearchBusy(true);
    setMessage('正在运行研究');
    try {
      const run = await api<ResearchRun>('/api/research/runs', {
        method: 'POST',
        body: JSON.stringify(input)
      });
      await refresh();
      await openResearchRun(run.id);
      setMessage(`研究运行完成：${run.status}`);
      addToast(`研究运行完成：${run.status}`, run.status === 'FAILED' ? 'error' : 'success');
    } catch (error) {
      const message = error instanceof Error ? error.message : '研究运行失败';
      setMessage(message);
      addToast(message, 'error');
    } finally {
      setResearchBusy(false);
    }
  }

  async function refreshWorkspace() {
    setMessage('正在刷新');
    try {
      await refresh();
      setMessage('数据已同步');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '刷新失败');
    }
  }

  async function compoundBriefToTopics(date: string) {
    await api(`/api/topics/from-brief/${date}`, { method: 'POST' });
    setMessage('简报已沉淀到主题库');
    await refresh();
    setView('topics');
  }

  async function updateLearningTaskStatus(taskId: number, status: string) {
    await api(`/api/learning-tasks/${taskId}/status`, {
      method: 'POST',
      body: JSON.stringify({ status })
    });
    setMessage(`学习任务已更新为 ${status}`);
    await refresh();
  }

  async function updateContentIdeaStatus(ideaId: number, status: string) {
    await api(`/api/content-ideas/${ideaId}/status`, {
      method: 'POST',
      body: JSON.stringify({ status })
    });
    setMessage(`选题状态已更新为 ${status}`);
    await refresh();
  }

  return (
    <AppShell
      view={view}
      currentTitle={currentTitle}
      theme={theme}
      articlesCount={articles.length}
      topicsCount={topics.length}
      message={message}
      toasts={toasts}
      onChangeView={setView}
      onToggleTheme={() => setTheme((current) => current === 'dark' ? 'light' : 'dark')}
      onRefresh={refreshWorkspace}
    >
      {view === 'dashboard' && <DashboardView dashboard={dashboard} articles={articles} />}
      {view === 'sources' && (
        <SourcesView
          sources={sources}
          onChanged={refresh}
          addToast={addToast}
        />
      )}
      {view === 'article' && (
        <ArticleView
          setView={setView}
          onWorkspaceChanged={refresh}
          addToast={addToast}
        />
      )}
      {view === 'briefs' && (
        <BriefsView
          briefs={briefs}
          onChanged={refresh}
          setMessage={setMessage}
          onOpenBrief={openBrief}
          onAfterCompound={() => setView('topics')}
        />
      )}
      {view === 'briefReader' && (
        <BriefReaderView
          brief={selectedBrief}
          researchContext={selectedBriefContext}
          onBack={() => setView('briefs')}
          onCompound={compoundBriefToTopics}
        />
      )}
      {view === 'research' && (
        <ResearchView
          runs={researchRuns}
          detail={researchRunDetail}
          busy={researchBusy}
          onRun={runResearch}
          onOpenRun={openResearchRun}
          onOpenBrief={openBrief}
        />
      )}
      {view === 'events' && <EventsView events={events} initialEventId={focusedEventId} />}
      {view === 'evidence' && <EvidenceView evidenceItems={evidenceItems} events={events} />}
      {view === 'topics' && (
        <TopicsView
          topics={topics}
          onChanged={refresh}
          onOpenTopicReader={openTopicReader}
          onDeleteTopic={(topic) => {
            setTopicDeleteTarget(topic);
          }}
        />
      )}
      {view === 'topicReader' && (
        <TopicReaderView
          topicDetail={topicDetail}
          onBack={() => setView('topics')}
          onRecordLearning={openTopicForLearning}
        />
      )}
      {view === 'learning' && (
        <LearningView
          topics={topics}
          learningTasks={learningTasks}
          topicDetail={topicDetail}
          onOpenTopic={openTopicForLearning}
          onOpenEvent={openEvent}
          onChanged={refresh}
          onTaskStatusChange={updateLearningTaskStatus}
          setMessage={setMessage}
          addToast={addToast}
        />
      )}
      {view === 'contentStudio' && (
        <ContentStudioView
          contentIdeas={contentIdeas}
          onIdeaStatusChange={updateContentIdeaStatus}
          addToast={addToast}
        />
      )}
      {view === 'agents' && <AgentRunsView agentRuns={agentRuns} />}
      {view === 'settings' && <SettingsView setMessage={setMessage} />}
      {topicDeleteTarget && (
        <div className="modal-overlay">
          <div className="modal topic-delete-modal" role="dialog" aria-modal="true" aria-labelledby="topic-delete-title">
            <div className="modal-header topic-delete-header">
              <span className="topic-delete-mark" aria-hidden="true">!</span>
              <div>
                <p className="modal-kicker">Confirm action</p>
                <h4 id="topic-delete-title">删除主题</h4>
              </div>
            </div>
            <div className="modal-content topic-delete-content">
              <p className="topic-delete-name">{topicDeleteTarget.name}</p>
              <p>关联文章、简报和原始内容不会被删除。</p>
            </div>
            <div className="modal-actions">
              <button className="secondary-button" type="button" onClick={() => setTopicDeleteTarget(null)}>
                取消
              </button>
              <button
                className="danger-button topic-delete-confirm-button"
                type="button"
                disabled={deletingTopicId === topicDeleteTarget.id}
                onClick={() => deleteTopic(topicDeleteTarget)}
              >
                {deletingTopicId === topicDeleteTarget.id ? '删除中' : '确认删除'}
              </button>
            </div>
          </div>
        </div>
      )}
    </AppShell>
  );
}
