"""Acquire CNINFO historical industry events for a frozen research universe."""
import argparse
import asyncio
from dataclasses import asdict
from datetime import datetime, timezone, timedelta
import hashlib
import json
from pathlib import Path

import numpy as np

from finscope_market_data.providers.cninfo_industry import CninfoIndustryHistoryProvider, parse_industry_changes, URL
from finscope_market_data.providers.http import ProviderHttpClient


async def acquire(args):
    with np.load(args.inputs, allow_pickle=False) as frozen:
        codes = sorted(set(map(str, frozen['codes'])))
    output = Path(args.output)
    raw_dir = output.parent / (output.stem + '-raw')
    raw_dir.mkdir(parents=True, exist_ok=True)
    http = ProviderHttpClient(timeout_seconds=20, minimum_interval_seconds=.15)
    provider = CninfoIndustryHistoryProvider(http)
    records, failures, files = [], [], []
    try:
        for index, code in enumerate(codes):
            path = raw_dir / f'{code}.json'
            try:
                if path.exists():
                    saved = json.loads(path.read_text())
                    if saved['queryEnd'] != args.end_date:
                        raise ValueError('缓存查询截止不一致，请使用独立输出目录')
                    changes = parse_industry_changes(saved['response'], code, saved['retrievedAt'])
                else:
                    retrieved = datetime.now(timezone(timedelta(hours=8))).isoformat()
                    raw, changes = await provider.fetch(code, args.end_date, retrieved)
                    saved = dict(code=code, retrievedAt=retrieved, queryEnd=args.end_date, sourceUrl=URL, response=raw)
                    path.write_text(json.dumps(saved, ensure_ascii=False, allow_nan=False))
                records.extend(asdict(record) for record in changes)
                files.append(dict(code=code, path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                                  changeCount=len(changes)))
            except Exception as error:
                failures.append(dict(code=code, errorType=type(error).__name__))
            if (index+1) % 20 == 0 or index+1 == len(codes):
                print(json.dumps(dict(stage='industry', done=index+1, total=len(codes), failures=len(failures))), flush=True)
    finally:
        await http.aclose()
    summary = dict(source='CNINFO', sourceUrl=URL, taxonomy='008003', availabilityBasis='VENDOR_RECONSTRUCTION',
        availableAtProvided=False, queryEnd=args.end_date, requestedCodes=codes,
        coveredCodes=sorted({record['code'] for record in records}), records=records, failures=failures, rawFiles=files,
        universeFingerprint=hashlib.sha256(Path(args.inputs).read_bytes()).hexdigest())
    output.write_text(json.dumps(summary, ensure_ascii=False, allow_nan=False))
    print(json.dumps(dict(output=str(output), coveredCodes=len(summary['coveredCodes']), records=len(records), failures=len(failures))))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--end-date', required=True)
    asyncio.run(acquire(parser.parse_args()))
