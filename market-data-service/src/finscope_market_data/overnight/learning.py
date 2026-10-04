"""Past-only fitting, independent calibration and rolling audits for each exit target."""
from dataclasses import dataclass
import math

import numpy as np
from sklearn.linear_model import LogisticRegression, Ridge
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from finscope_market_data.forecast.calibration import CalibrationResult, PlattCalibrator

TRAIN_WINDOW = 240
MIN_TRAIN = 40
CALIBRATION_WINDOW = 20
MIN_VALIDATION = 20
VALIDATION_WINDOW = 60
# Two boundary labels may still be immature at a 14:30/15:00 decision cutoff.
MIN_SAMPLES = MIN_TRAIN + CALIBRATION_WINDOW + MIN_VALIDATION + 2


@dataclass
class OvernightFit:
    classifier: object
    regression: object
    calibration: CalibrationResult
    training_prior: float
    baseline: float
    baseline_return: float
    radius: float
    audit: dict

    def predict(self, features):
        raw = float(self.classifier.predict_proba([features])[0, 1]) if self.classifier is not None else self.training_prior
        probability = self.calibration.calibrate(raw)
        expected = float(self.regression.predict([features])[0])
        return probability, raw, expected, expected - self.radius, expected + self.radius


def fit_at(samples, cutoff):
    matured = sorted((s for s in samples if s['exitAt'] < cutoff), key=lambda s: s['signalDate'])
    calibration = matured[-CALIBRATION_WINDOW:]
    if len(calibration) < CALIBRATION_WINDOW:
        return None
    calibration_start = f"{calibration[0]['signalDate']}T{cutoff[11:]}"
    training = [s for s in matured[:-CALIBRATION_WINDOW] if s['exitAt'] < calibration_start][-TRAIN_WINDOW:]
    if len(training) < MIN_TRAIN:
        return None
    x = np.asarray([s['features'] for s in training])
    returns = np.asarray([s['actualNetReturn'] for s in training])
    labels = returns > 0
    prior = float(labels.mean())
    classifier = None
    if len(set(labels)) == 2:
        classifier = make_pipeline(StandardScaler(), LogisticRegression(C=.1, max_iter=300, random_state=42))
        classifier.fit(x, labels)
    regression = make_pipeline(StandardScaler(), Ridge(alpha=20))
    regression.fit(x, returns)
    calibration_x = [s['features'] for s in calibration]
    raw = classifier.predict_proba(calibration_x)[:, 1].tolist() if classifier is not None else [prior] * len(calibration)
    calibrator = PlattCalibrator.fit(raw, [s['actualNetReturn'] > 0 for s in calibration])
    residuals = sorted(abs(s['actualNetReturn'] - estimate)
                       for s, estimate in zip(calibration, regression.predict(calibration_x)))
    radius = float(residuals[min(len(residuals) - 1, math.ceil((len(residuals) + 1) * .8) - 1)])
    baseline_samples = matured[-TRAIN_WINDOW - CALIBRATION_WINDOW:]
    return OvernightFit(classifier, regression, calibrator, prior,
        float(np.mean([s['actualNetReturn'] > 0 for s in baseline_samples])),
        float(np.mean([s['actualNetReturn'] for s in baseline_samples])), radius,
        {'trainingCount': len(training), 'calibrationCount': len(calibration),
         'trainingThrough': training[-1]['exitAt'], 'calibrationStart': calibration_start,
         'calibrationThrough': calibration[-1]['exitAt'], 'calibrationStatus': calibrator.status,
         'calibrationReason': calibrator.reason, 'baselineCount': len(baseline_samples)})


def evaluate(checks, calibration_status):
    """Fixed diagnostic rules; never selects a model or an exit time from the test results."""
    if not checks:
        return {'status': 'INSUFFICIENT_VALIDATION', 'count': 0}

    def metrics(rows):
        brier = float(np.mean([(s['probability'] - (s['actual'] > 0)) ** 2 for s in rows]))
        baseline = float(np.mean([(s['prior'] - (s['actual'] > 0)) ** 2 for s in rows]))
        return {'brier': brier, 'baselineBrier': baseline,
                'skill': 1 - brier / baseline if baseline > 1e-12 else None}

    overall = metrics(checks)
    recent = metrics(checks[-10:])
    coverage = float(np.mean([s['lower'] <= s['actual'] <= s['upper'] for s in checks]))
    if len(checks) < MIN_VALIDATION:
        status = 'INSUFFICIENT_VALIDATION'
    elif overall['skill'] is None or overall['skill'] <= 0:
        status = 'BASELINE_NOT_BEATEN'
    elif recent['skill'] is None or recent['skill'] <= 0:
        status = 'RECENT_DEGRADATION'
    elif calibration_status != 'FITTED':
        status = 'CALIBRATION_UNAVAILABLE'
    elif coverage < .6:
        status = 'INTERVAL_UNRELIABLE'
    else:
        status = 'HISTORICAL_EDGE'
    return {'status': status, 'count': len(checks), 'from': checks[0]['signalDate'],
        'through': checks[-1]['signalDate'], 'brierSkill': overall['skill'],
        'recentCount': min(10, len(checks)), 'recentBrierSkill': recent['skill'],
        'rawBrier': float(np.mean([(s['rawProbability'] - (s['actual'] > 0)) ** 2 for s in checks])),
        'baselineAccuracy': float(np.mean([(s['prior'] >= .5) == (s['actual'] > 0) for s in checks])),
        'intervalCoverage': coverage, 'nominalCoverage': .8,
        'expectedReturnMae': float(np.mean([abs(s['actual'] - s['expected']) for s in checks])),
        **overall}


def forecast_target(samples, features, cutoff):
    samples = sorted((sample for sample in samples if sample['exitAt'] < cutoff), key=lambda sample: sample['signalDate'])
    if len(samples) < MIN_SAMPLES:
        return {'status': 'INSUFFICIENT_DATA', 'sampleCount': len(samples), 'minimumSamples': MIN_SAMPLES,
                'missingSamples': MIN_SAMPLES - len(samples)}
    checks = []
    for index in range(max(0, len(samples) - VALIDATION_WINDOW), len(samples)):
        sample = samples[index]
        test_cutoff = f"{sample['signalDate']}T{cutoff[11:]}"
        fitted = fit_at(samples[:index], test_cutoff)
        if fitted is None:
            continue
        probability, raw, expected, lower, upper = fitted.predict(sample['features'])
        checks.append({'signalDate': sample['signalDate'], 'probability': probability, 'rawProbability': raw,
            'expected': expected, 'lower': lower, 'upper': upper, 'actual': sample['actualNetReturn'],
            'prior': fitted.baseline, 'trainingThrough': fitted.audit['trainingThrough'],
            'calibrationThrough': fitted.audit['calibrationThrough']})
    fitted = fit_at(samples, cutoff)
    if fitted is None or len(checks) < MIN_VALIDATION:
        return {'status': 'INSUFFICIENT_DATA', 'sampleCount': len(samples), 'minimumSamples': MIN_SAMPLES,
                'validationCount': len(checks), 'missingValidationSamples': max(0, MIN_VALIDATION - len(checks))}
    probability, raw, expected, lower, upper = fitted.predict(features)
    audit = evaluate(checks, fitted.calibration.status)
    reference = select_reference(probability, expected, lower, upper, fitted, audit)
    return {'status': 'WATCH', 'sampleCount': len(samples), 'minimumSamples': MIN_SAMPLES,
        **reference, 'rawUpProbability': raw,
        'baselineProbability': fitted.baseline, 'baselineExpectedNetReturn': fitted.baseline_return,
        'validationCount': len(checks),
        'brierScore': audit['brier'], 'baselineBrier': audit['baselineBrier'],
        'directionAccuracy': float(np.mean([(s['probability'] >= .5) == (s['actual'] > 0) for s in checks])),
        **fitted.audit, 'reliability': audit, 'validation': checks}


def select_reference(probability, expected, lower, upper, fitted, audit):
    """Only already-matured rolling checks can promote a model over its prior.

    Historical means are labelled as such. A rejected regression's interval
    must never be attached to a different (baseline) expected return.
    """
    use_model = audit['status'] == 'HISTORICAL_EDGE'
    return {'probabilitySource': 'CALIBRATED_MODEL' if use_model else 'HISTORICAL_BASELINE',
        'selectionReason': audit['status'], 'modelUpProbability': probability,
        'modelExpectedNetReturn': expected, 'modelLowerNetReturn': lower, 'modelUpperNetReturn': upper,
        'upProbability': probability if use_model else fitted.baseline,
        'expectedNetReturn': expected if use_model else fitted.baseline_return,
        'lowerNetReturn': lower if use_model else None, 'upperNetReturn': upper if use_model else None}
