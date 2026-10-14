"""Explicit, retrospective recovery; never scheduled or promoted to a timely forecast."""
from datetime import datetime, timedelta

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.models import OvernightRequest


def recover_today(automation, cutoff):
    now = automation.clock()
    if cutoff not in {'14:30', '14:45'}:
        raise ValueError('只允许补跑已结束的尾盘窗口')
    cutoff_at = datetime.fromisoformat(f'{now.date()}T{cutoff}')
    if now < cutoff_at + timedelta(minutes=5):
        raise ValueError('原决策窗口尚未结束，请等待正常任务')
    if next_session(now.date() - timedelta(days=1)) != now.date():
        raise ValueError('今天不是已核验的交易日')
    key = f'{now.date()}|RECOVERY|{cutoff}'
    job = automation.store.claim(key, now, {'signalDate': str(now.date()), 'cutoff': cutoff,
        'mode': 'TAIL_ENTRY', 'phase': 'PREDICT'})
    if not job:
        return automation.store.job(key)
    context = automation.store.get('context') or AutomationContext().model_dump(mode='json', by_alias=True)
    try:
        scan = automation.scanner.scan(now, context['candidateLimit'])
        snapshot_at = automation.clock().isoformat()
        results = []
        for candidate in scan['candidates']:
            request = OvernightRequest(instrument_code=candidate['instrumentCode'], signal_date=now.date(),
                mode='TAIL_ENTRY', cutoff=cutoff, cost_bps=context['tailCostBps'])
            try:
                report = automation.service.generate(request, freeze_all=True, settle_cached=False, cohort='CUSTOM')
                results.append({'instrumentCode': request.instrument_code, 'status': report['status'],
                    'reportId': report['id'], 'evidenceKind': report['evidenceKind'], 'warnings': report['warnings']})
            except Exception as error:
                results.append({'instrumentCode': request.instrument_code, 'status': 'FAILED',
                    'reason': f'事后补跑失败：{type(error).__name__}'})
        incomplete = any(row['status'] != 'WATCH' for row in results)
        automation.store.finish(job, automation.clock(), **scan, results=results, snapshotAt=snapshot_at,
            status=('PARTIAL' if incomplete else 'COMPLETED') if results else 'EMPTY',
            reason='人工事后补跑；候选在原窗口之后采集，不能作为当时的交易依据；原错过窗口记录保留')
    except Exception as error:
        automation.store.finish(job, automation.clock(), status='FAILED',
            reason=f'事后补跑扫描失败：{type(error).__name__}: {error}')
    return automation.store.job(key)


def main():
    import argparse
    import json
    from finscope_market_data.settings import Settings
    from finscope_market_data.providers.tencent_market import TencentMarketSnapshotService
    from finscope_market_data.providers.tdx_package import TdxDailyPackageService
    from finscope_market_data.overnight.automation import OvernightAutomation
    from finscope_market_data.overnight.joint_research import OvernightJointResearch
    from finscope_market_data.overnight.provider import OvernightMinuteProvider
    from finscope_market_data.overnight.scanner import OvernightCandidateScanner
    from finscope_market_data.overnight.service import OvernightService
    from finscope_market_data.overnight.store import OvernightStore

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cutoff', choices=['14:30', '14:45'], required=True)
    args = parser.parse_args()
    config = Settings()
    packages = TdxDailyPackageService(config.data_dir / 'tdx-daily-packages')
    fallback = TencentMarketSnapshotService(config.data_dir / 'official-stock-directory.json',
        universe_loader=packages.active_symbols)
    service = OvernightService(OvernightStore(config.data_dir / 'overnight-research.db'), OvernightMinuteProvider())
    automation = OvernightAutomation(service, OvernightCandidateScanner(fallback=fallback))
    service.joint = OvernightJointResearch(service.store, automation.store)
    job = recover_today(automation, args.cutoff)
    print(json.dumps({key: job.get(key) for key in ('key', 'status', 'reason', 'results')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
