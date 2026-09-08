"""Small fixed candidate family, selected only on pre-test temporal periods."""
import numpy as np
from lightgbm import LGBMClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from finscope_market_data.forecast.calibration import PlattCalibrator


def date_weights(rows, half_life=None):
    days, inverse, counts = np.unique([r.sample.signal_date for r in rows], return_inverse=True, return_counts=True)
    weights = 1. / counts[inverse]
    if half_life is not None:
        weights *= 2 ** (-(len(days) - 1 - inverse) / half_life)
    return weights / weights.mean()


class ColumnModel:
    def __init__(self, model, columns):
        self.model, self.columns = model, columns

    def predict_proba(self, x):
        values = np.asarray(x)[:, self.columns]
        if isinstance(self.model, LGBMClassifier):
            p = self.model.booster_.predict(values)
            return np.column_stack((1-p, p))
        return self.model.predict_proba(values)


class Ensemble:
    def __init__(self, models):
        self.models = models

    def predict_proba(self, x):
        return np.mean([model.predict_proba(x) for model in self.models], axis=0)


def fit_candidates(rows, feature_codes, parameters):
    x = np.asarray([r.sample.features for r in rows])
    y = np.asarray([r.sample.positive for r in rows])
    if len(np.unique(y)) < 2:
        raise ValueError('候选训练需要上涨和非上涨样本')
    full = list(range(x.shape[1]))
    core = [i for i, name in enumerate(feature_codes) if not name.startswith('STATE_')]
    days = sorted({r.sample.signal_date for r in rows})
    recent = np.asarray([r.sample.signal_date >= days[max(0, len(days)-180)] for r in rows])
    models = {}
    for code, columns, mask, half_life in (
        ('BASE_TREE', core, np.ones(len(rows), dtype=bool), None),
        ('FULL_TREE', full, np.ones(len(rows), dtype=bool), None),
        ('RECENT_TREE', full, recent, None),
        ('DECAY_TREE', full, np.ones(len(rows), dtype=bool), 126),
    ):
        subset = tuple(row for row, keep in zip(rows, mask) if keep)
        models[code] = ColumnModel(LGBMClassifier(**parameters).fit(x[mask][:, columns], y[mask],
            sample_weight=date_weights(subset, half_life)), columns)
    weights = date_weights(rows)
    logistic = make_pipeline(StandardScaler(), LogisticRegression(C=.1, max_iter=500)).fit(x, y,
        standardscaler__sample_weight=weights, logisticregression__sample_weight=weights)
    models['POOLED_LOGISTIC'] = ColumnModel(logistic, full)
    models['EQUAL_ENSEMBLE'] = Ensemble((models['FULL_TREE'], models['POOLED_LOGISTIC']))
    return models


def _mean(values, rows):
    weights = date_weights(rows)
    return float(np.average(values, weights=weights))


def select_adaptive_candidate(training, selection, feature_codes, parameters):
    dates = sorted({r.sample.signal_date for r in training})
    if len(dates) < 120:
        raise ValueError('时序候选比较需要至少 120 个训练日期')
    periods, scores = [], {}
    # Earlier rolling period plus three later periods. Final calibration/test never enter here.
    for earlier in (True, False):
        calibration_start = dates[-60] if earlier else dates[-30]
        validation_start = dates[-30] if earlier else selection[0].sample.signal_date
        fit = tuple(r for r in training if r.sample.exit_date < calibration_start)
        cal = tuple(r for r in training if calibration_start <= r.sample.signal_date and r.sample.exit_date < validation_start)
        validation = tuple(r for r in training if r.sample.signal_date >= validation_start) if earlier else selection
        if len({r.sample.signal_date for r in fit}) < 60 or not cal:
            raise ValueError('候选比较清除跨段标签后样本不足')
        models = fit_candidates(fit, feature_codes, parameters)
        cx = np.asarray([r.sample.features for r in cal]); cy = [r.sample.positive for r in cal]
        vx = np.asarray([r.sample.features for r in validation]); vy = np.asarray([r.sample.positive for r in validation])
        probabilities = {}
        for code, model in models.items():
            calibrator = PlattCalibrator.fit(model.predict_proba(cx)[:, 1], cy)
            probabilities[code] = np.asarray([calibrator.calibrate(float(p)) for p in model.predict_proba(vx)[:, 1]])
        validation_dates = sorted({r.sample.signal_date for r in validation})
        chunks = [validation_dates] if earlier else np.array_split(validation_dates, 3)
        prior = _mean(np.asarray([r.sample.positive for r in fit], dtype=float), fit)
        for chunk in chunks:
            mask = np.asarray([r.sample.signal_date in set(chunk) for r in validation])
            rows = tuple(r for r, keep in zip(validation, mask) if keep)
            if not rows:
                continue
            period = dict(trainingThrough=max(r.sample.exit_date for r in fit), calibrationStart=calibration_start,
                calibrationThrough=max(r.sample.exit_date for r in cal), startDate=rows[0].sample.signal_date,
                endDate=rows[-1].sample.exit_date, dayCount=len(chunk))
            periods.append(period)
            for code, p in probabilities.items():
                scores.setdefault(code, []).append(dict(brier=_mean((p[mask]-vy[mask])**2, rows),
                    accuracy=_mean(((p[mask]>=.5)==vy[mask]).astype(float), rows),
                    baselineBrier=_mean((prior-vy[mask])**2, rows)))
    candidates = {code: dict(periods=values, meanBrier=float(np.mean([v['brier'] for v in values])),
        worstBrier=max(v['brier'] for v in values), meanAccuracy=float(np.mean([v['accuracy'] for v in values])),
        improvedPeriodCount=sum(v['brier'] < v['baselineBrier'] for v in values)) for code, values in scores.items()}
    # Keep a simple incumbent when all challengers lack cross-period probability evidence.
    eligible = [code for code, values in candidates.items() if values['improvedPeriodCount'] >= 3 or code == 'BASE_TREE']
    chosen = min(eligible, key=lambda code: (candidates[code]['meanBrier'], candidates[code]['worstBrier'], -candidates[code]['meanAccuracy'], code))
    return chosen, dict(selected=chosen, candidates=candidates, periods=periods, periodCount=len(periods),
        selectionEnd=selection[-1].sample.exit_date, evidenceKind='PRE_TEST_SELECTION',
        rule='至少三个时期优于训练先验；按平均 Brier、最差 Brier、方向命中依次比较；基础树保留为回退')
