import copy
import importlib.util
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('report_repair', Path(__file__).resolve().parents[1] / 'repair-research-reports.py')
repair = importlib.util.module_from_spec(spec)
spec.loader.exec_module(repair)


class ReportRepairTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.database = self.root / 'finance.db'
        self.path = self.root / 'vault/research-reports/thesis-3/run-9.md'
        self.path.parent.mkdir(parents=True)
        self.path.write_text('旧报告：原始内容')
        old = {'id': 1, 'research_run_id': 9, 'thesis_id': 3, 'status': 'COMPLETED', 'conclusion': '旧结论',
               'conclusion_direction': 'MIXED', 'confidence': 'LOW', 'executive_summary': '旧摘要',
               'content_markdown': '旧报告：原始内容', 'generation_mode': 'MODEL', 'warning_message': '',
               'evidence_count': 2, 'source_count': 2, 'character_count': 8,
               'markdown_path': str(self.path), 'updated_at': '2026-11-05T10:00:00'}
        with sqlite3.connect(self.database) as connection:
            fields = ','.join(name + (' INTEGER' if isinstance(value, int) else ' TEXT') for name, value in old.items())
            connection.execute('CREATE TABLE research_report(' + fields + ')')
            connection.execute('INSERT INTO research_report VALUES(' + ','.join('?' for _ in old) + ')', list(old.values()))
            connection.execute('CREATE TABLE research_run(id INTEGER,thesis_id INTEGER,status TEXT)')
            connection.execute("INSERT INTO research_run VALUES(9,3,'COMPLETED')")
            connection.execute('CREATE TABLE research_thesis(id INTEGER,conclusion TEXT,confidence TEXT,updated_at TEXT)')
            connection.execute("INSERT INTO research_thesis VALUES(3,'旧结论','LOW','before')")
            connection.execute('CREATE TABLE evidence(id INTEGER PRIMARY KEY)')
            connection.execute('CREATE TABLE thesis_finding(id INTEGER PRIMARY KEY,thesis_id INTEGER,research_run_id INTEGER,'
                               'stance TEXT,summary TEXT,evidence_id INTEGER REFERENCES evidence(id),created_at TEXT,updated_at TEXT)')
            connection.execute("INSERT INTO thesis_finding VALUES(1,3,9,'SUPPORT','旧认识',NULL,'before','before')")
            connection.execute('CREATE TABLE article(id INTEGER,body TEXT)')
            connection.execute("INSERT INTO article VALUES(7,'原始证据禁止改写')")
        replacement = {key: value for key, value in old.items() if key not in {'id', 'research_run_id', 'thesis_id', 'updated_at', 'markdown_path'}}
        replacement.update(conclusion='新结论', content_markdown='# 修复报告\n\n新内容', generation_mode='DETERMINISTIC')
        self.plan = [{'original': old, 'replacement': replacement,
                      'findings': [{'stance': 'UNKNOWN', 'summary': '新认识', 'evidence_id': None}]}]

    def test_backs_up_rows_and_files_and_keeps_raw_evidence_unchanged(self):
        backup = self.root / 'backup'
        repair.apply_plan(self.database, self.plan, backup)
        original = json.loads((backup / 'originals.json').read_text())[0]
        self.assertEqual(original['content'], '旧报告：原始内容')
        self.assertEqual(original['findings'][0]['summary'], '旧认识')
        self.assertEqual(self.path.read_text(), '# 修复报告\n\n新内容')
        with sqlite3.connect(self.database) as connection:
            self.assertEqual(connection.execute('SELECT conclusion FROM research_thesis').fetchone()[0], '新结论')
            self.assertEqual(connection.execute('SELECT summary FROM thesis_finding').fetchone()[0], '新认识')
            self.assertEqual(connection.execute('SELECT body FROM article').fetchone()[0], '原始证据禁止改写')

    def test_rejects_concurrent_changes_before_writing_files(self):
        with sqlite3.connect(self.database) as connection:
            connection.execute("UPDATE research_report SET updated_at='newer'")
        with self.assertRaises(ValueError):
            repair.apply_plan(self.database, self.plan, self.root / 'backup')
        self.assertEqual(self.path.read_text(), '旧报告：原始内容')

    def test_restores_both_database_and_markdown_if_a_write_fails(self):
        plan = copy.deepcopy(self.plan)
        plan[0]['findings'][0]['evidence_id'] = 999
        with self.assertRaises(sqlite3.IntegrityError):
            repair.apply_plan(self.database, plan, self.root / 'backup')
        self.assertEqual(self.path.read_text(), '旧报告：原始内容')
        with sqlite3.connect(self.database) as connection:
            self.assertEqual(connection.execute('SELECT conclusion FROM research_report').fetchone()[0], '旧结论')
            self.assertEqual(connection.execute('SELECT summary FROM thesis_finding').fetchone()[0], '旧认识')


if __name__ == '__main__':
    unittest.main()
