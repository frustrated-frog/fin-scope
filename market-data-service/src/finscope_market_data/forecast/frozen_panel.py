"""Versioned research inputs: observable features separate from mature labels."""
from pathlib import Path

import numpy as np

from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.joint_dataset import JointDataset, JointRow, ObservableRow


PANEL_VERSION = 2


def save_frozen_panel(path, dataset):
    if not dataset.observable_rows:
        raise ValueError('旧冻结数据缺少完整可观测截面，必须从行情重建，不能由有标签股票伪造')
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(path, schemaVersion=np.array(PANEL_VERSION), asOfDate=np.array(dataset.as_of),
        features=np.array([r.sample.features for r in dataset.rows]),
        returns=np.array([r.sample.net_return for r in dataset.rows]), codes=np.array([r.code for r in dataset.rows]),
        signalDates=np.array([r.sample.signal_date for r in dataset.rows]),
        exitDates=np.array([r.sample.exit_date for r in dataset.rows]), featureCodes=np.array(dataset.feature_codes),
        marketReturns=np.array([r.market_return if r.market_return is not None else np.nan for r in dataset.rows]),
        observableCodes=np.array([r.code for r in dataset.observable_rows]),
        observableDates=np.array([r.signal_date for r in dataset.observable_rows]),
        observableFeatures=np.array([r.features for r in dataset.observable_rows]))


def load_frozen_panel(path, as_of):
    with np.load(path, allow_pickle=False) as data:
        version = int(data['schemaVersion']) if 'schemaVersion' in data else 1
        if version not in (1, PANEL_VERSION):
            raise ValueError('不支持的冻结输入版本')
        if version == PANEL_VERSION and str(data['asOfDate']) != as_of:
            raise ValueError('冻结输入截止日期与请求不一致')
        codes, dates, exits = data['codes'], data['signalDates'], data['exitDates']
        x, values, feature_codes = data['features'], data['returns'], tuple(map(str, data['featureCodes']))
        if (not len(codes) or not (len(codes) == len(dates) == len(exits) == len(x) == len(values))
                or x.shape != (len(codes), len(feature_codes)) or not np.all(np.isfinite(x))
                or not np.all(np.isfinite(values)) or len(set(zip(codes, dates))) != len(codes)):
            raise ValueError('冻结输入的标签行或特征矩阵无效')
        if np.any(exits > as_of) or np.any(dates >= exits):
            raise ValueError('冻结输入包含截止日期之后或未按顺序成熟的标签')
        market = data['marketReturns'] if 'marketReturns' in data else np.full(len(codes), np.nan)
        if market.shape != (len(codes),) or np.any(np.isinf(market)):
            raise ValueError('冻结市场收益未对齐')
        rows = tuple(JointRow(str(code), ForecastSample(str(day), str(day), str(exit_day),
            tuple(features), float(value)), None if np.isnan(market_value) else float(market_value))
            for code, day, exit_day, features, value, market_value in zip(codes, dates, exits, x, values, market))
        observable = ()
        if version == PANEL_VERSION:
            oc, od, ox = data['observableCodes'], data['observableDates'], data['observableFeatures']
            if (not len(oc) or len(oc) != len(od) or ox.shape != (len(oc), len(feature_codes))
                    or not np.all(np.isfinite(ox)) or np.any(od > as_of)
                    or len(set(zip(oc, od))) != len(oc)):
                raise ValueError('冻结可观测截面无效')
            observable = tuple(ObservableRow(str(code), str(day), tuple(features)) for code, day, features in zip(oc, od, ox))
            lookup = {(r.code, r.signal_date): r.features for r in observable}
            if any(lookup.get((r.code, r.sample.signal_date)) != r.sample.features for r in rows):
                raise ValueError('有标签股票的特征与可观测截面不一致')
        return JointDataset(as_of, rows, {}, {}, feature_codes, observable)
