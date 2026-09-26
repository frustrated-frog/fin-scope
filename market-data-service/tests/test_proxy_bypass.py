import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest
from requests.utils import get_environ_proxies

from finscope_market_data.providers.http import ProviderHttpClient, configure_direct_market_access


def test_akshare_market_hosts_bypass_proxy_and_preserve_other_routes(monkeypatch):
    monkeypatch.setenv("HTTPS_PROXY", "http://127.0.0.1:1")
    monkeypatch.setenv("NO_PROXY", "existing.example")
    monkeypatch.setenv("no_proxy", "another.example")
    configure_direct_market_access()
    configure_direct_market_access()
    for host in ("82.push2.eastmoney.com", "push2his.eastmoney.com", "hq.sinajs.cn",
                 "finance.sina.com.cn", "q.10jqka.com.cn", "existing.example", "another.example"):
        assert get_environ_proxies(f"https://{host}/") == {}
    assert get_environ_proxies("https://unrelated.example/")["https"] == "http://127.0.0.1:1"


@pytest.mark.asyncio
async def test_provider_connects_directly_despite_broken_inherited_proxy(monkeypatch):
    monkeypatch.setenv("HTTP_PROXY", "http://127.0.0.1:1")
    monkeypatch.setenv("http_proxy", "http://127.0.0.1:1")
    monkeypatch.setenv("ALL_PROXY", "http://127.0.0.1:1")
    monkeypatch.setenv("NO_PROXY", "")
    monkeypatch.setenv("no_proxy", "")

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b'{"direct": true}')

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    client = ProviderHttpClient(timeout_seconds=2)
    try:
        assert await client.get_json("TEST", f"http://127.0.0.1:{server.server_port}/") == {"direct": True}
    finally:
        await client.aclose()
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
