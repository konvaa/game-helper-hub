from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Any, Optional, Tuple, Callable

from .loader import (
    Dataset, load_dataset, normalize_key,
    get_skill, get_skill_vars, pick_summon_monster_id,
    get_monster, extract_monster_base_stats,
)
from .expr import EvalContext, eval_expr
from .stats import BaseStats
from summon_models.base import SummonResult


@dataclass
class EngineInput:
    generated_dir: str
    skill_key: str
    slvl: int
    blvl: int
    difficulty: str
    overrides_total: Dict[str, int]
    overrides_base: Dict[str, int]


@dataclass
class EngineContext:
    dataset: Dataset
    inp: EngineInput

    skill_key: str
    skill_rec: Dict[str, Any]
    monster_id: str
    monster_rec: Dict[str, Any]
    base_stats: BaseStats

    eval_ctx: EvalContext


def _build_level_maps(inp: EngineInput) -> Tuple[Dict[str, int], Dict[str, int]]:
    """
    Build maps:
      - totals: normalized skill_key -> total level (slvl)
      - bases:  normalized skill_key -> base level (blvl)
    including overrides for other skills (synergies/masteries/etc.).
    """
    total: Dict[str, int] = {normalize_key(inp.skill_key): int(inp.slvl)}
    base: Dict[str, int] = {normalize_key(inp.skill_key): int(inp.blvl)}

    for k, v in (inp.overrides_total or {}).items():
        total[normalize_key(k)] = int(v)

    for k, v in (inp.overrides_base or {}).items():
        base[normalize_key(k)] = int(v)

    return total, base


def build_context(inp: EngineInput) -> EngineContext:
    ds = load_dataset(inp.generated_dir)

    sk = normalize_key(inp.skill_key)
    skill_rec = get_skill(ds, sk)
    skill_vars = get_skill_vars(ds, sk)

    monster_id = pick_summon_monster_id(skill_rec)
    mon_rec = get_monster(ds, monster_id)

    hp, dmin, dmax, ac, th = extract_monster_base_stats(mon_rec, inp.difficulty)
    base_stats = BaseStats(hp=hp, phys_min=dmin, phys_max=dmax, defense=ac, attack_rating=th)

    totals, bases = _build_level_maps(inp)

    eval_ctx = EvalContext(
        lvl=int(totals.get(sk, 0)),
        vars=skill_vars,
        skill_levels_total=totals,
        skill_levels_base=bases,
        get_skill_vars=lambda key: get_skill_vars(ds, key),
        stats={},  # future: item/buff stats
    )

    return EngineContext(
        dataset=ds,
        inp=inp,
        skill_key=sk,
        skill_rec=skill_rec,
        monster_id=monster_id,
        monster_rec=mon_rec,
        base_stats=base_stats,
        eval_ctx=eval_ctx,
    )


def extract_passives(ctx: EngineContext) -> Dict[str, float]:
    """
    Returns map passivestat -> value (evaluated)
    passivestat1..14, passivecalc1..14
    """
    out: Dict[str, float] = {}
    rec = ctx.skill_rec
    for i in range(1, 15):
        st = rec.get(f"passivestat{i}")
        calc = rec.get(f"passivecalc{i}")
        if not st or not calc:
            continue
        stn = str(st).strip().lower()
        out[stn] = eval_expr(str(calc), ctx.eval_ctx)
    return out


def _factory_raise_skeleton():
    from summon_models.raise_skeleton import RaiseSkeletonModel
    return RaiseSkeletonModel()


def _factory_raise_skeletal_mage():
    from summon_models.raise_skeletal_mage import RaiseSkeletalMageModel
    return RaiseSkeletalMageModel()


def _factory_clay_golem():
    from summon_models.clay_golem import ClayGolemModel
    return ClayGolemModel()


# Central registry: skill_key -> model factory
MODEL_REGISTRY: Dict[str, Callable[[], Any]] = {
    "raise_skeleton": _factory_raise_skeleton,
    "raise_skeletal_mage": _factory_raise_skeletal_mage,
    "clay_golem": _factory_clay_golem,
}


def list_supported_skills() -> Dict[str, str]:
    """
    Convenience for UI or debugging: which skills have implemented models.
    Returns map skill_key -> status string.
    """
    return {k: "implemented" for k in sorted(MODEL_REGISTRY.keys())}


def load_model(skill_key: str) -> Optional[Any]:
    """
    Registry: map skill -> model.
    """
    sk = normalize_key(skill_key)
    factory = MODEL_REGISTRY.get(sk)
    return factory() if factory else None


def run_compute(inp: EngineInput) -> SummonResult:
    ctx = build_context(inp)
    model = load_model(ctx.skill_key)

    if model is None:
        res = SummonResult(
            skill_key=ctx.skill_key,
            monster_id=ctx.monster_id,
            difficulty=ctx.inp.difficulty.lower(),
        )
        res.warnings.append(f"No model implemented for skill '{ctx.skill_key}' yet.")
        # provide at least base stats
        res.final = {
            "hp": ctx.base_stats.hp,
            "phys_min": ctx.base_stats.phys_min,
            "phys_max": ctx.base_stats.phys_max,
            "defense": ctx.base_stats.defense,
            "attack_rating": ctx.base_stats.attack_rating,
        }
        return res

    return model.compute(ctx)
