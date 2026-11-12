"""Current Shanghai-calendar week: missing windows are separate from model scores."""
from datetime import datetime, timedelta

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.overnight.error_audit import build_error_audit

PROTOCOL = 'overnight-weekly-diagnostics-v1'


def build_weekly_diagnostics(store, meta, contexts, now):
    start = now.date() - timedelta(days=now.weekday())
    result = build_error_audit(store, meta, contexts, now, since=str(start), use_incumbent=False)
    windows = []
    day = start
    while day <= now.date():
        if next_session(day - timedelta(days=1)) == day:
            for cutoff in ('14:30', '14:45'):
                deadline = datetime.fromisoformat(f'{day}T{cutoff}:00') + timedelta(minutes=5)
                if now < deadline:
                    continue
                job = meta.job(f'{day}|TAIL|{cutoff}') or {}
                windows.append({'signalDate': str(day), 'cutoff': cutoff, 'status': job.get('status', 'NO_RECORD'),
                    'reason': job.get('reason'), 'reasonCode': job.get('reasonCode'),
                    'diagnostic': job.get('windowDiagnostic')})
        day += timedelta(days=1)
    originals = {}
    for report in sorted(store.iter_history(), key=lambda r: (r['generatedAt'], r['id'])):
        if (str(start) <= report['signalDate'] <= str(now.date()) and report['generatedAt'] < now.isoformat()
                and report.get('evidenceKind') == 'FORWARD'
                and (report.get('jointResearch') or {}).get('cohort') == 'AUTOMATIC'):
            originals.setdefault((report['signalDate'], report['cutoff'], report['instrumentCode']), report)
    pending = 0
    archives = {}
    for report in originals.values():
        archive = archives.setdefault(report['mode'], {'mode': report['mode'], 'recordCount': 0, 'pendingCount': 0})
        archive['recordCount'] += 1
        target = next_session(datetime.fromisoformat(report['signalDate']).date())
        if target and f'{target}T15:00:00' >= now.isoformat():
            pending += 1
            archive['pendingCount'] += 1
    result.update(protocol=PROTOCOL, periodStart=str(start), periodEnd=str(now.date()),
                  recordCount=len(originals), pendingCount=pending, archives=list(archives.values()), windows=windows)
    return result
