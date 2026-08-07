from __future__ import annotations

from datetime import date, timedelta

import pytest

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.logistic import RegularizedLogisticModel


def test_logistic_model_is_deterministic_and_orders_probabilities() -> None:
    first = date(2018, 1, 1)
    samples: list[ForecastSample] = []
    for index in range(240):
        signal = index % 12 - 5.5
        samples.append(
            ForecastSample(
                signal_date=(first + timedelta(days=index)).isoformat(),
                entry_date=(first + timedelta(days=index + 1)).isoformat(),
                exit_date=(first + timedelta(days=index + 20)).isoformat(),
                features=(signal, 0, 0, 0, 0, 0, 0),
                net_return=0.04 if signal > 0 else -0.03,
            )
        )

    first_model = RegularizedLogisticModel.fit(samples)
    second_model = RegularizedLogisticModel.fit(samples)
    negative = first_model.predict((-3, 0, 0, 0, 0, 0, 0))
    positive = first_model.predict((3, 0, 0, 0, 0, 0, 0))

    assert 0 <= negative < positive <= 1
    assert second_model.predict((3, 0, 0, 0, 0, 0, 0)) == pytest.approx(positive)
