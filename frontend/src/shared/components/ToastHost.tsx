import { ToastItem } from '../types';

const toastMeta = {
  success: { icon: '✓', title: '完成', label: '成功通知' },
  error: { icon: '!', title: '注意', label: '错误通知' },
  info: { icon: 'i', title: '提示', label: '信息通知' }
} satisfies Record<ToastItem['type'], { icon: string; title: string; label: string }>;

export function ToastHost({ toasts }: { toasts: ToastItem[] }) {
  return (
    <div className="toast-container" role="region" aria-label="全局通知" aria-live="polite">
      {toasts.map((toast) => {
        const meta = toastMeta[toast.type];
        return (
          <div key={toast.id} className={`toast toast-${toast.type}`} role="status" aria-label={meta.label}>
            <span className="toast-mark" aria-hidden="true">{meta.icon}</span>
            <span className="toast-copy">
              <strong>{meta.title}</strong>
              <span>{toast.message}</span>
            </span>
          </div>
        );
      })}
    </div>
  );
}
