"""Private SDK subprocess. Only structured, bounded data is sent to the parent."""
from contextlib import redirect_stdout
import json
import os
import socket
import sys

from finscope_market_data.providers.baostock_minutes import FIELDS, MAX_BARS


def download(code, start, end, mode='minutes'):
    import baostock as bs

    socket.setdefaulttimeout(8)
    with open(os.devnull, 'w') as quiet, redirect_stdout(quiet):
        if bs.login().error_code != '0':
            raise RuntimeError('Historical source login failed')
        try:
            if mode == 'actions':
                result = bs.query_adjust_factor(code[-2:].lower() + '.' + code[:6], start_date=start, end_date=end)
            elif mode == 'minutes':
                result = bs.query_history_k_data_plus(code[-2:].lower() + '.' + code[:6], ','.join(FIELDS),
                    start_date=start, end_date=end, frequency='5', adjustflag='3')
            else:
                raise ValueError('Unknown query mode')
            rows = []
            while result.error_code == '0' and result.next():
                rows.append(result.get_row_data())
                if len(rows) > MAX_BARS:
                    raise ValueError('Historical source exceeded row limit')
            if result.error_code != '0':
                raise RuntimeError('Historical source query failed')
            return {'fields': result.fields, 'rows': rows}
        finally:
            bs.logout()


if __name__ == '__main__':
    try:
        print(json.dumps(download(*sys.argv[1:]), allow_nan=False))
    except Exception:
        # Do not forward SDK diagnostics, account details or unbounded responses.
        sys.exit(1)
