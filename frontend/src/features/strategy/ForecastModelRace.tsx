import { ForecastModelRace as ModelRace, SingleStockForecast } from './quantTypes';

type Competition = NonNullable<SingleStockForecast['modelCompetition']>;
type Candidate = Competition['candidates'][number];

const percent = (value?: number, digits = 1) => value == null ? '—' : `${(value * 100).toFixed(digits)}%`;
const decimal = (value?: number, digits = 3) => value == null ? '—' : value.toFixed(digits);

const roleCopy = {
  CHAMPION: '当前冠军',
  CHALLENGER: '挑战者',
  BASELINE: '规则基线'
};

const decisionCopy = { UP: '偏多', DOWN: '偏空', ABSTAIN: '弃权' };

const statusCopy = {
  EVIDENCE_ACCUMULATING: '真实证据积累中',
  EVIDENCE_INCOMPLETE: '证据链不完整',
  PROMOTION_REVIEW: '进入人工晋升审查',
  CHAMPION_LEADS: '当前冠军领先',
  NO_STABLE_EDGE: '暂无稳定领先'
};

function CandidateLane({ candidate, race }: { candidate: Candidate; race?: ModelRace }) {
  const live = race?.candidates.find(item => item.modelCode === candidate.code);
  const role = candidate.role ?? (candidate.selected ? 'CHAMPION' : 'CHALLENGER');
  const probability = candidate.calibratedProbability;
  const marker = Math.max(2, Math.min(98, (probability ?? .5) * 100));
  const delta = live?.brierDeltaVsChampion;

  return <article className="forecast-race-lane" data-role={role} data-review={live?.promotionEligible}>
    <header>
      <div><span>{roleCopy[role]}</span><h5>{candidate.name}</h5><code>{candidate.code}</code></div>
      <div className="forecast-race-probability"><strong>冻结概率 {percent(probability)}</strong><small>{decisionCopy[candidate.shadowDecision ?? 'ABSTAIN']} · {candidate.qualificationStatus ?? '历史版本'}</small></div>
    </header>
    <div className="forecast-race-track" aria-label={`${candidate.name}冻结概率`}>
      <i style={{ left: `${marker}%` }} /><span>40%</span><span>拒绝区间</span><span>60%</span>
    </div>
    <div className="forecast-race-evidence">
      <section><span>历史资格赛</span><strong>Brier {decimal(candidate.brierScore)}</strong><small>{candidate.validationFoldCount ?? 1} 折 · 波动 {decimal(candidate.brierStd)}</small></section>
      <section><span>锁定测试</span><strong>Brier {decimal(candidate.lockedMetrics?.brierScore)}</strong><small>Skill {percent(candidate.lockedMetrics?.brierSkillScore)}</small></section>
      <section data-live={Boolean(live)}><span>真实影子赛</span><strong>{live ? `Brier ${decimal(live.brierScore)}` : '等待到期'}</strong><small>{live ? `${live.sampleCount} 次 · 覆盖 ${percent(live.coverage)}` : '不回填旧版本结果'}</small></section>
      <section data-improved={delta != null && delta < 0}><span>相对冠军</span><strong>{delta == null ? '—' : `${delta > 0 ? '+' : ''}${decimal(delta)}`}</strong><small>{live?.promotionEligible ? '达到人工审查门槛' : '尚未达到晋升门槛'}</small></section>
    </div>
  </article>;
}

export function ForecastModelRace({ competition, race }: { competition: Competition; race?: ModelRace }) {
  const sampleCount = race?.sampleCount ?? 0;
  const minimum = race?.minimumPromotionSamples ?? 12;
  const progress = Math.min(100, sampleCount / Math.max(1, minimum) * 100);

  return <div className="forecast-model-race">
    <header className="forecast-race-heading">
      <div><span>MODEL CHALLENGE BOARD</span><h4><b>历史离线资格赛</b><i>→</i><b>真实到期影子赛</b></h4><p>{competition.selectionRule}</p></div>
      <div className="forecast-race-status" data-status={race?.status ?? 'EVIDENCE_ACCUMULATING'}><span>{race ? statusCopy[race.status] : '等待 v6 真实证据'}</span><strong>{sampleCount} / {minimum}</strong><small>独立数据指纹 · 最小审查样本</small></div>
    </header>
    <div className="forecast-race-progress"><i style={{ width: `${progress}%` }} /></div>
    <div className="forecast-race-legend"><span><i />当前冠军</span><span><i />挑战者</span><span><i />规则基线</span><p>所有模型冻结同一时刻的概率，等待同一未来结果；达到门槛只进入人工审查，不会自动换模。</p></div>
    <div className="forecast-race-lanes">{competition.candidates.map(candidate => <CandidateLane key={candidate.code} candidate={candidate} race={race} />)}</div>
    <footer><strong>{race?.conclusion ?? '新版本开始后，真实到期结果会逐次累积在这里。'}</strong><span>晋升硬门槛：至少 {minimum} 次成对结果 · Brier 优于冠军 0.010 · Log Loss 不变差 · 覆盖率与命中率同时过关</span></footer>
  </div>;
}
