from __future__ import annotations

from summon_models.base import SummonModel, SummonResult
from summon_engine.stats import round_down
from summon_engine.runner import EngineContext, extract_passives
from summon_engine.expr import seg5
from summon_engine.loader import get_skill_field_float


class RaiseSkeletonModel(SummonModel):
    """
    Raise Skeleton model (Phase 5, robust):
      - RS internal flat damage bonus derived from skills.txt columns:
          EMin, EMinLev1..5 via seg5()
        seg5 breakpoints: 1–8 / 9–16 / 17–22 / 23–28 / 29+
      - Total flat skill bonus = SM*2 + RS_internal_flat
      - Damage% and AR/DEF and HP rules as before.
    """

    def compute(self, engine_ctx: EngineContext) -> SummonResult:
        ctx = engine_ctx
        res = SummonResult(
            skill_key=ctx.skill_key,
            monster_id=ctx.monster_id,
            difficulty=ctx.inp.difficulty.lower(),
        )

        base = ctx.base_stats
        passives = extract_passives(ctx)

        rs_lvl = int(ctx.eval_ctx.lvl)
        sm_lvl = int(ctx.eval_ctx.skill_levels_total.get("skeleton_mastery", 0))

        # RS params
        par2 = float(ctx.eval_ctx.vars.get("par2", 0.0))  # HP% per level after 3
        par3 = float(ctx.eval_ctx.vars.get("par3", 0.0))  # dmg% per level after 3
        par4 = float(ctx.eval_ctx.vars.get("par4", 0.0))  # AR per (rs+sm)
        par5 = float(ctx.eval_ctx.vars.get("par5", 0.0))  # DEF per (rs+sm)

        res.breakdown.append({"label": "Levels", "value": {"raise_skeleton": rs_lvl, "skeleton_mastery": sm_lvl}})
        res.breakdown.append({"label": "RS params", "value": {"par2_hp_pct": par2, "par3_dmg_pct": par3, "par4_ar": par4, "par5_def": par5}})

        # -------------------------
        # HP: base + base*(RS%) + SM*8
        # -------------------------
        base_hp = base.hp
        sm_hp_flat = sm_lvl * 8
        hp_pct = 0.0 if rs_lvl < 4 else (rs_lvl - 3) * par2
        hp_from_rs = base_hp * (hp_pct / 100.0)
        final_hp = base_hp + hp_from_rs + sm_hp_flat

        res.breakdown.append({
            "label": "HP steps",
            "value": {
                "base_hp": base_hp,
                "rs_hp_pct": hp_pct,
                "hp_from_rs": hp_from_rs,
                "sm_hp_flat": sm_hp_flat,
                "final_hp": final_hp,
            }
        })

        # -------------------------
        # AR/DEF
        # -------------------------
        ar_add = (rs_lvl + sm_lvl) * par4
        def_add = (rs_lvl + sm_lvl) * par5
        ar = base.attack_rating + ar_add
        defense = base.defense + def_add
        res.breakdown.append({"label": "AR scaling", "value": {"base_ar": base.attack_rating, "add": ar_add, "final_ar": ar}})
        res.breakdown.append({"label": "DEF scaling", "value": {"base_def": base.defense, "add": def_add, "final_def": defense}})

        # -------------------------
        # RS internal flat bonus from EMin/EMinLev1..5
        # -------------------------
        # NOTE: In your generated skills_raw.json, the key is "Raise Skeleton"
        # Engine normalizes keys; our ctx.skill_key is normalized from CLI input "raise_skeleton"
        # but dataset key might be "raise skeleton" or "raise_skeleton" etc.
        # We can safely access by display key "Raise Skeleton" normalized -> "raise_skeleton".
        rs_data_key = "Raise Skeleton"

        emin = get_skill_field_float(ctx.dataset, rs_data_key, "EMin")
        lev1 = get_skill_field_float(ctx.dataset, rs_data_key, "EMinLev1")
        lev2 = get_skill_field_float(ctx.dataset, rs_data_key, "EMinLev2")
        lev3 = get_skill_field_float(ctx.dataset, rs_data_key, "EMinLev3")
        lev4 = get_skill_field_float(ctx.dataset, rs_data_key, "EMinLev4")
        lev5 = get_skill_field_float(ctx.dataset, rs_data_key, "EMinLev5")

        rs_internal_flat = 0.0
        if all(v is not None for v in (emin, lev1, lev2, lev3, lev4, lev5)):
            rs_internal_flat = seg5(rs_lvl, emin, lev1, lev2, lev3, lev4, lev5)
            res.breakdown.append({
                "label": "RS internal flat (seg5)",
                "value": {
                    "EMin": emin, "EMinLev1": lev1, "EMinLev2": lev2, "EMinLev3": lev3, "EMinLev4": lev4, "EMinLev5": lev5,
                    "rs_internal_flat": rs_internal_flat,
                    "notes": "seg5 breakpoints: 1–8 / 9–16 / 17–22 / 23–28 / 29+",
                }
            })
        else:
            res.warnings.append("Could not read EMin/EMinLev1..5 for Raise Skeleton; rs_internal_flat=0 used.")
            res.breakdown.append({"label": "RS internal flat (seg5)", "value": "MISSING EMin fields -> 0"})

        # -------------------------
        # Total flat skill bonus to dmg = SM*2 + RS internal flat
        # -------------------------
        sm_flat = sm_lvl * 2
        skill_bonus = sm_flat + rs_internal_flat

        base_min = base.phys_min
        base_max = base.phys_max
        temp_min = base_min + skill_bonus
        temp_max = base_max + skill_bonus

        res.breakdown.append({
            "label": "Skill bonus (flat)",
            "value": {
                "sm_flat": sm_flat,
                "rs_internal_flat": rs_internal_flat,
                "total_skill_bonus": skill_bonus,
                "temp_min": temp_min,
                "temp_max": temp_max,
            },
        })

        # -------------------------
        # Damage% and final damage
        # -------------------------
        dmg_pct = 0.0 if rs_lvl < 4 else (rs_lvl - 3) * par3
        mult = 1.0 + (dmg_pct / 100.0)
        final_min = temp_min * mult
        final_max = temp_max * mult

        res.breakdown.append({"label": "Damage % bonus", "value": {"dmg_pct": dmg_pct, "mult": mult}})
        res.breakdown.append({"label": "Damage steps", "value": {"base_min": base_min, "base_max": base_max, "temp_min": temp_min, "temp_max": temp_max, "final_min": final_min, "final_max": final_max}})

        # keep passives visible (debug), but don't double apply
        for k, v in passives.items():
            if k in ("maxhp", "item_normaldamage", "item_pierce_damage_immunity"):
                res.breakdown.append({"label": f"Passive {k}", "value": v, "note": "debug only"})
            else:
                res.warnings.append(f"Passive '{k}' evaluated ({v}) but not used in RaiseSkeletonModel.")

        res.final = {
            "hp": round_down(final_hp),
            "phys_min": round_down(final_min),
            "phys_max": round_down(final_max),
            "defense": round_down(defense),
            "attack_rating": round_down(ar),
            "count": self._calc_count(rs_lvl),
        }

        return res

    @staticmethod
    def _calc_count(rs_lvl: int) -> int:
        if rs_lvl < 4:
            return rs_lvl
        return int(2 + (rs_lvl / 3.0))
