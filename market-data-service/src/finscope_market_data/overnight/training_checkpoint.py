"""Resume failed training components from immutable, non-serving checkpoints."""
import logging

logger = logging.getLogger(__name__)


class TrainingCheckpoint:
    def __init__(self, saved=None):
        self.components = (saved or {}).get('components', {})
        self.data = (saved or {}).get('data', {})

    def run(self, name, operation, default=None):
        previous = self.components.get(name, {})
        if previous.get('status') in ('READY', 'INSUFFICIENT_DATA'):
            return previous['result']
        try:
            result = operation()
            failed = isinstance(result, dict) and result.get('status') == 'PARTIAL'
            self.components[name] = {'status': 'FAILED' if failed else 'READY' if result is not None else 'INSUFFICIENT_DATA',
                                     'result': result, 'reason': '部分训练失败' if failed else None}
            return result
        except Exception as error:
            logger.exception('Overnight training component %s failed', name)
            self.components[name] = {'status': 'FAILED', 'reason': type(error).__name__, 'result': default}
            return default

    @property
    def failed(self):
        return [key for key, value in self.components.items() if value['status'] == 'FAILED']

    def summary(self):
        return {key: {k: v for k, v in value.items() if k != 'result'} for key, value in self.components.items()}

    def dump(self):
        return {'components': self.components, 'data': self.data}
