import { FinancialEvidence } from './financialTypes';

export function FinancialEvidenceRefs({ refs, evidenceById, onEvidence }: {
  refs: string[];
  evidenceById: Map<string, FinancialEvidence>;
  onEvidence: (id: string) => void;
}) {
  return <div className="financial-evidence-refs">
    {refs.map((id) => {
      const label = evidenceById.get(id)?.label || id;
      return <button key={id} type="button" aria-label={`${label}证据`} onClick={() => onEvidence(id)}>{label}</button>;
    })}
  </div>;
}
