"""Frozen daily versus daily+industry direct-direction experiment."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from threadpoolctl import threadpool_limits

from evaluate_general_next_session import ReadOnlyHistory
from finscope_market_data.industry_models import IndustryChange
from finscope_market_data.forecast.frozen_panel import load_frozen_panel, save_frozen_panel
from finscope_market_data.forecast.historical_industry import industry_panel_features
from finscope_market_data.forecast.joint_dataset import build_joint_dataset
from finscope_market_data.forecast.joint_training import PARAMETERS
from finscope_market_data.forecast.observable_direction import rolling_panel_direction, PANEL_DIRECTION_VERSION
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.forecast.direction_error_attribution import attribute_direction_errors

OLD_INDUSTRY_COLUMNS = {'INDUSTRY_MOMENTUM_20', 'RELATIVE_MOMENTUM_20_INDUSTRY',
    'PEER_MOMENTUM_20', 'PEER_UP_BREADTH', 'PEER_RELATIVE_MOMENTUM_20', 'PEER_COVERAGE'}


def fingerprint(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def source_fingerprint():
    root = Path(__file__).parents[1] / 'src/finscope_market_data'
    paths = [*sorted((root/'forecast').glob('*.py')), root/'industry_models.py', Path(__file__)]
    return hashlib.sha256(b''.join(p.name.encode()+p.read_bytes() for p in paths)).hexdigest()


def emit(value):
    print(json.dumps(value, ensure_ascii=False), flush=True)


def prepare(args, output):
    data = load_frozen_panel(args.inputs, args.as_of)
    if data.observable_rows:
        return data, Path(args.inputs)
    codes = sorted({r.code for r in data.rows})
    store = ReadOnlyHistory(args.snapshots)
    try:
        histories = {code: store.daily_history_as_of(('SH:' if code.startswith('6') else 'SZ:')+code, args.as_of)
                     for code in codes}
        market = store.daily_history_as_of('SH:000300',args.as_of)
    finally:
        store.connection.close()
    if not market:
        raise ValueError('缺少市场交易日历')
    emit(dict(stage='rebuild_observable', stocks=len(codes)))
    rebuilt = build_joint_dataset(histories,as_of=args.as_of,market_bars=market)
    if {r.code for r in rebuilt.observable_rows} != set(codes):
        raise ValueError('原研发池存在无法重建的股票，不能静默改变股票池')
    path = output.with_suffix('.panel.npz')
    if path.exists():
        raise ValueError('目标冻结输入已存在，请指定新输出名或重放已有面板')
    save_frozen_panel(path,rebuilt)
    return rebuilt,path


def score(a,b,selected_days,mode):
    observable = np.isin(a['dates'],selected_days)
    mask = observable & (a['labels'] >= 0)
    p,base,y,dates = b['probabilities'][mode][mask],a['probabilities'][mode][mask],a['labels'][mask],a['dates'][mask]
    coverage = [dict(signalDate=day,observableCount=int(np.sum(a['dates']==day)),
        labelledCount=int(np.sum((a['dates']==day)&(a['labels']>=0)))) for day in selected_days]
    return dict(startDate=selected_days[0],endDate=selected_days[-1],
        D=evaluate_direction(base,y,dates,{'D':base}), DS=evaluate_direction(p,y,dates,{'D':base}),
        observableCount=int(observable.sum()),labelledCount=int(mask.sum()),
        labelCoverage=float(mask.sum()/observable.sum()),dailyLabelCoverage=coverage,
        attribution=attribute_direction_errors(p,base,y,dates))


def evaluate(args):
    output = Path(args.output)
    if output.exists() or output.with_suffix('.scores.npz').exists() or output.with_suffix('.protocol.json').exists():
        raise ValueError('输出或协议已存在，禁止覆盖实验')
    output.parent.mkdir(parents=True,exist_ok=True)
    source = source_fingerprint()
    data,panel_path = prepare(args,output)
    industry = json.loads(Path(args.industry).read_text())
    if industry['failures'] or set(industry['requestedCodes']) != {r.code for r in data.observable_rows}:
        raise ValueError('行业采集失败或请求股票池不一致')
    changes = tuple(IndustryChange(**r) for r in industry['records'])
    extra,extra_codes = industry_panel_features(data.observable_rows,data.feature_codes,changes)
    keep = [i for i,c in enumerate(data.feature_codes) if c not in OLD_INDUSTRY_COLUMNS]
    daily = np.array([r.features for r in data.observable_rows])[:,keep]
    arms = dict(D=daily, DS=np.column_stack((daily,extra)))
    labelled_days = sorted({r.sample.signal_date for r in data.rows})
    if len(labelled_days) < 360:
        raise ValueError('实验需要至少 360 个成熟日期')
    test_days,start = labelled_days[-180:],labelled_days[-240]
    protocol = dict(modelVersion=PANEL_DIRECTION_VERSION, parameters=PARAMETERS,
        arms=['D','DS'],primaryMode='INTERCEPT',diagnosticMode='RAW',step=5,trainDays=505,
        calibrationDays=60,threshold=.5,weighting='EQUAL_DATE_THEN_STOCK',
        warmupStart=start,testStart=test_days[0],testEnd=test_days[-1],windowDays=60,
        availabilityBasis='VENDOR_RECONSTRUCTION',assumedAvailabilityLagCalendarDays=1,
        industryTaxonomy='CNINFO_008003_HISTORICAL_NAMES_VERSION_UNVERIFIED',
        dailyFeatures=[data.feature_codes[i] for i in keep],industryFeatures=list(extra_codes),
        panelFingerprint=fingerprint(panel_path),industryFingerprint=fingerprint(args.industry),sourceFingerprint=source)
    protocol_path = output.with_suffix('.protocol.json')
    protocol_path.write_text(json.dumps(protocol,ensure_ascii=False,allow_nan=False))
    emit(dict(stage='protocol_frozen',path=str(protocol_path),testStart=test_days[0],testEnd=test_days[-1]))
    results = {}
    with threadpool_limits(limits=1):
        for name,x in arms.items():
            results[name] = rolling_panel_direction(data,x,start,
                progress=lambda value: emit(dict(arm=name,**value)))
    a,b = results['D'],results['DS']
    if a['keys'] != b['keys'] or not np.array_equal(a['labels'],b['labels']):
        raise ValueError('两臂预测截面或标签不一致')
    windows = [list(chunk) for chunk in np.array_split(test_days,3)]
    evaluation = {mode:dict(overall=score(a,b,test_days,mode),windows=[score(a,b,w,mode) for w in windows])
                  for mode in ('INTERCEPT','RAW')}
    target = np.array([r.signal_date >= start for r in data.observable_rows])
    scores = output.with_suffix('.scores.npz')
    np.savez_compressed(scores,dates=a['dates'],codes=np.array([k[0] for k in a['keys']]),labels=a['labels'],
        exits=a['exits'],batchIds=a['batchIds'],industryMissing=extra[target,-1],
        **{f'{arm}_{mode}':r['probabilities'][mode] for arm,r in results.items() for mode in ('RAW','INTERCEPT')})
    if (source != source_fingerprint() or fingerprint(panel_path) != protocol['panelFingerprint']
            or fingerprint(args.industry) != protocol['industryFingerprint']):
        raise ValueError('训练期间代码或冻结输入改变，结果不可发布')
    summary = dict(version='industry-information-e2-v1',evidenceKind='RETROSPECTIVE',productionEligible=False,
        protocol=protocol,universeCount=len({r.code for r in data.observable_rows}),
        industryUsableRowFraction=float(np.mean(extra[target,-1]==0)),evaluations=evaluation,
        batches={arm:r['batches'] for arm,r in results.items()},
        files=dict(panel=str(panel_path),industry=args.industry,scores=str(scores),protocol=str(protocol_path)),
        limitations=['旧240股票研发池，存在缓存选择和存活偏差；尚无独立股票池验证',
            '供应商重建没有公告时间；变更日后一天可用仅为研究假设',
            '行业同行仅来自固定研发池，非全市场行业参考池',
            '全部可观测股票先预测；缺失标签仅在评分时排除，缺失原因未知',
            '历史窗口已参与研发；窗口区间未纠正全部重复研究偏差',
            '固定单一种子和模型族，正结果仍需独立验证和影子预测'])
    output.write_text(json.dumps(summary,ensure_ascii=False,allow_nan=False))
    emit(dict(stage='complete',output=str(output),coverage=summary['industryUsableRowFraction'],
        metrics={mode:dict(D=ev['overall']['D']['accuracy'],DS=ev['overall']['DS']['accuracy'],
            comparison=ev['overall']['DS']['comparisons']['D'],
            windows=[dict(D=w['D']['accuracy'],DS=w['DS']['accuracy']) for w in ev['windows']]) for mode,ev in evaluation.items()}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs',required=True)
    parser.add_argument('--industry',required=True)
    parser.add_argument('--output',required=True)
    parser.add_argument('--as-of',required=True)
    parser.add_argument('--snapshots',default='data/market-data-snapshots.db')
    evaluate(parser.parse_args())
