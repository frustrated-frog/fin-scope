import { useCallback, useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import { ResearchRadarSnapshot } from '../news/researchRadarTypes';
import { KnowledgeNavigation } from './KnowledgeNavigation';
import { knowledgeApi } from './knowledgeApi';
import {
  KnowledgeEntryInput,
  KnowledgeEntry,
  KnowledgeEvidence,
  InvestmentRecognitionCandidate,
  InvestmentRecognitionRun,
  InvestmentRecognitionStatus,
  KnowledgeOverview,
  KnowledgeReviewInput,
  KnowledgeSection,
  KnowledgeTask,
  KnowledgeTopic,
  KnowledgeTopicWorkspace
} from './knowledgeTypes';
import { InvestmentRecognitionDesk } from './topics/InvestmentRecognitionDesk';
import { LearningWorkspace } from './learning/LearningWorkspace';
import { TopicWorkspace } from './topics/TopicWorkspace';
import { ReviewQueue } from './review/ReviewQueue';
import { VerificationQueue } from './facts/VerificationQueue';
import { DailyResearchDesk } from './daily/DailyResearchDesk';
import { isFormalInvestmentRecognition } from './topics/knowledgeClassification';

const validSections = new Set<KnowledgeSection>(['home', 'facts', 'topics', 'learning', 'review']);

function locationState() {
  const params = new URLSearchParams(window.location.search);
  const candidate = params.get('section') as KnowledgeSection | null;
  const topic = Number(params.get('topic'));
  const task = Number(params.get('task'));
  return {
    section: candidate && validSections.has(candidate) ? candidate : 'home' as KnowledgeSection,
    topicId: Number.isSafeInteger(topic) && topic > 0 ? topic : undefined,
    taskId: Number.isSafeInteger(task) && task > 0 ? task : undefined,
    taskStatus: params.get('status') || undefined
  };
}

export function KnowledgeView({
  addToast,
  setMessage
}: {
  addToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  setMessage: (message: string) => void;
}) {
  const initial = locationState();
  const [section, setSection] = useState<KnowledgeSection>(initial.section);
  const [topicId, setTopicId] = useState<number | undefined>(initial.topicId);
  const [selectedTaskId, setSelectedTaskId] = useState<number | undefined>(initial.taskId);
  const [taskStatus, setTaskStatus] = useState<string | undefined>(initial.taskStatus);
  const [overview, setOverview] = useState<KnowledgeOverview | null>(null);
  const [topics, setTopics] = useState<KnowledgeTopic[]>([]);
  const [loading, setLoading] = useState(false);
  const [tasks, setTasks] = useState<KnowledgeTask[]>([]);
  const [evidence, setEvidence] = useState<KnowledgeEvidence[]>([]);
  const [draft, setDraft] = useState<KnowledgeEntry | undefined>();
  const [topicWorkspace, setTopicWorkspace] = useState<KnowledgeTopicWorkspace | null>(null);
  const [dueTopics, setDueTopics] = useState<KnowledgeTopic[]>([]);
  const [verificationWorkspaces, setVerificationWorkspaces] = useState<KnowledgeTopicWorkspace[]>([]);
  const [radar, setRadar] = useState<ResearchRadarSnapshot | null>(null);
  const [radarError, setRadarError] = useState('');
  const [recognitionCandidates, setRecognitionCandidates] = useState<InvestmentRecognitionCandidate[]>([]);
  const [recognitionAgentRunning, setRecognitionAgentRunning] = useState(false);

  const restoreLocation = useCallback(() => {
    const next = locationState();
    setSection(next.section);
    setTopicId(next.topicId);
    setSelectedTaskId(next.taskId);
    setTaskStatus(next.taskStatus);
    setTopicWorkspace(null);
  }, []);

  const selectedTask = useMemo(
    () => tasks.find((task) => task.id === selectedTaskId) || tasks[0],
    [tasks, selectedTaskId]
  );

  const loadLearning = useCallback(async (status?: string | null) => {
    const taskPages = status === 'SUGGESTED'
      ? [await knowledgeApi.tasks({ status: 'SUGGESTED' })]
      : await Promise.all([
        knowledgeApi.tasks({ status: 'IN_PROGRESS' }),
        knowledgeApi.tasks({ status: 'TODO' })
      ]);
    const topicPage = await knowledgeApi.topics({ lifecycle: 'ACTIVE', size: 100 });
    const nextTasks = taskPages.flatMap((page) => page.items || []);
    setTasks(nextTasks);
    setTopics(topicPage.items);
    setSelectedTaskId((current) => nextTasks.some((task) => task.id === current)
      ? current
      : nextTasks[0]?.id);
  }, []);

  useEffect(() => {
    window.addEventListener('popstate', restoreLocation);
    return () => window.removeEventListener('popstate', restoreLocation);
  }, [restoreLocation]);

  useEffect(() => {
    setLoading(true);
    const load = section === 'home'
      ? Promise.all([
        knowledgeApi.overview().then(setOverview),
        api<ResearchRadarSnapshot>('/api/research-radar?category=ALL&watchlistOnly=false&limit=8')
          .then((snapshot) => {
            setRadar(snapshot);
            setRadarError('');
          })
          .catch(() => {
            setRadar(null);
            setRadarError('今日资讯暂不可用，已有认识和复查任务仍可继续。');
          })
      ])
      : section === 'facts'
        ? Promise.all([
          knowledgeApi.topics({ lifecycle: 'ACTIVE', size: 100 }),
          api<InvestmentRecognitionCandidate[]>('/api/knowledge/investment-recognitions')
        ]).then(async ([page, nextCandidates]) => {
          const acceptedTopicIds = new Set((Array.isArray(nextCandidates) ? nextCandidates : [])
            .filter((candidate) => candidate.status === 'ACCEPTED' && candidate.topicId)
            .map((candidate) => candidate.topicId as number));
          const recognitions = (page.items || []).filter((topic) =>
            isFormalInvestmentRecognition(topic, acceptedTopicIds));
          const workspaces = await Promise.all(recognitions.map((topic) => knowledgeApi.topicWorkspace(topic.id)));
          setVerificationWorkspaces(workspaces);
        })
      : section === 'topics'
        ? topicId
          ? knowledgeApi.topicWorkspace(topicId).then(setTopicWorkspace)
          : Promise.all([
            knowledgeApi.topics(),
            api<InvestmentRecognitionCandidate[]>('/api/knowledge/investment-recognitions')
          ]).then(([page, nextCandidates]) => {
            setTopicWorkspace(null);
            setTopics(page.items);
            setRecognitionCandidates(Array.isArray(nextCandidates) ? nextCandidates : []);
          })
        : section === 'learning'
          ? loadLearning(taskStatus)
          : section === 'review'
            ? topicId
              ? knowledgeApi.topicWorkspace(topicId).then(setTopicWorkspace)
              : knowledgeApi.dueReviews().then((page) => {
                setTopicWorkspace(null);
                setDueTopics(page.items);
              })
            : Promise.resolve();
    load.catch((error) => {
      const message = error instanceof Error ? error.message : '知识工作台加载失败';
      setMessage(message);
      addToast(message, 'error');
    }).finally(() => setLoading(false));
  }, [section, topicId, taskStatus, loadLearning]);

  useEffect(() => {
    if (section !== 'learning' || !selectedTask?.eventId) {
      setEvidence([]);
      setDraft(undefined);
      return;
    }
    Promise.all([knowledgeApi.taskEvidence(selectedTask.id), knowledgeApi.taskDraft(selectedTask.id)])
      .then(([nextEvidence, nextDraft]) => {
        setEvidence(nextEvidence);
        setDraft(nextDraft);
      })
      .catch(() => {
        setEvidence([]);
        setDraft(undefined);
      });
  }, [section, selectedTask?.id, selectedTask?.eventId]);

  function navigate(next: KnowledgeSection) {
    const params = new URLSearchParams(window.location.search);
    params.set('section', next);
    params.delete('topic');
    params.delete('task');
    params.delete('status');
    window.history.pushState({}, '', `${window.location.pathname}?${params.toString()}`);
    setTopicId(undefined);
    setSelectedTaskId(undefined);
    setTaskStatus(undefined);
    setTopicWorkspace(null);
    setSection(next);
  }

  function navigateTarget(target: string) {
    const targetParams = new URLSearchParams(target.startsWith('?') ? target.slice(1) : target);
    const next = targetParams.get('section') as KnowledgeSection | null;
    if (!next || !validSections.has(next)) return;
    window.history.pushState({}, '', `${window.location.pathname}?${targetParams.toString()}`);
    const task = Number(targetParams.get('task'));
    const nextTopic = Number(targetParams.get('topic'));
    setSelectedTaskId(Number.isSafeInteger(task) && task > 0 ? task : undefined);
    setTopicId(Number.isSafeInteger(nextTopic) && nextTopic > 0 ? nextTopic : undefined);
    setTaskStatus(targetParams.get('status') || undefined);
    setSection(next);
  }

  const searchTopics = useCallback(async (query: string) => {
    setLoading(true);
    try {
      const page = await knowledgeApi.topics({ query });
      setTopics(page.items);
    } finally {
      setLoading(false);
    }
  }, []);

  async function createTopic(input: { name: string; description: string }) {
    const created = await knowledgeApi.createTopic(input);
    addToast('投资认识档案已建立', 'success');
    navigateTarget(`?section=topics&topic=${created.id}`);
  }

  const acceptedRecognitionTopicIds = useMemo(() => new Set(recognitionCandidates
    .filter((candidate) => candidate.status === 'ACCEPTED' && candidate.topicId)
    .map((candidate) => candidate.topicId as number)), [recognitionCandidates]);

  async function runRecognitionAgent() {
    setRecognitionAgentRunning(true);
    try {
      const result = await api<InvestmentRecognitionRun>('/api/knowledge/investment-recognitions/run', { method: 'POST' });
      const [nextCandidates, page] = await Promise.all([
        api<InvestmentRecognitionCandidate[]>('/api/knowledge/investment-recognitions'),
        knowledgeApi.topics()
      ]);
      setRecognitionCandidates(Array.isArray(nextCandidates) ? nextCandidates : []);
      setTopics(page.items);
      addToast(`Agent 已检查 ${result.checkedObjects} 个投资对象，形成 ${result.candidateCount} 条候选`, 'success');
    } catch (error) {
      addToast(error instanceof Error ? error.message : '投资 Agent 运行失败', 'error');
    } finally {
      setRecognitionAgentRunning(false);
    }
  }

  async function acceptRecognitionCandidate(id: number, revision: number) {
    await api(`/api/knowledge/investment-recognitions/${id}/accept`, {
      method: 'POST',
      body: JSON.stringify({ expectedRevision: revision })
    });
    const [page, nextCandidates] = await Promise.all([
      knowledgeApi.topics(),
      api<InvestmentRecognitionCandidate[]>('/api/knowledge/investment-recognitions')
    ]);
    setTopics(page.items);
    setRecognitionCandidates(nextCandidates);
    addToast('Agent 候选已沉淀为正式投资认识', 'success');
  }

  async function updateRecognitionCandidate(id: number, status: InvestmentRecognitionStatus, revision: number) {
    const updated = await api<InvestmentRecognitionCandidate>(`/api/knowledge/investment-recognitions/${id}/status`, {
      method: 'POST',
      body: JSON.stringify({ status, expectedRevision: revision })
    });
    setRecognitionCandidates((current) => current.map((item) => item.id === id ? updated : item));
    addToast(status === 'NEEDS_EVIDENCE' ? '已转入待补证据' : '已更新候选状态', 'info');
  }

  function selectTask(id: number) {
    const params = new URLSearchParams(window.location.search);
    params.set('section', 'learning');
    params.set('task', String(id));
    window.history.replaceState({}, '', `${window.location.pathname}?${params.toString()}`);
    setSelectedTaskId(id);
  }

  async function acceptTask(taskId: number, acceptedTopicId: number, revision: number) {
    await knowledgeApi.acceptTask(taskId, acceptedTopicId, revision);
    setTaskStatus(undefined);
    navigateTarget(`?section=learning&task=${taskId}`);
    await loadLearning(null);
    addToast('建议已加入学习队列', 'success');
  }

  async function startTask(taskId: number, revision: number) {
    await knowledgeApi.startTask(taskId, revision);
    await loadLearning(null);
    addToast('任务已开始', 'success');
  }

  async function saveDraft(taskId: number, input: KnowledgeEntryInput) {
    const saved = await knowledgeApi.saveDraft(taskId, input);
    setDraft(saved);
    addToast('草稿已保存', 'success');
    return saved;
  }

  async function completeTask(taskId: number, input: KnowledgeEntryInput) {
    const completed = await knowledgeApi.completeTask(taskId, input);
    setDraft(undefined);
    await loadLearning(null);
    addToast('答案已沉淀到投资认识', 'success');
    return completed;
  }

  async function dismissTask(taskId: number, reason: string, revision: number) {
    await knowledgeApi.dismissTask(taskId, reason, revision);
    await loadLearning(taskStatus);
    addToast('任务已移出队列', 'info');
  }

  async function reviewTopic(input: KnowledgeReviewInput) {
    if (!topicId) return;
    await knowledgeApi.reviewTopic(topicId, input);
    const refreshed = await knowledgeApi.topicWorkspace(topicId);
    setTopicWorkspace(refreshed);
    addToast('复习完成，下一次日期已更新', 'success');
  }

  return (
    <section className="knowledge-workbench" data-testid="knowledge-view" data-topic-id={topicId}>
      <KnowledgeNavigation section={section} onChange={navigate} />

      {section === 'home' && (overview
        ? <DailyResearchDesk overview={overview} radar={radar} radarError={radarError} onNavigate={navigateTarget} />
        : <section className="knowledge-loading" aria-label="正在加载知识首页"><span /></section>)}
      {section === 'facts' && (loading
        ? <section className="knowledge-loading" aria-label="正在加载核验队列"><span /></section>
        : <VerificationQueue workspaces={verificationWorkspaces} onNavigate={navigateTarget} />)}
      {section === 'topics' && (topicWorkspace
        ? <TopicWorkspace workspace={topicWorkspace} onBack={() => navigateTarget('?section=topics')} onReview={reviewTopic} />
        : <InvestmentRecognitionDesk
          candidates={recognitionCandidates}
          topics={topics.filter((topic) => isFormalInvestmentRecognition(topic, acceptedRecognitionTopicIds))}
          loading={loading}
          running={recognitionAgentRunning}
          onRun={runRecognitionAgent}
          onAccept={acceptRecognitionCandidate}
          onStatus={updateRecognitionCandidate}
          onSearch={searchTopics}
          onCreate={createTopic}
          onOpenTopic={(id) => navigateTarget(`?section=topics&topic=${id}`)}
        />)}
      {section === 'learning' && <LearningWorkspace
        tasks={tasks}
        topics={topics}
        selectedTaskId={selectedTaskId}
        evidence={evidence}
        draft={draft}
        onSelectTask={selectTask}
        onAccept={acceptTask}
        onStart={startTask}
        onSaveDraft={saveDraft}
        onComplete={completeTask}
        onDismiss={dismissTask}
        onOpenEvent={() => addToast('请从市场资讯查看完整来源', 'info')}
      />}
      {section === 'review' && (topicWorkspace
        ? <TopicWorkspace workspace={topicWorkspace} reviewMode onBack={() => navigateTarget('?section=review')} onReview={reviewTopic} />
        : <ReviewQueue topics={dueTopics} onOpen={(id) => navigateTarget(`?section=review&topic=${id}`)} />)}
    </section>
  );
}
