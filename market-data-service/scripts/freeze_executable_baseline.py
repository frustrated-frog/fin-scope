"""Freeze baseline A from verified archived metadata and existing research histories."""
from __future__ import annotations

import argparse
import json
from urllib.error import URLError
from urllib.request import Request, urlopen

from replay_executable_quant import render
from pathlib import Path
from pydantic import ValidationError

from finscope_market_data.forecast.executable_archive import BaselineArchive
from finscope_market_data.forecast.executable_baseline import freeze_baseline
from finscope_market_data.forecast.executable_sources import audit_snapshots, load_research_histories


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, help='Offline archive with execution and universe evidence')
    parser.add_argument('--snapshots', type=Path, help='Read-only research snapshot database')
    parser.add_argument('--audit-only', action='store_true')
    parser.add_argument('--replay-api', help='Optionally submit validated input to the Java replay endpoint')
    parser.add_argument('--output', type=Path, required=True, help='New directory; existing results are never overwritten')
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Output directory already exists')
    if (args.audit_only and not args.snapshots) or (not args.audit_only and not args.manifest):
        parser.error('Supply --snapshots for audit or --manifest for freezing')
    files = {}
    if args.audit_only:
        audit = audit_snapshots(args.snapshots)
    else:
        try:
            source = json.loads(args.manifest.read_text())
            if args.snapshots:
                if source.get('researchHistories'):
                    raise ValueError('Choose inline histories or snapshots, not both')
                source['researchHistories'] = load_research_histories(
                    args.snapshots, {row['instrumentCode'] for row in source['universe']}, source['endDate'])
            archive = BaselineArchive.model_validate(source)
            bundle, audit = freeze_baseline(archive)
            files = {'archive.json': archive.model_dump(mode='json'), 'input.json': bundle}
        except (ValueError, KeyError) as error:
            # Keep an actionable failure without echoing arbitrary source payloads.
            audit = dict(status='BLOCKED', errorType=type(error).__name__,
                         reason='Archive validation failed; see schema and coverage requirements')
            if isinstance(error, ValidationError):
                audit['issues'] = error.errors(include_input=False, include_context=False, include_url=False)[:50]
            elif type(error) is ValueError:
                audit['reason'] = str(error)
    markdown = None
    if audit['status'] == 'READY' and args.replay_api:
        try:
            request = Request(args.replay_api, data=json.dumps(files['input.json'], allow_nan=False).encode(),
                              headers={'Content-Type': 'application/json'}, method='POST')
            with urlopen(request, timeout=60) as response:
                envelope = json.load(response)
            if envelope.get('success') is not True:
                raise ValueError('Java replay did not succeed')
            markdown = render(envelope['data'])
            files['account.json'] = envelope['data']
            audit['replayStatus'] = 'SUCCEEDED'
        except (URLError, ValueError, KeyError) as error:
            audit['status'] = 'BLOCKED'
            audit['replayStatus'] = 'FAILED'
            audit['reason'] = 'Frozen input is preserved; Java replay failed: ' + type(error).__name__
    args.output.mkdir(parents=True)
    for name, value in {**files, 'audit.json': audit}.items():
        with (args.output / name).open('x') as output:
            json.dump(value, output, ensure_ascii=False, indent=2, allow_nan=False)
            output.write('\n')
    if markdown is not None:
        (args.output / 'account.md').write_text(markdown)
    print(json.dumps(dict(status=audit['status'], output=str(args.output))))
    if audit['status'] != 'READY':
        raise SystemExit(2)


if __name__ == '__main__':
    main()
