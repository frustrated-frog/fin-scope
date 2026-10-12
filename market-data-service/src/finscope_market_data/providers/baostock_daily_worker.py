"""Private bounded SDK worker; SDK diagnostics never enter the HTTP contract."""
from contextlib import redirect_stdout
import json
import os
import socket
import sys

from finscope_market_data.providers.baostock_daily import FIELDS


def download(symbol, start, end):
    import baostock as bs
    socket.setdefaulttimeout(8)
    with open(os.devnull, 'w') as quiet, redirect_stdout(quiet):
        if bs.login().error_code != '0':
            raise RuntimeError('login failed')
        try:
            result = bs.query_history_k_data_plus(symbol[:2] + '.' + symbol[2:], ','.join(FIELDS),
                start_date=start, end_date=end, frequency='d', adjustflag='2')
            rows = []
            while result.error_code == '0' and result.next():
                rows.append(result.get_row_data())
                if len(rows) > 10000:
                    raise ValueError('daily row limit')
            if result.error_code != '0':
                raise RuntimeError('query failed')
            return {'fields': result.fields, 'rows': rows}
        finally:
            bs.logout()


if __name__ == '__main__':
    try:
        print(json.dumps(download(*sys.argv[1:]), allow_nan=False))
    except Exception:
        sys.exit(1)
