"""Read-only research instruments. Every run names a reproducible source and opens no account."""
from datetime import date, timedelta
from hashlib import sha256
import json
from math import sqrt
from statistics import correlation, mean
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from finscope_market_data.forecast.trading_calendar import consecutive_observed_sessions
from . import classic_study, classic_universe, performance
from .analysis import frames
from .classic_recipe import Recipe
from .engine import ENGINE, new_book
from .portfolio_math import aligned_covariance, optimized_weights

Tool = Literal["ALLOCATION", "CORRELATION", "EXECUTION"]
METHODS = ("EQUAL", "INVERSE_VOL", "RISK_PARITY")


class StudyInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    tool: Tool
    source: str = Field(pattern=r"^[0-9a-f]{64}$")


def fingerprint(*parts):
    return sha256(json.dumps(parts, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def nominations(decisions):
    # Archive the ex-ante selections, including a deliberate move to cash.
    return {s["at"]: sorted(c for c, w in s["targets"].items() if w > 0)
            for s in decisions if not s.get("skipped")}


def catalog(lab):
    found = {}
    with lab.db() as db:
        rows = db.execute("""SELECT id,json_extract(spec,'$.rule.name') name,json_extract(spec,'$.rule.recipe') recipe,
             json_extract(history,'$.classicSnapshots') snapshots,json_extract(history,'$.universeTimeline') timeline,
             json_extract(history,'$.decisions') decisions,json_extract(history,'$.days') days,
             json_extract(history,'$.firstDay') first,json_extract(history,'$.lastDay') last,
             json_extract(spec,'$.capital') capital FROM paper_version WHERE json_extract(history,'$.engine')=?
             ORDER BY last DESC,days DESC,id ASC""", (ENGINE+"-classic-v2",))
        for r in rows:
            snaps, timeline = json.loads(r["snapshots"]), json.loads(r["timeline"])
            base = [snaps, timeline, r["first"], r["last"]]
            tape = nominations(json.loads(r["decisions"]))
            recipe = json.loads(r["recipe"])
            for tool in ("ALLOCATION", "CORRELATION"):
                if tool == "ALLOCATION":
                    if not any(len(codes) >= 2 for codes in tape.values()):
                        continue
                    settings = {k:recipe.get(k) for k in ("rebalance","riskWindow","maxWeight","exposure","stopLoss","drawdownStop","maxFeeBps","targetVol")}
                    key = fingerprint(tool, base, tape, settings, r["capital"])
                else:
                    key = fingerprint(tool, base)
                found.setdefault(key, dict(tool=tool, source=key, version=r["id"], first=r["first"], last=r["last"],
                                           observations=r["days"], title=r["name"] if tool=="ALLOCATION" else "时点股票池历史行情"))
        accounts = db.execute("""SELECT p.id,substr(p.at,1,10) day,max(p.at) last,count(*) n
            FROM paper_point p WHERE substr(p.at,12,5)<='15:00' GROUP BY p.id,substr(p.at,1,10)
            HAVING substr(max(p.at),12,5)='15:00' ORDER BY day DESC,p.id LIMIT 30""")
        for row in accounts:
            key = fingerprint("EXECUTION", row["id"], row["last"], row["n"])
            found[key] = dict(tool="EXECUTION", source=key, version=row["id"], first=row["day"], last=row["day"],
                              observations=row["n"], title=f"模拟账户 #{row['id']} 的交易记录")
    return list(found.values())


def risk_weights(method, cov, exposure, cap):
    size = len(cov)
    total = min(exposure, cap*size)
    if method == "RISK_PARITY":
        return optimized_weights(cov, method, total, cap)
    if method == "EQUAL":
        return [total/size]*size
    inv = [1/sqrt(cov[i][i]) for i in range(size)]
    # Scale inverse volatilities proportionally, redistributing only amounts above the cap.
    remaining, budget = list(range(size)), total
    weights = [0.]*size
    while remaining:
        denominator = sum(inv[i] for i in remaining)
        uncapped = {i: budget*inv[i]/denominator for i in remaining}
        capped = [i for i,w in uncapped.items() if w > cap+1e-12]
        if not capped:
            for i,w in uncapped.items():
                weights[i] = w
            break
        for i in capped:
            weights[i] = cap
            remaining.remove(i)
            budget -= cap
    return weights


class FixedSelection:
    def __init__(self, tape, recipe, method):
        self.tape = tape
        self.recipe = recipe
        self.method = method
        self.samples = []
        self.missing = []

    def decide(self, histories, book, at, equity):
        key = at.isoformat()
        if key not in self.tape:
            return {}, {}
        codes = self.tape[key]
        weights = {}
        if codes:
            histories_daily = {c:[{"dailyWindow":[bar for bar in histories[c][-1].dailyWindow
                                                    if bar["at"][:10] < key[:10]]}] for c in codes if c in histories}
            cov, dates = aligned_covariance(codes,histories_daily,self.recipe.riskWindow) if len(histories_daily)==len(codes) else (None,[])
            if cov is None:
                self.missing.append(key)
                return {}, {}
            vector = risk_weights(self.method,cov,self.recipe.exposure,self.recipe.maxWeight)
            marginal = [sum(a*b for a,b in zip(row,vector)) for row in cov]
            var = sum(a*b for a,b in zip(vector,marginal))
            vol = sqrt(var*252)
            scale = min(1.,self.recipe.targetVol/vol) if self.recipe.targetVol and vol else 1.
            weights = {c:w*scale for c,w in zip(codes,vector)}
            self.samples.append(dict(at=key,knownThrough=dates[-1][-1],volatility=vol*scale,
                                     members=[dict(code=c, weight=weights[c],risk=w*m/var) for c,w,m in zip(codes,vector,marginal)]))
        book.setdefault("strategyState",{})["selection"] = dict(at=key,candidates=codes,targets=weights)
        targets = dict(weights)
        for code in book["positions"]:
            if code in histories:
                targets.setdefault(code, 0.)
        return targets, {code: 0. for code in targets}


def allocation(panel, spec, history):
    recipe = Recipe.model_validate(spec["rule"]["recipe"])
    tape = nominations(history["decisions"])
    runs = [(FixedSelection(tape,recipe,method),new_book(spec["capital"]),[]) for method in METHODS]
    timeline = history["universeTimeline"]
    processed = set()
    for at, bars in frames(panel):
        if not history["firstDay"] <= at.date().isoformat() <= history["lastDay"]:
            continue
        entry = classic_universe.as_of(timeline,at)
        if not entry or not any(c in bars for c in entry["codes"]):
            continue
        processed.add(at.isoformat())
        for runtime, book, curve in runs:
            point = classic_study.step(book,bars,entry,at,at+timedelta(seconds=3),spec["capital"],runtime,execution=recipe)
            if at.strftime("%H:%M") == "15:00":
                curve.append({k:point[k] for k in ("at","equity","benchmark")})
    if set(tape)-processed:
        raise ValueError("冻结选股时点缺少行情，无法配对撮合")
    legs = []
    for runtime,book,curve in runs:
        if not curve or not any(len(s["members"])>=2 for s in runtime.samples):
            raise ValueError("共同历史收益样本不足以比较多股票配置")
        metrics = performance.report(curve,spec["capital"],book["fees"],book.get("executionCosts"))
        legs.append(dict(method=runtime.method,net=metrics["netReturn"],drawdown=metrics["maxDrawdown"],fees=book["fees"],
                         fills=book["fills"],turnover=metrics["turnover"],samples=runtime.samples,
                         curve=[dict(day=p["at"][:10],nav=p["equity"]/spec["capital"]) for p in curve]))
    return dict(methodVersion=2,capital=spec["capital"],decisions=len(tape),skipped=runs[0][0].missing,window=recipe.riskWindow,legs=legs,
                formula="σp² = wᵀΣw；风险贡献占比 RCᵢ = wᵢ(Σw)ᵢ / (wᵀΣw)",
                design="冻结每个历史决策时点的选中名单。三组使用相同本金、日期、风险约束与费用，各自独立撮合。Σ 用共同日收益估计，非对角项收缩 10%。",
                limitation="风险贡献对应决策时的目标仓位。整手、现金和 T+1 会使实际持仓偏离目标；缺少共同风险窗口时三组一起跳过。")


def correlation_study(panel, history):
    timeline = history["universeTimeline"]
    first, last = history["firstDay"], history["lastDay"]
    opening = next((at for at,_ in frames(panel) if at.date().isoformat() == first and classic_universe.as_of(timeline,at)), None)
    if not opening:
        raise ValueError("缺少期初可用股票名单")
    roster = classic_universe.as_of(timeline,opening)["codes"]
    stocks, proxy = roster[:5], roster[5:]
    if len(stocks)<2 or len(proxy)<3:
        raise ValueError("期初股票池不足以构造独立市场代理")
    returns = {}
    for c in stocks+proxy:
        series = {}
        for bar in panel.get(c,[]):
            day = bar.observed_at.date().isoformat()
            if bar.observed_at.strftime("%H:%M")!="15:00" or not first<=day<=last or len(bar.dailyWindow)<2:
                continue
            a,b = bar.dailyWindow[-2:]
            if (b["at"][:10]==day and consecutive_observed_sessions(date.fromisoformat(a["at"][:10]),date.fromisoformat(day))):
                series[day] = b["close"]/a["close"]-1
        returns[c] = series
    dates = sorted(set.intersection(*(set(returns[c]) for c in stocks+proxy)))
    proxy_returns = {d:mean(returns[c][d] for c in proxy) for d in dates}
    groups = []
    for name, span in (("UP",[d for d in dates if proxy_returns[d]>0]),("DOWN",[d for d in dates if proxy_returns[d]<0])):
        if len(span)<5:
            raise ValueError("上涨和下跌各需至少五个共同的日收益观测")
        pairs = []
        for i,left in enumerate(stocks):
            for right in stocks[:i]:
                a,b = [returns[left][d] for d in span],[returns[right][d] for d in span]
                if min(a)!=max(a) and min(b)!=max(b):
                    pairs.append(dict(left=left,right=right,rho=correlation(a,b)))
        if len(pairs)!=len(stocks)*(len(stocks)-1)//2:
            raise ValueError("股票日收益为常数，无法比较同一组相关系数")
        groups.append(dict(state=name,days=len(span),pairs=pairs,mean=mean(p["rho"] for p in pairs)))
    return dict(groups=groups,stocks=stocks,proxySize=len(proxy),observations=len(dates),flat=sum(r==0 for r in proxy_returns.values()),
                formula="ρᵢⱼ = Cov(rᵢ, rⱼ) / (σᵢσⱼ)",
                design="期初名单前五只为观察组，其余股票等权日收益划分同期涨跌。两组互斥，只使用完整共同交易日。",
                limitation="代理反映该股票池，不代表整个 A 股。按当日行情分段属于描述统计，不能作为事前交易信号。")


def execute(lab, plan):
    selected = next((s for s in catalog(lab) if s["source"]==plan.source and s["tool"]==plan.tool),None)
    if not selected:
        raise ValueError("所选研究材料已不可用")
    version = selected["version"]
    if plan.tool=="EXECUTION":
        with lab.db() as db:
            points = [json.loads(r[0]) for r in db.execute("SELECT point FROM paper_point WHERE id=? AND substr(at,1,10)=? ORDER BY at",(version,selected["first"]))]
            prior = db.execute("SELECT point FROM paper_point WHERE id=? AND at<? ORDER BY at DESC LIMIT 1",(version,selected["first"])).fetchone()
            spec = lab.spec(db,version)
        report = lab.day_summary(points,json.loads(prior[0]) if prior else None,spec.capital)
        metrics = dict(net=report["pnl"],fees=report["fees"],fills=report["fills"],beforeFees=report["attribution"]["beforeFees"],
                       observations=len(points),events=report["decisions"],formula="扣费后盈亏 = 期末权益 − 期初权益；扣费前盈亏 = 扣费后盈亏 + 成交费用",
                       design="按选定交易日汇总逐笔模拟成交与账户权益。信号、未成交和成交分别计数。",
                       limitation="同一委托可能跨时点等待或重新发出。各类事件的数量不等同于独立订单的成交概率。")
        codes = {row["code"] for row in metrics["events"]}
    else:
        with lab.db() as db:
            row = db.execute("SELECT spec,history FROM paper_version WHERE id=?",(version,)).fetchone()
        spec,history = map(json.loads,row)
        panel = {}
        for digest in history["classicSnapshots"]:
            panel.update(lab.jobs.read_panel(digest,daily_window=True))
        metrics = allocation(panel,spec,history) if plan.tool=="ALLOCATION" else correlation_study(panel,history)
        codes = {c for entry in history["universeTimeline"] for c in entry["codes"]}
        metrics["snapshots"] = history["classicSnapshots"]
    return dict(source=selected,metrics=metrics,names=lab.name_lookup(codes) if lab.name_lookup else {})
