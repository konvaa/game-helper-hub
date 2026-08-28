from __future__ import annotations

import math
import re
from dataclasses import dataclass
from typing import Dict, Optional, Any, List, Tuple

from .loader import normalize_key


@dataclass
class EvalContext:
    lvl: int
    vars: Dict[str, float]

    # skill level maps (normalized skill key -> int)
    skill_levels_total: Dict[str, int]
    skill_levels_base: Dict[str, int]

    # dataset accessor callbacks
    get_skill_vars: Any  # fn(skill_key)->vars dict

    # global stats (items/buffs) - future use
    stats: Dict[str, float]


# -----------------------------
# TERNARY PARSING (D2 style)
# -----------------------------

def _scan_ternary_positions_top(expr: str) -> tuple[int, int] | None:
    s = expr
    n = len(s)

    depth_par = 0
    depth_br = 0
    in_sq = False
    in_dq = False
    esc = False

    qpos = -1
    nested = 0

    for i in range(n):
        ch = s[i]

        if esc:
            esc = False
            continue
        if ch == "\\":
            esc = True
            continue

        if in_sq:
            if ch == "'":
                in_sq = False
            continue
        if in_dq:
            if ch == '"':
                in_dq = False
            continue

        if ch == "'":
            in_sq = True
            continue
        if ch == '"':
            in_dq = True
            continue

        if ch == "(":
            depth_par += 1
            continue
        if ch == ")":
            depth_par = max(0, depth_par - 1)
            continue
        if ch == "[":
            depth_br += 1
            continue
        if ch == "]":
            depth_br = max(0, depth_br - 1)
            continue

        if depth_par != 0 or depth_br != 0:
            continue

        if ch == "?":
            if qpos < 0:
                qpos = i
                nested = 0
            else:
                nested += 1
            continue

        if ch == ":" and qpos >= 0:
            if nested == 0:
                return (qpos, i)
            nested -= 1

    return None


def _build_paren_pairs(expr: str) -> Dict[int, int]:
    s = expr
    stack: List[int] = []
    pairs: Dict[int, int] = {}

    in_sq = False
    in_dq = False
    esc = False

    for i, ch in enumerate(s):
        if esc:
            esc = False
            continue
        if ch == "\\":
            esc = True
            continue

        if in_sq:
            if ch == "'":
                in_sq = False
            continue
        if in_dq:
            if ch == '"':
                in_dq = False
            continue

        if ch == "'":
            in_sq = True
            continue
        if ch == '"':
            in_dq = True
            continue

        if ch == "(":
            stack.append(i)
        elif ch == ")":
            if stack:
                op = stack.pop()
                pairs[op] = i

    return pairs


def _find_enclosing_parens(pairs: Dict[int, int], pos: int) -> Tuple[int, int] | None:
    best = None
    for op, cp in pairs.items():
        if op < pos < cp:
            if best is None or (op > best[0] and cp < best[1]):
                best = (op, cp)
    return best


def _convert_local_ternary(expr: str) -> str:
    e = expr.strip()
    if "?" not in e:
        return e

    pos = _scan_ternary_positions_top(e)
    if not pos:
        return e

    qpos, cpos = pos
    cond = e[:qpos].strip()
    a = e[qpos + 1:cpos].strip()
    b = e[cpos + 1:].strip()

    cond_py = ternary_to_python(cond)
    a_py = ternary_to_python(a)
    b_py = ternary_to_python(b)

    return f"(({a_py}) if ({cond_py}) else ({b_py}))"


def ternary_to_python(expr: str) -> str:
    s = str(expr)
    if "?" not in s:
        return s

    while "?" in s:
        pairs = _build_paren_pairs(s)

        in_sq = in_dq = False
        esc = False
        q_index = -1
        for i, ch in enumerate(s):
            if esc:
                esc = False
                continue
            if ch == "\\":
                esc = True
                continue
            if in_sq:
                if ch == "'":
                    in_sq = False
                continue
            if in_dq:
                if ch == '"':
                    in_dq = False
                continue
            if ch == "'":
                in_sq = True
                continue
            if ch == '"':
                in_dq = True
                continue
            if ch == "?":
                q_index = i
                break

        if q_index < 0:
            break

        enc = _find_enclosing_parens(pairs, q_index)

        if enc:
            op, cp = enc
            inner = s[op + 1:cp]
            converted = _convert_local_ternary(inner)
            s = s[:op] + "(" + converted + ")" + s[cp + 1:]
        else:
            s2 = _convert_local_ternary(s)
            if s2 == s:
                break
            s = s2

    return s


# -----------------------------
# EXPR TRANSLATION
# -----------------------------

def translate_expr(expr: str) -> str:
    e = str(expr).strip()
    if not e:
        return "0"

    e = ternary_to_python(e)

    # stat('X'.accr) / stat("X".accr) / stat('X')
    e = re.sub(r"\bstat\(\s*'([^']+)'\s*\.\s*([A-Za-z0-9_]+)\s*\)", r"stat_var('\1','\2')", e, flags=re.IGNORECASE)
    e = re.sub(r'\bstat\(\s*\"([^\"]+)\"\s*\.\s*([A-Za-z0-9_]+)\s*\)', r"stat_var('\1','\2')", e, flags=re.IGNORECASE)
    e = re.sub(r"\bstat\(\s*'([^']+)'\s*\)", r"stat_val('\1')", e, flags=re.IGNORECASE)
    e = re.sub(r'\bstat\(\s*\"([^\"]+)\"\s*\)', r"stat_val('\1')", e, flags=re.IGNORECASE)

    # skill('X'.blvl) / skill('X'.lvl)
    e = re.sub(r"\bskill\(\s*'([^']+)'\s*\.\s*blvl\s*\)", r"skill_blvl('\1')", e, flags=re.IGNORECASE)
    e = re.sub(r"\bskill\(\s*'([^']+)'\s*\.\s*lvl\s*\)",  r"skill_lvl('\1')",  e, flags=re.IGNORECASE)
    e = re.sub(r'\bskill\(\s*\"([^\"]+)\"\s*\.\s*blvl\s*\)', r"skill_blvl('\1')", e, flags=re.IGNORECASE)
    e = re.sub(r'\bskill\(\s*\"([^\"]+)\"\s*\.\s*lvl\s*\)',  r"skill_lvl('\1')",  e, flags=re.IGNORECASE)

    # skill('X'.parN / lnXY / dmXY / etc.)
    e = re.sub(r"\bskill\(\s*'([^']+)'\s*\.\s*([A-Za-z0-9_]+)\s*\)", r"skill_var('\1','\2')", e, flags=re.IGNORECASE)
    e = re.sub(r'\bskill\(\s*\"([^\"]+)\"\s*\.\s*([A-Za-z0-9_]+)\s*\)', r"skill_var('\1','\2')", e, flags=re.IGNORECASE)

    # bare tokens (NOT inside quotes)
    e = re.sub(r"(?<!['\"])\blvl\b", "ctx.lvl", e)
    e = re.sub(r"(?<!['\"])\bln([1-8])([1-8])\b", r"lin(\1,\2)", e, flags=re.IGNORECASE)
    e = re.sub(r"(?<!['\"])\bdm([1-8])([1-8])\b", r"dm(\1,\2)", e, flags=re.IGNORECASE)
    e = re.sub(
        r"(?<!['\"])\bpar([0-9]{1,2})\b",
        lambda m: f"ctx.vars.get('par{m.group(1)}',0.0)",
        e,
        flags=re.IGNORECASE,
    )
    e = re.sub(
        r"(?<!['\"])\b(enms|exms|edmn|edmx)\b",
        lambda m: f"ctx.vars.get('{m.group(1).lower()}',0.0)",
        e,
        flags=re.IGNORECASE,
    )
    return e


# -----------------------------
# Helpers used by some skills
# -----------------------------

def seg5(lvl: int, base: float, lev1: float, lev2: float, lev3: float, lev4: float, lev5: float) -> float:
    lvl = int(lvl)
    if lvl <= 1:
        return float(base)

    incs = [float(lev1), float(lev2), float(lev3), float(lev4), float(lev5)]
    steps = [0, 0, 0, 0, 0]

    if lvl <= 8:
        steps[0] = lvl - 1
    elif lvl <= 16:
        steps[0] = 7
        steps[1] = lvl - 8
    elif lvl <= 22:
        steps[0] = 7
        steps[1] = 8
        steps[2] = lvl - 16
    elif lvl <= 28:
        steps[0] = 7
        steps[1] = 8
        steps[2] = 6
        steps[3] = lvl - 22
    else:
        steps[0] = 7
        steps[1] = 8
        steps[2] = 6
        steps[3] = 6
        steps[4] = lvl - 28

    val = float(base)
    for s, inc in zip(steps, incs):
        val += s * inc
    return val


def _dm_curve(mn: float, mx: float, lvl: int) -> float:
    """
    Practical D2R tooltip curve for dmXY tokens (validated on:
    - Clay Golem slow% (par3..par4) -> lvl1 ~= 11% when mx=75
    - Golem Mastery velocity% (par3..par4) -> lvl1 ~= 6% when mx=40

    Curve: mn + (mx-mn) * lvl / (lvl + K)
    with K tuned to 5.5 so that floor() matches in-game tooltip at lvl1.
    """
    lvl = max(1, int(lvl))
    K = 5.5
    if mx == mn:
        return mn
    return mn + (mx - mn) * (lvl / float(lvl + K))


# -----------------------------
# EVAL
# -----------------------------

def eval_expr(expr: Optional[str], ctx: EvalContext) -> float:
    if not expr:
        return 0.0

    code = translate_expr(expr)

    def lin(a: int, b: int) -> float:
        pa = float(ctx.vars.get(f"par{int(a)}", 0.0))
        pb = float(ctx.vars.get(f"par{int(b)}", 0.0))
        return pa + max(0, ctx.lvl - 1) * pb

    def dm(a: int, b: int) -> float:
        a = int(a)
        b = int(b)
        mn = float(ctx.vars.get(f"par{a}", 0.0))
        mx = float(ctx.vars.get(f"par{b}", 0.0))
        return _dm_curve(mn, mx, ctx.lvl)

    def skill_lvl(skill_name: str) -> float:
        k = normalize_key(skill_name)
        return float(ctx.skill_levels_total.get(k, 0))

    def skill_blvl(skill_name: str) -> float:
        k = normalize_key(skill_name)
        return float(ctx.skill_levels_base.get(k, 0))

    def skill_var(skill_name: str, var: str) -> float:
        k = normalize_key(skill_name)
        vmap = ctx.get_skill_vars(k) or {}
        var = str(var).strip().lower()

        if var in vmap:
            return float(vmap.get(var, 0.0))

        m = re.match(r"^par(\d{1,2})$", var)
        if m:
            return float(vmap.get(f"par{int(m.group(1))}", 0.0))

        sl = int(ctx.skill_levels_total.get(k, 0))

        # critical: skills at level 0 contribute nothing to ln/dm tokens
        if sl <= 0 and re.match(r"^(ln|dm)[1-8][1-8]$", var):
            return 0.0

        m = re.match(r"^ln([1-8])([1-8])$", var)
        if m:
            a = int(m.group(1))
            b = int(m.group(2))
            pa = float(vmap.get(f"par{a}", 0.0))
            pb = float(vmap.get(f"par{b}", 0.0))
            return pa + max(0, sl - 1) * pb

        m = re.match(r"^dm([1-8])([1-8])$", var)
        if m:
            a = int(m.group(1))
            b = int(m.group(2))
            mn = float(vmap.get(f"par{a}", 0.0))
            mx = float(vmap.get(f"par{b}", 0.0))
            return _dm_curve(mn, mx, sl)

        return 0.0

    def stat_val(stat_name: str) -> float:
        k = normalize_key(stat_name)
        return float(ctx.stats.get(k, 0.0))

    def stat_var(stat_name: str, _field: str) -> float:
        return stat_val(stat_name)

    safe = {
        "ctx": ctx,
        "math": math,
        "min": min,
        "max": max,
        "abs": abs,
        "floor": math.floor,
        "ceil": math.ceil,
        "round": round,
        "lin": lin,
        "dm": dm,
        "seg5": seg5,
        "skill_lvl": skill_lvl,
        "skill_blvl": skill_blvl,
        "skill_var": skill_var,
        "stat_val": stat_val,
        "stat_var": stat_var,
    }

    try:
        return float(eval(code, {"__builtins__": {}}, safe))
    except Exception as ex:
        raise RuntimeError(f"eval_expr failed: expr={expr!r} code={code!r} err={ex}") from ex
