"""Keep API app imports and background workers away from developer databases."""
import os
from tempfile import TemporaryDirectory

import pytest


def pytest_configure(config):
    config.finscope_test_data = TemporaryDirectory(prefix='finscope-pytest-')
    keys = ('FINSCOPE_MARKET_DATA_DATA_DIR', 'FINSCOPE_MARKET_DATA_FUYAO_API_KEY')
    config.finscope_original_env = {key: os.environ.get(key) for key in keys}
    # app.py creates its default application during test collection, before fixtures.
    os.environ[keys[0]] = config.finscope_test_data.name
    os.environ[keys[1]] = ''


@pytest.fixture(autouse=True)
def isolated_default_data_dir(tmp_path, monkeypatch):
    monkeypatch.setenv('FINSCOPE_MARKET_DATA_DATA_DIR', str(tmp_path / 'default-app-data'))


def pytest_unconfigure(config):
    for key, value in config.finscope_original_env.items():
        if value is None:
            os.environ.pop(key, None)
        else:
            os.environ[key] = value
    config.finscope_test_data.cleanup()
