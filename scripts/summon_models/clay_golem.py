from __future__ import annotations

from dataclasses import dataclass
from typing import List, Tuple

from summon_engine.expr import eval_expr
from summon_engine.stats import round_down
from summon_models.base import SummonResult


def _interp_piecewise(level: int, points: List[Tuple[int, float]]) -> float:
    """
    Piecewise linear interpolation.
    - exact match on known levels
    - linear between neighbors
    - clamp outside range
    """
    lvl = int(level)
    pts = sorted(points, key=lambda x: x[0])

    if lvl <= pts[0][0]:
        return float(pts[0][1])
    if lvl >= pts[-1][0]:
        return float(pts[-1][1])

    for i in range(len(pts) - 1):
        x0, y0 = pts[i]
        x1, y1 = pts[i + 1]
        if lvl == x0:
            return float(y0)
        if x0 < lvl < x1:
            t = (lvl - x0) / float(x1 - x0)
            return float(y0 + (y1 - y0) * t)

    return float(pts[-1][1])


def _gm_velocity_curve(lvl: int, mn: float, mx: float) -> float:
    """
    Special curve for Golem Mastery velocity% (tooltip behavior).

    We fit a simple "power rational" curve:
        y = mn + (mx-mn) * (lvl^p) / (lvl^p + K)

    Tuned to match your observed tooltips:
      lvl 1 -> 6%
      lvl 3 -> 14%
      lvl 9 -> 26%

    This curve produces fractional values; tooltip behaves like rounding-to-nearest
    for this stat (otherwise lvl9 would land too low).
    """
    lvl = max(0, int(lvl))
    if lvl <= 0:
        return 0.0
    if mx == mn:
        return float(mn)

    p = 1.09
    K = 5.991145572786393

    lp = float(lvl) ** p
    y = float(mn) + (float(mx) - float(mn)) * (lp / (lp + K))

    # tooltip-like rounding (nearest integer)
    return float(int(round(y)))


@dataclass
class ClayGolemModel:
    """
    Necromancer: Clay Golem (D2R).

    Slow%:
    - aurastat=item_slow, aurastatcalc=dm34
    - empiricky má jinou křivku -> používáme ověřenou tabulku + interpolaci

    Velocity%:
    - passivecalc1 = skill('Golem Mastery'.dm34)
    - empiricky GM dm34 neodpovídá stejné křivce jako naše obecné dm()
      -> používáme special křivku pro GM velocity
    """

    # Ověřené body z tvých dat (skill level -> slow%)
    SLOW_POINTS: List[Tuple[int, float]] = (
        (1, 11.0),
        (2, 20.0),
        (3, 27.0),
        (4, 33.0),
        (5, 37.0),
        (7, 44.0),
        (10, 51.0),
        (15, 58.0),
        (20, 63.0),
        (30, 68.0),
    )

    def compute(self, ctx) -> SummonResult:
        base = ctx.base_stats
        rec = ctx.skill_rec

        # --- HP% modifier (calc1 includes GM HP% + Blood synergy as encoded in skills.txt) ---
        hp_bonus_pct = float(eval_expr(rec.get("calc1"), ctx.eval_ctx))
        hp_mult = (100.0 + hp_bonus_pct) / 100.0
        hp_final = float(base.hp) * hp_mult

        # --- AR / Damage / Defense passives ---
        tohit_flat = float(eval_expr(rec.get("passivecalc2"), ctx.eval_ctx))
        dmg_pct = float(eval_expr(rec.get("passivecalc3"), ctx.eval_ctx))

        # armorclass: Iron synergy uses TOTAL level (iron.lvl), not blvl
        armor_flat = float(eval_expr("skill('IronGolem'.lvl) * skill('IronGolem'.par8)", ctx.eval_ctx))

        # --- Slow% (item_slow aura) ---
        slow_pct = _interp_piecewise(ctx.eval_ctx.lvl, list(self.SLOW_POINTS))

        # --- Velocity% (GM special curve) ---
        gm_lvl = int(ctx.eval_ctx.skill_levels_total.get("golem_mastery", 0))
        gm_vars = ctx.eval_ctx.get_skill_vars("golem_mastery") or {}
        # GM dm34 uses par3..par4 in GM vars
        gm_mn = float(gm_vars.get("par3", 0.0))
        gm_mx = float(gm_vars.get("par4", 0.0))
        velocity_pct = _gm_velocity_curve(gm_lvl, gm_mn, gm_mx)

        # --- Final stats ---
        ar_final = float(base.attack_rating) + tohit_flat
        def_final = float(base.defense) + armor_flat

        dmg_mult = (100.0 + dmg_pct) / 100.0
        phys_min = float(base.phys_min) * dmg_mult
        phys_max = float(base.phys_max) * dmg_mult

        res = SummonResult(
            skill_key=ctx.skill_key,
            monster_id=ctx.monster_id,
            difficulty=ctx.inp.difficulty.lower(),
        )

        res.final = {
            "hp": round_down(hp_final),
            "phys_min": round_down(phys_min),
            "phys_max": round_down(phys_max),
            "defense": round_down(def_final),
            "attack_rating": round_down(ar_final),
            "slow_percent": round_down(slow_pct),
            # velocity already rounded to tooltip-like integer in _gm_velocity_curve
            "velocity_percent": int(velocity_pct),
        }

        res.debug = {
            "base": {
                "hp": base.hp,
                "phys_min": base.phys_min,
                "phys_max": base.phys_max,
                "defense": base.defense,
                "attack_rating": base.attack_rating,
            },
            "hp_bonus_pct": hp_bonus_pct,
            "hp_mult": hp_mult,
            "tohit_flat": tohit_flat,
            "armor_flat": armor_flat,
            "damage_percent": dmg_pct,
            "damage_mult": dmg_mult,
            "slow_percent_raw": slow_pct,
            "gm_lvl": gm_lvl,
            "gm_par3": gm_mn,
            "gm_par4": gm_mx,
            "velocity_percent_raw": velocity_pct,
        }

        return res
