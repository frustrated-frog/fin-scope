"""Fixed equal-weight slow/recent ensemble, evaluated against its actual incumbent."""
from dataclasses import dataclass
from typing import Sequence

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.local_prediction import LocalFit, fit_local
from finscope_market_data.forecast.short_term import (
    HALF_LIFE, SHORT_TERM_FEATURE_CODES, ShortTermModel,
)

MODEL_VERSION = 'next-session-ensemble-v4'
RECENT_WEIGHT = .5


@dataclass(frozen=True)
class NextSessionEnsemble:
    incumbent: LocalFit
    recent: ShortTermModel

    @classmethod
    def fit(cls, samples: Sequence[ForecastSample], short_samples: Sequence[ForecastSample], cutoff: str):
        if (len(samples) != len(short_samples) or any(
                (left.signal_date, left.exit_date, left.net_return) !=
                (right.signal_date, right.exit_date, right.net_return)
                for left, right in zip(samples, short_samples))):
            raise ValueError('长短期模型必须使用同日期、同标签样本')
        return cls(fit_local(samples, cutoff), ShortTermModel(short_samples, cutoff))

    def predict(self, features: Sequence[float], short_features: Sequence[float]):
        probability, expected, lower, upper = self.incumbent.predict(features)
        recent_probability = self.recent.predict(short_features)
        return ((1 - RECENT_WEIGHT) * probability + RECENT_WEIGHT * recent_probability,
                expected, lower, upper)

    @property
    def audit(self):
        return {**self.incumbent.audit, 'enhancement': dict(
            modelVersion=MODEL_VERSION, method='FIXED_EQUAL_BLEND', recentWeight=RECENT_WEIGHT,
            featureCodes=list(SHORT_TERM_FEATURE_CODES), halfLifeSessions=HALF_LIFE,
            recentTrainingThrough=self.recent.training_through,
            recentTrainingCount=self.recent.training_count,
            incumbentTrainingThrough=self.incumbent.training_through,
            returnModel='LOCAL_V3',
            rule='固定等权结合长期与近期模型；独立滚动验证同时比较原版，不依据测试结果调权',
        )}
