import { Table } from '../../shared/components/Table';
import { AgentRun } from '../../shared/types';

export function AgentRunsView({ agentRuns }: { agentRuns: AgentRun[] }) {
  return (
    <section className="panel wide agent-runs-panel">
      <div className="panel-heading">
        <h3>Agent Trace</h3>
        <span className="subtle-badge">{agentRuns.length} runs</span>
      </div>
      <Table
        headers={['节点', '开始时间', '状态', '耗时', '错误']}
        rows={agentRuns.map((run) => [
          run.nodeName,
          formatStartTime(run.createdAt),
          run.status,
          `${run.durationMs}ms`,
          run.errorMessage || '-'
        ])}
        empty="暂无 Agent 运行记录"
      />
    </section>
  );
}

function formatStartTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}
