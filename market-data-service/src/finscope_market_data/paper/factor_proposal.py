"""Portable factor formulas proposed from development data. Training and the guest share one evaluator."""

import ast
from collections import Counter

from pydantic import BaseModel, Field
from .timeframes import FEATURES

INPUTS = (*FEATURES, "range15m", "closeLocation60m", "volumeShare15m")


class FactorProposal(BaseModel):
    hypothesis: str = Field(min_length=1, max_length=1000)
    factors: list[str] = Field(min_length=2, max_length=4)


def factor_inputs(returns, candles):
    values = returns.copy()
    quarter, hour = candles.get("15m"), candles.get("60m")
    if quarter:
        values["range15m"] = (quarter["high"] - quarter["low"]) / quarter["open"]
    if hour and hour["high"] > hour["low"]:
        values["closeLocation60m"] = (hour["close"] - hour["low"]) / (hour["high"] - hour["low"])
    if hour and quarter and hour["volume"] and quarter["volume"] is not None:
        values["volumeShare15m"] = quarter["volume"] / hour["volume"]
    return values


def formula_tree(node, available):
    if isinstance(node, ast.Name) and node.id in available:
        return node.id
    if isinstance(node, ast.Constant) and type(node.value) in (int, float) and abs(node.value) <= 100:
        return node.value
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
        return ["neg", formula_tree(node.operand, available)]
    if (isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == "abs"
            and len(node.args) == 1 and not node.keywords):
        return ["abs", formula_tree(node.args[0], available)]
    operators = {ast.Add: "+", ast.Sub: "-", ast.Mult: "*", ast.Div: "/"}
    if isinstance(node, ast.BinOp) and type(node.op) in operators:
        return [operators[type(node.op)], formula_tree(node.left, available), formula_tree(node.right, available)]
    raise ValueError("公式含有不可用的变量或运算")


def definitions(proposal, available):
    result = {}
    for item in proposal.factors:
        fields = [part.strip() for part in item.split("|")]
        if len(fields) != 3:
            raise ValueError("每项因子须包含名称、公式和理由")
        name, formula, reason = fields
        if not (0 < len(name) <= 40 and 0 < len(formula) <= 200 and 0 < len(reason) <= 500) or name in result:
            raise ValueError("因子名称重复或定义长度无效")
        try:
            tree = ast.parse(formula, mode="eval")
        except SyntaxError as error:
            raise ValueError("因子公式语法错误") from error
        nodes = list(ast.walk(tree))
        if len(nodes) > 60 or not any(isinstance(n, ast.Name) and n.id in available for n in nodes):
            raise ValueError("公式须引用有开发期样本的因子输入，且最多 60 个语法节点")
        compiled = formula_tree(tree.body, available)
        if any(d["tree"] == compiled for d in result.values()):
            raise ValueError("同一公式重复入选")
        result[name] = dict(formula=formula, reason=reason, tree=compiled)
    return result


def value_of(tree, inputs):
    if isinstance(tree, str):
        return inputs[tree]
    if isinstance(tree, (float, int)):
        return tree
    op = tree[0]
    a = value_of(tree[1], inputs)
    if op == "neg":
        return -a
    if op == "abs":
        return abs(a)
    b = value_of(tree[2], inputs)
    if op == "+":
        return a + b
    if op == "-":
        return a - b
    if op == "*":
        return a * b
    return a / b


def factor_values(specs, inputs):
    values = {}
    for name, spec in specs.items():
        try:
            value = value_of(spec["tree"], inputs)
            if abs(value) <= 1e12:
                values[name] = value
        except (KeyError, ArithmeticError):
            continue
    return values


def propose(rows, invoke, previous_error=""):
    counts = Counter(k for row in rows for k in row["features"])
    available = {k: count for k, count in counts.items() if count >= 50}
    if not available:
        raise ValueError("开发段不足 50 个有效标签样本")
    prompt = (
        "为沪深 A 股尾盘选股提出可检验的因子。14:45 发出买入信号，次日开盘后首次允许卖出。"
        "提交中文 hypothesis，factors 为 2 至 4 个字符串，每项格式为 中文名称 | 表达式 | 中文经济理由。"
        "表达式的高值应代表更值得买入，等权对照会对标准化后的因子取平均。"
        "可使用 + - * / abs() 和绝对值不超过 100 的常数。只用有覆盖的输入，避免重复及小数分母。"
        "return 是对应已收盘周期的收益，trend 是跨交易日收盘趋势，range15m 是15分钟相对振幅，"
        "closeLocation60m 是60分钟收盘在高低点之间的位置，volumeShare15m 是最近已收盘15分钟量与已收盘小时量之比。"
        f"开发段信号日 {min(r['signalDate'] for r in rows)} 至 {max(r['signalDate'] for r in rows)}；"
        f"可用输入及有效样本数 {available}。上次方案反馈 {previous_error[:400]}"
    )
    proposal = invoke(FactorProposal, prompt)
    return proposal, definitions(proposal, available)
