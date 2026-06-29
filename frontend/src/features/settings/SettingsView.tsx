import { api } from '../../shared/api/client';

export function SettingsView({ setMessage }: { setMessage: (message: string) => void }) {
  async function exportData() {
    const result = await api<{ path: string }>('/api/exports', { method: 'POST' });
    setMessage(`导出完成：${result.path}`);
  }

  return (
    <section className="panel wide">
      <h3>导出与恢复</h3>
      <p className="muted">本地优先存储，导出包包含 SQLite、Markdown Vault 与 manifest。</p>
      <button className="primary-button" onClick={exportData}>生成备份包</button>
    </section>
  );
}
