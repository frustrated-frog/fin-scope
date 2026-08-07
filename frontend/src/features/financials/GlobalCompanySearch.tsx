import { useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { CompanySearchResult } from './financialTypes';

const capabilityLabels: Record<CompanySearchResult['capabilityLevel'], string> = {
  L1: '公司已识别',
  L2: '原始披露可用',
  L3: '三张表可用',
  L4: '完整分析可用'
};

export function GlobalCompanySearch({ onSelect }: { onSelect: (company: CompanySearchResult) => void }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<CompanySearchResult[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const sequence = useRef(0);
  const selectedQuery = useRef('');
  const normalizedQuery = query.trim();

  useEffect(() => {
    if (normalizedQuery === selectedQuery.current) {
      setResults([]);
      setBusy(false);
      setError('');
      return;
    }
    if (normalizedQuery.length < 2) {
      setResults([]);
      setBusy(false);
      setError('');
      return;
    }
    const current = ++sequence.current;
    const timer = window.setTimeout(() => {
      setBusy(true);
      setError('');
      api<CompanySearchResult[]>(`/api/companies/search?q=${encodeURIComponent(normalizedQuery)}&limit=8`)
        .then((items) => {
          if (current === sequence.current) setResults(items);
        })
        .catch((reason) => {
          if (current === sequence.current) {
            setResults([]);
            setError(reason instanceof Error ? reason.message : '公司目录暂时不可用');
          }
        })
        .finally(() => {
          if (current === sequence.current) setBusy(false);
        });
    }, 300);
    return () => window.clearTimeout(timer);
  }, [normalizedQuery]);

  function select(company: CompanySearchResult) {
    selectedQuery.current = company.displayName;
    setQuery(company.displayName);
    setResults([]);
    onSelect(company);
  }

  return (
    <div className="global-company-search">
      <div className="global-company-search-field">
        <span aria-hidden="true">⌕</span>
        <input
          role="combobox"
          aria-label="搜索全球上市公司"
          aria-expanded={results.length > 0}
          aria-controls="global-company-results"
          autoComplete="off"
          placeholder="搜索公司名称、当地名称或股票代码"
          value={query}
          onChange={(event) => {
            selectedQuery.current = '';
            setQuery(event.target.value);
          }}
        />
        <small>{busy ? '正在查询全球公司目录…' : 'A 股 · 美国 · 韩国'}</small>
      </div>
      {normalizedQuery.length === 1 && <p className="global-company-search-hint">输入至少两个字符开始搜索</p>}
      {error && <p className="global-company-search-error" role="alert">{error}</p>}
      {normalizedQuery.length >= 2 && normalizedQuery !== selectedQuery.current && !busy && !error && results.length === 0 && (
        <p className="global-company-search-hint">未找到匹配公司，可尝试英文名或股票代码。</p>
      )}
      {results.length > 0 && (
        <div id="global-company-results" className="global-company-results" role="listbox">
          {results.map((company) => {
            const symbols = company.securities.map((item) => item.symbol).join(' / ');
            const exchange = company.securities[0]?.exchange;
            return (
              <button
                type="button"
                role="option"
                aria-selected="false"
                aria-label={`${company.displayName} ${symbols}`}
                key={`${company.providerCode}:${company.providerCompanyId}`}
                onClick={() => select(company)}
              >
                <span className="global-company-result-main">
                  <strong>{company.displayName}</strong>
                  {company.nativeName && company.nativeName !== company.displayName && <em>{company.nativeName}</em>}
                  <small>{symbols}{exchange ? ` · ${exchange}` : ''}{company.countryCode ? ` · ${company.countryCode}` : ''}</small>
                </span>
                <span className={`global-company-capability ${company.capabilityLevel.toLowerCase()}`}>
                  {capabilityLabels[company.capabilityLevel]}
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
