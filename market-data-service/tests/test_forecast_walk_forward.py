from finscope_market_data.forecast.walk_forward import retraining_interval


def test_expensive_models_use_bounded_retraining_cadence() -> None:
    assert retraining_interval("LOGISTIC") == 20
    assert retraining_interval("HISTOGRAM_GB") == 60
    assert retraining_interval("STACKED") == 60
