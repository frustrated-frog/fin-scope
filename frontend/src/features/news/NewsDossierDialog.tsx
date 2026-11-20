import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import './RadarEventDetailDrawer.css';

type DialogProps = {
  title: string;
  recordId: string | number;
  meta: ReactNode;
  closeLabel: string;
  onClose: () => void;
  windowed?: boolean;
  railLabel: string;
  rail: ReactNode;
  children: ReactNode;
};

export function NewsDossierDialog({ title, recordId, meta, closeLabel, onClose, windowed, railLabel, rail, children }: DialogProps) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const element = dialog.current;
    const trigger = document.activeElement;
    if (typeof element?.showModal === 'function') {
      element.showModal();
    } else {
      element?.setAttribute('open', '');
    }
    return () => {
      element?.close?.();
      if (trigger instanceof HTMLElement && trigger.isConnected) {
        trigger.focus({ preventScroll: true });
      }
    };
  }, []);

  const content = <dialog ref={dialog} className={windowed ? 'radar-detail-drawer dossier-window' : 'radar-detail-drawer'}
    aria-labelledby={titleId} onCancel={(event) => { event.preventDefault(); onClose(); }}
    onClick={(event) => {
      if (windowed && event.target === event.currentTarget) {
        onClose();
      }
    }}>
    <div className="radar-detail-scroll">
      <div className="radar-dossier">
        <header className="radar-dossier-header">
          <div className="radar-dossier-id"><span>EVENT DOSSIER</span><small>#{recordId}</small></div>
          <button type="button" className="radar-drawer-close" aria-label={closeLabel} onClick={onClose}>×</button>
          <h2 id={titleId}>{title}</h2>
          <div className="radar-dossier-meta">{meta}</div>
        </header>
        <div className="radar-dossier-layout">
          <div className="radar-dossier-main">{children}</div>
          <aside className="radar-dossier-rail" aria-label={railLabel}>{rail}</aside>
        </div>
      </div>
    </div>
  </dialog>;
  // 弹窗脱离资讯列表的按钮样式；全屏雷达保留自己的页面上下文。
  return windowed ? createPortal(content, document.body) : content;
}

export function DossierTabs<T extends string>({ label, tabs, current, onSelect, counts }: {
  label: string;
  tabs: ReadonlyArray<readonly [T, string]>;
  current: T;
  onSelect: (tab: T) => void;
  counts?: Partial<Record<T, number>>;
}) {
  return <nav className="radar-dossier-tabs" aria-label={label}>
    {tabs.map(([code, name]) => <button type="button" key={code} aria-pressed={current === code} onClick={() => onSelect(code)}>
      {name}{counts?.[code] ? <small aria-hidden="true">{counts[code]}</small> : null}
    </button>)}
  </nav>;
}

export function DossierPrimaryAction({ label, disabled, onClick }: { label: string; disabled?: boolean; onClick: () => void }) {
  return <button type="button" className="radar-dossier-primary" disabled={disabled} onClick={onClick}>
    <span aria-hidden="true">✦</span>{label}
  </button>;
}
