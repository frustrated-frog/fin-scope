"""Loopback-only, manual capture sidecar. No third-party Python dependencies."""
import json
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from parser import empty, parse_capture

READER = Path(__file__).resolve().parent / '.build' / 'ths-reader'
LOCK = threading.Lock()


def capture():
    if not LOCK.acquire(blocking=False):
        return empty('BUSY')
    try:
        result = subprocess.run([str(READER)], capture_output=True, text=True, timeout=15, check=True)
        return parse_capture(json.loads(result.stdout))
    except subprocess.TimeoutExpired:
        return empty('TIMEOUT')
    except FileNotFoundError:
        return empty('READER_UNAVAILABLE')
    except (OSError, subprocess.SubprocessError, ValueError, TypeError, KeyError):
        return empty('READ_FAILED')
    finally:
        LOCK.release()


def allowed_request(headers):
    return (headers.get('Host') in ('127.0.0.1:18765', 'localhost:18765')
            and not headers.get('Origin') and headers.get('X-FinScope-Desktop') == '1')


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(20)

    def do_POST(self):
        if not allowed_request(self.headers):
            self.send_error(403)
            return
        if self.path != '/v1/capture':
            self.send_error(404)
            return
        if self.headers.get('Transfer-Encoding') or self.headers.get('Content-Length', '0') not in ('0', '2'):
            self.send_error(400)
            return
        if self.headers.get('Content-Length') == '2':
            self.rfile.read(2)
        data = json.dumps(capture(), ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path != '/health':
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(b'{"service":"finscope-desktop-ths","status":"UP"}')

    def log_message(self, format, *args):
        pass  # No desktop contents in logs.


if __name__ == '__main__':
    print('FinScope 同花顺读取服务：http://127.0.0.1:18765', flush=True)
    HTTPServer(('127.0.0.1', 18765), Handler).serve_forever()
