"""Publish completed evidence once, without overwriting concurrent or earlier runs."""
import json
import os
from pathlib import Path
from tempfile import NamedTemporaryFile


def freeze_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(content)
        stream.flush()
        os.fsync(stream.fileno())
    try:
        os.link(temporary, path)
    except FileExistsError:
        pass
    finally:
        temporary.unlink(missing_ok=True)


def freeze_json(path: Path, value):
    freeze_text(path, json.dumps(value, ensure_ascii=False))
    return json.loads(path.read_text())
