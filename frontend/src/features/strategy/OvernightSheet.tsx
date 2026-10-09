import { useEffect, useId, useRef, type ReactNode } from 'react';

/** Native modal provides focus containment, Escape dismissal and focus restoration. */
export function OvernightSheet({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const element = dialog.current;
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    element?.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      element?.close();
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  return <dialog ref={dialog} className="overnight-sheet" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); onClose(); }}>
    <header className="overnight-sheet-heading"><div><span>隔夜研究 / 详情</span><h3 id={titleId}>{title}</h3></div>
      <button type="button" onClick={onClose} aria-label="关闭详情" autoFocus>关闭 ×</button></header>
    <div className="overnight-sheet-body">{children}</div>
  </dialog>;
}
