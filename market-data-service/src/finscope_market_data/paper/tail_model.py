"""Frozen linear models for a 14:45 signal and the next session's executable morning exit."""

from collections import Counter
from datetime import datetime
import hashlib
import inspect
import json
import math

import numpy as np
from sklearn.linear_model import Ridge
from sklearn.preprocessing import StandardScaler

from finscope_market_data.forecast.trading_calendar import consecutive_observed_sessions
from finscope_market_data.overnight.joint_dataset import date_weights
from . import factor_proposal
from .return_targets import has_capacity
from .timeframes import FEATURES, Timeframes

NAME = "尾盘多周期 · Ridge 隔夜选股"
HYPOTHESIS = "14:45 已收盘的多周期价格信息能否改善隔夜选股？比较历史拟合的权重和相同因子的等权组合。"
DEFAULT_FEATURES = FEATURES[:4]


def samples(code, bars, joined=""):
    """Rows are keyed by the date when the label becomes observable, not just the entry date."""
    state = Timeframes(candles=True)
    days = {}
    lot = 200 if code.startswith(("688", "689")) else 100
    first_signal = datetime.fromisoformat(joined) if joined else None
    for b in bars:
        at = b.observed_at
        if not hasattr(b, "multiTimeframe"):
            state.update(at, b)
        row = days.setdefault(at.date(), {})
        clock = at.strftime("%H:%M")
        if getattr(b, "actions", []):
            row["ex"] = True
        if not getattr(b, "actionsVerified", False):
            row["unverified"] = True
        if clock == "14:45" and (first_signal is None or at >= first_signal):
            row["feature"] = factor_proposal.factor_inputs(getattr(b, "multiTimeframe", state.features),
                                                           getattr(b, "completedBars", state.candles))
        if clock in ("09:35", "14:55", "09:45"):
            row[clock] = b
    result = []
    for day, following in zip(days, list(days)[1:]):
        before, after = days[day], days[following]
        if (not consecutive_observed_sessions(day, following) or "feature" not in before
                or "unverified" in before or "unverified" in after or after.get("ex")
                or "09:35" not in after):
            continue
        entry, exit_bar = before.get("14:55"), after.get("09:45")
        if has_capacity(entry, lot) and has_capacity(exit_bar, lot):
            result.append(dict(code=code, signalDate=str(day), exitDate=str(following),
                               features=before["feature"], target=exit_bar.open / entry.open - 1))
    return result


def train(rows, through, features=DEFAULT_FEATURES, alpha=20., formulas=None):
    if formulas:
        features = list(formulas)
        rows = [{**r, "features": factor_proposal.factor_values(formulas, r["features"])}
                for r in rows if r["exitDate"] < through]
    if (not 1 <= len(features) <= 4 or len(set(features)) != len(features)
            or not set(features) <= set(formulas or FEATURES) or not math.isfinite(alpha) or not 0 < alpha <= 10000):
        raise ValueError("请选择一至四个可用因子及大于零、至多 10000 的 Ridge 正则强度")
    usable = sorted((r for r in rows if r["exitDate"] < through and
                     all(f in r["features"] for f in features)), key=lambda r: (r["signalDate"], r["code"]))
    dates = sorted({r["signalDate"] for r in usable})
    counts = Counter(r["code"] for r in usable)
    if len(dates) < 10 or len(counts) < 3 or len(usable) < 50:
        raise ValueError(f"训练覆盖不足：{len(dates)} 日、{len(counts)} 股、{len(usable)} 个样本；至少需要 10 日、3 股、50 个样本")
    weights = date_weights(usable)
    x = np.asarray([[r["features"][f] for f in features] for r in usable])
    if formulas and any(np.ptp(x, axis=0) < 1e-12):
        raise ValueError("组合中的因子在可用训练样本上没有变化")
    scaler = StandardScaler().fit(x, sample_weight=weights)
    model = Ridge(alpha=alpha).fit(scaler.transform(x), [r["target"] for r in usable], sample_weight=weights)
    return dict(formulas=formulas or {}, features=list(features), mean=scaler.mean_.tolist(), scale=scaler.scale_.tolist(),
                coefficients=model.coef_.tolist(), intercept=float(model.intercept_), alpha=alpha,
                samples=len(usable), dates=len(dates), symbols=len(counts), samplesByCode=dict(counts),
                firstDay=dates[0], lastLabel=max(r["exitDate"] for r in usable), sampleHash=hashlib.sha256(
                    json.dumps(usable, sort_keys=True, separators=(",", ":")).encode()).hexdigest())


def source(model, equal=False):
    config = {k: model[k] for k in ("features", "mean", "scale", "coefficients", "intercept")}
    if equal:
        config.update(coefficients=[1/len(config["features"])] * len(config["features"]), intercept=0.)
    config["equal"] = equal
    helpers = ""
    if model.get("formulas"):
        config["formulas"] = model["formulas"]
        helpers = "\n\n".join(inspect.getsource(fn) for fn in (
            factor_proposal.factor_inputs, factor_proposal.value_of, factor_proposal.factor_values))
    return "MODEL = " + repr(config) + "\n" + helpers + '''

def factors(bars):
    result = {name: {} for name in MODEL['features']}
    for code, rows in bars.items():
        values = rows[-1].get('multiTimeframe', {})
        if 'formulas' in MODEL:
            inputs = factor_inputs(values, rows[-1].get('completedBars', {}))
            values = factor_values(MODEL['formulas'], inputs)
        for name in result:
            if name in values:
                result[name][code] = values[name]
    return result


def decide(ctx):
    values = factors(ctx['bars'])
    score = {}
    for code in ctx['bars']:
        if all(code in v for v in values.values()):
            score[code] = MODEL['intercept'] + sum(
                w * (values[name][code] - mu) / scale
                for name, mu, scale, w in zip(MODEL['features'], MODEL['mean'],
                                              MODEL['scale'], MODEL['coefficients']))
    weights, reasons = {}, {}
    clock = ctx['at'][11:16]
    held = ctx['positionDetails']
    for code, position in held.items():
        if code in ctx['bars'] and position['sellable'] and clock != '14:45':
            weights[code] = 0.
            score.setdefault(code, 0.)
            reasons[code] = "隔夜持仓已到退出时点，可卖 %d 股，目标仓位降至零。" % position["sellable"]
    if clock == '14:45':
        available = max(0, 3 - len(held))
        for code in sorted(score, key=lambda c: (-score[c], c)):
            if not available:
                break
            lot = 200 if code.startswith(('688', '689')) else 100
            if code not in held and ctx['bars'][code][-1]['close'] * lot + ctx['costs']['minimumCommissionCny'] <= min(ctx['cash'], ctx['equity'] * 0.32):
                weights[code] = 0.32
                parts = [(name, w * (values[name][code] - mu) / scale)
                         for name, mu, scale, w in zip(MODEL['features'], MODEL['mean'], MODEL['scale'], MODEL['coefficients'])]
                leading, contribution = max(parts, key=lambda pair: abs(pair[1]))
                method = '等权因子得分' if MODEL['equal'] else '预测隔夜收益'
                factor_score = ('%.4f' if MODEL['equal'] else '%.3f%%') % (score[code] if MODEL['equal'] else score[code] * 100)
                reasons[code] = '尾盘按%s排序，%s；贡献幅度最大的因子为%s（%s），目标仓位 32%%。' % (
                    method, factor_score, leading, '正向' if contribution >= 0 else '负向')
                available -= 1
    return {'weights': weights, 'scores': score, 'reasons': reasons, 'state': {}}
'''
