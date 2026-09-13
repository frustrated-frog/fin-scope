import subprocess
import unittest
from unittest.mock import patch
from server import capture, allowed_request

class ServerTests(unittest.TestCase):
    def test_timeout_becomes_explicit_status(self):
        with patch('server.subprocess.run', side_effect=subprocess.TimeoutExpired('reader', 15)):
            self.assertEqual('TIMEOUT', capture()['status'])

    def test_invalid_reader_output_is_not_success(self):
        with patch('server.subprocess.run', return_value=subprocess.CompletedProcess([], 0, 'not json')):
            self.assertEqual('READ_FAILED', capture()['status'])

    def test_rejects_browser_origins_and_wrong_host(self):
        self.assertFalse(allowed_request({'Host': 'evil.example:18765'}))
        self.assertFalse(allowed_request({'Host': '127.0.0.1:18765', 'Origin': 'https://evil.example'}))
        self.assertTrue(allowed_request({'Host': '127.0.0.1:18765', 'X-FinScope-Desktop': '1'}))
