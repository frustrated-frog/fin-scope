"""Immutable, named factor implementations with development evidence and explicit code dependencies."""

import ast
import hashlib
import json

BUNDLE = "\n# FinScope frozen factor dependencies\n"


def factor_module(code):
    """Preserve module initialization. Drop the portfolio entry point for ordinary pure modules."""
    tree = ast.parse(code)
    dynamic = any(
        isinstance(n, ast.Name)
        and n.id in {"globals", "locals", "eval", "exec", "decide"}
        for n in ast.walk(tree)
    )
    ordinary = all(
        isinstance(
            n,
            (
                ast.FunctionDef,
                ast.ImportFrom,
                ast.Import,
                ast.Assign,
                ast.AnnAssign,
                ast.Expr,
                ast.ClassDef,
            ),
        )
        for n in tree.body
    )
    if ordinary and not dynamic:
        tree.body = [
            n
            for n in tree.body
            if not (isinstance(n, ast.FunctionDef) and n.name == "decide")
        ]
    return ast.unparse(tree)


def bind(code, available):
    raw = code.split(BUNDLE, 1)[0]
    try:
        tree = ast.parse(raw)
    except SyntaxError as error:
        raise ValueError("SyntaxError: " + str(error)) from error
    refs = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == "library_factor":
            raise ValueError("library_factor 是策略 SDK 提供的函数")
        if (
            isinstance(node, ast.Call)
            and isinstance(node.func, ast.Name)
            and node.func.id == "library_factor"
        ):
            arg = node.args[0] if node.args else None
            if (
                not isinstance(arg, ast.Constant)
                or not isinstance(arg.value, str)
                or arg.value not in available
            ):
                raise ValueError(
                    "请选择当前开发时点可用的因子版本，并用固定 ID 调用 library_factor"
                )
            refs.add(arg.value)
    if not refs:
        return raw, []
    modules = {
        key: (available[key]["name"], available[key]["source"]) for key in sorted(refs)
    }
    bound = (
        raw
        + BUNDLE
        + "def library_factor(asset, bars):\n    name, source = "
        + repr(modules)
        + "[asset]\n    scope = {}\n    exec(source, scope)\n    return scope['factors'](bars)[name]\n"
    )
    if len(bound) > 32000:
        raise ValueError("含冻结因子源码的策略超过 32000 字，请减少依赖")
    return bound, sorted(refs)


def schema(db):
    db.execute("""CREATE TABLE IF NOT EXISTS paper_factor_asset(id TEXT PRIMARY KEY,name TEXT NOT NULL,version INTEGER NOT NULL,
                  source TEXT NOT NULL, UNIQUE(name,version))""")
    db.execute("""CREATE TABLE IF NOT EXISTS paper_factor_observation(asset TEXT,job INTEGER,ordinal INTEGER,report TEXT,
                  knowledge TEXT, PRIMARY KEY(asset,job,ordinal))""")
    db.execute(
        """CREATE TABLE IF NOT EXISTS paper_factor_use(asset TEXT,job INTEGER,ordinal INTEGER,PRIMARY KEY(asset,job,ordinal))"""
    )
    db.execute(
        "CREATE TABLE IF NOT EXISTS paper_factor_indexed(job INTEGER,ordinal INTEGER,PRIMARY KEY(job,ordinal))"
    )


def record(db, job, ordinal, program, metrics):
    reports = metrics.get("factors", {})
    if reports:
        module = factor_module(program["code"])
        for name, report in reports.items():
            key = hashlib.sha256((name + "\0" + module).encode()).hexdigest()
            db.execute(
                """INSERT OR IGNORE INTO paper_factor_asset
                     SELECT ?,?,coalesce(max(version),0)+1,? FROM paper_factor_asset WHERE name=?""",
                (key, name, module, name),
            )
            db.execute(
                "INSERT OR IGNORE INTO paper_factor_observation VALUES(?,?,?,?,?)",
                (
                    key,
                    job,
                    ordinal,
                    json.dumps(report, allow_nan=False),
                    metrics.get("information", {}).get("knowledgeThrough"),
                ),
            )
    db.executemany(
        "INSERT OR IGNORE INTO paper_factor_use VALUES(?,?,?)",
        [(k, job, ordinal) for k in metrics.get("factorRefs", [])],
    )
    db.execute("INSERT OR IGNORE INTO paper_factor_indexed VALUES(?,?)", (job, ordinal))


def backfill(db):
    schema(db)
    rows = db.execute("""SELECT c.job,c.ordinal,c.program,c.metrics FROM paper_candidate c
        LEFT JOIN paper_factor_indexed i ON i.job=c.job AND i.ordinal=c.ordinal
        WHERE i.job IS NULL ORDER BY c.job,c.ordinal""").fetchall()
    for r in rows:
        record(
            db,
            r["job"],
            r["ordinal"],
            json.loads(r["program"]),
            json.loads(r["metrics"]),
        )


def assets(db, through=None, asset=None):
    backfill(db)
    rows = db.execute(
        """SELECT a.id,a.name,a.version,a.source,o.job,o.ordinal,o.knowledge,o.report,
             json_extract(c.program,'$.factor') AS description,json_extract(c.program,'$.name') AS strategy,
             json_extract(c.decision,'$.action') AS decision, CASE WHEN json_extract(v.history,'$.selectedCandidate')=o.ordinal THEN v.id END AS deployed
             FROM paper_factor_observation o JOIN paper_factor_asset a ON a.id=o.asset
             JOIN paper_candidate c ON c.job=o.job AND c.ordinal=o.ordinal JOIN paper_job j ON j.id=o.job
             LEFT JOIN paper_version v ON v.id=j.version
             WHERE (? IS NULL OR o.knowledge<=?) AND (? IS NULL OR a.id=?) ORDER BY o.job DESC,o.ordinal DESC,a.name""",
        (through, through, asset, asset),
    )
    result = {}
    for r in rows:
        item = result.setdefault(
            r["id"],
            {k: r[k] for k in ("id", "name", "version", "source", "description")},
        )
        evidence = dict(
            job=r["job"],
            candidate=r["ordinal"],
            knowledgeThrough=r["knowledge"],
            strategy=r["strategy"],
            report=json.loads(r["report"]),
        )
        # Historical research tools receive development evidence only.
        if through is None:
            evidence.update(decision=r["decision"], publishedVersion=r["deployed"])
        item.setdefault("studies", []).append(evidence)
        item.setdefault("references", [])
    if through is None:
        uses = db.execute("""SELECT u.*,json_extract(c.program,'$.name') AS name FROM paper_factor_use u
              JOIN paper_candidate c ON c.job=u.job AND c.ordinal=u.ordinal ORDER BY u.job DESC,u.ordinal""")
        for r in uses:
            if r["asset"] in result:
                result[r["asset"]]["references"].append(
                    dict(job=r["job"], candidate=r["ordinal"], strategy=r["name"])
                )
    return result


def catalog(db):
    backfill(db)
    return [
        dict(r)
        for r in db.execute("""SELECT a.id,a.name,a.version,count(*) AS studies,
        max(o.knowledge) AS lastKnowledge,
        (SELECT count(*) FROM paper_factor_use u WHERE u.asset=a.id) AS uses
        FROM paper_factor_asset a JOIN paper_factor_observation o ON o.asset=a.id
        GROUP BY a.id ORDER BY max(o.job) DESC,a.name,a.version DESC""")
    ]
