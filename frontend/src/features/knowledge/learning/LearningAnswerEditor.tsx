import { useEffect, useState } from 'react';

import { KnowledgeEntry, KnowledgeEntryInput, KnowledgeEvidence, KnowledgeTask } from '../knowledgeTypes';

export function LearningAnswerEditor({
  task,
  draft,
  evidence,
  onSaveDraft,
  onComplete
}: {
  task: KnowledgeTask;
  draft?: KnowledgeEntry;
  evidence: KnowledgeEvidence[];
  onSaveDraft: (taskId: number, input: KnowledgeEntryInput) => Promise<KnowledgeEntry | void>;
  onComplete: (taskId: number, input: KnowledgeEntryInput) => Promise<KnowledgeEntry | void>;
}) {
  const [markdown, setMarkdown] = useState('');
  const [confidence, setConfidence] = useState<KnowledgeEntryInput['confidence']>('MEDIUM');
  const [evidenceIds, setEvidenceIds] = useState<number[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setMarkdown(draft?.contentMarkdown || '');
    setConfidence(draft?.confidence || 'MEDIUM');
    setEvidenceIds([]);
    setError('');
  }, [task.id, draft?.id, draft?.revision]);

  function input(): KnowledgeEntryInput {
    return {
      topicId: task.topicId as number,
      markdown: markdown.trim(),
      confidence,
      evidenceIds,
      expectedTaskRevision: task.revision,
      expectedEntryRevision: draft?.revision
    };
  }

  async function run(command: 'draft' | 'complete') {
    if (!markdown.trim() || !task.topicId) return;
    setSaving(true);
    setError('');
    try {
      if (command === 'draft') await onSaveDraft(task.id, input());
      else await onComplete(task.id, input());
    } catch (reason) {
      const conflict = (reason as { status?: number }).status === 409;
      setError(conflict
        ? '内容已在其他窗口更新。你的输入仍然保留，请比较最新版本后重新提交。'
        : reason instanceof Error ? reason.message : '保存失败，请稍后重试。');
    } finally {
      setSaving(false);
    }
  }

  function toggleEvidence(id: number) {
    setEvidenceIds((current) => current.includes(id)
      ? current.filter((item) => item !== id)
      : [...current, id]);
  }

  return (
    <section className="learning-answer-editor" role="region" aria-label="学习答案编辑器">
      <div className="learning-editor-heading">
        <div><p className="knowledge-kicker">Your reasoning</p><h3>形成自己的回答</h3></div>
        <label>置信度
          <select value={confidence} onChange={(event) => setConfidence(event.target.value as KnowledgeEntryInput['confidence'])}>
            <option value="LOW">低 · 仍需验证</option>
            <option value="MEDIUM">中 · 有证据支持</option>
            <option value="HIGH">高 · 多方验证</option>
          </select>
        </label>
      </div>
      <label className="learning-answer-field">
        <span>我的回答</span>
        <textarea
          rows={10}
          value={markdown}
          onChange={(event) => setMarkdown(event.target.value)}
          placeholder="先写结论，再说明证据、推理链和可能推翻它的条件。"
        />
      </label>
      <fieldset className="learning-evidence-picker">
        <legend>引用证据 <small>只勾选直接支持或反驳当前判断的事实</small></legend>
        {evidence.length > 0 ? evidence.map((item) => (
          <label key={item.id}>
            <input
              type="checkbox"
              checked={evidenceIds.includes(item.id)}
              onChange={() => toggleEvidence(item.id)}
            />
            <span><strong>{item.claim}</strong><small>{item.sourceTier} · 可信度 {item.confidence}</small></span>
          </label>
        )) : <p>当前事件还没有可引用证据。可以先保存草稿，再回到事件档案补充证据。</p>}
      </fieldset>
      {error && <p className="knowledge-inline-error" role="alert">{error}</p>}
      <div className="learning-editor-actions">
        <button type="button" disabled={!markdown.trim() || saving} onClick={() => run('draft')}>保存草稿</button>
        <button className="knowledge-primary-button" type="button" disabled={!markdown.trim() || saving} onClick={() => run('complete')}>
          完成并沉淀
        </button>
      </div>
    </section>
  );
}
