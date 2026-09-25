"""Immutable daily discovery evidence and read-only cache outcome snapshots."""
from __future__ import annotations

import hashlib
import json
from contextlib import closing
from pathlib import Path
import sqlite3

from finscope_market_data.discovery.recall import evaluate_recall
from finscope_market_data.discovery.evidence_io import freeze_json, freeze_text
from finscope_market_data.discovery.trading_scope import TradingScopePolicy


def load_outcome_inputs(path: Path, signal_date: str, as_of: str):
    histories, calendar = {}, []
    with closing(sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)) as connection, connection:
        connection.execute('BEGIN')
        for key, payload in connection.execute("SELECT symbol_key,payload_json FROM market_data_snapshot WHERE capability='DAILY_BARS'"):
            rows = json.loads(payload).get('data') or []
            if key == 'SH:000300':
                calendar = [x['trade_date'] for x in rows if x['trade_date'] <= as_of]
            code = key.split(':')[-1]
            decision = TradingScopePolicy().classify(code)
            if decision.allowed and key.split(':')[0] == decision.market:
                histories[code] = [x for x in rows if signal_date <= x['trade_date'] <= as_of]
    return {'histories': histories, 'calendar': calendar}


class DiscoveryRecallArchive:
    def __init__(self, directory: Path, snapshots: Path):
        self.directory = directory
        self.snapshots = snapshots

    def update(self, report: dict):
        self.directory.mkdir(parents=True, exist_ok=True)
        # Freeze once per date. Later retries cannot rewrite the originally observed list.
        path = self.directory / f"{report['as_of_date']}.json"
        frozen = {key: value for key, value in report.items() if key != 'recall_evaluations'}
        freeze_json(path, frozen)
        previous = sorted(p for p in self.directory.glob('????-??-??.json')
                          if p.stem < report['as_of_date'])[-5:]
        if not previous:
            return []
        inputs = load_outcome_inputs(self.snapshots, previous[0].stem, report['as_of_date'])
        encoded = json.dumps(inputs, sort_keys=True, ensure_ascii=False)
        input_hash = hashlib.sha256(encoded.encode()).hexdigest()
        outcome_path = self.directory / f'outcomes-{input_hash}.json'
        freeze_text(outcome_path, encoded)
        results = []
        for previous_path in previous:
            original = json.loads(previous_path.read_text())
            result = evaluate_recall(original, inputs['histories'], inputs['calendar'], report['as_of_date'])
            result['input_fingerprint'] = input_hash
            result['report_fingerprint'] = hashlib.sha256(previous_path.read_bytes()).hexdigest()
            # Preserve full observations locally; send a bounded summary through the UI.
            output = self.directory / f"evaluation-{previous_path.stem}-{input_hash}.json"
            freeze_json(output, result)
            results.append({key: value for key, value in result.items()
                            if key not in {'observations', 'missing_codes'}})
        return results
