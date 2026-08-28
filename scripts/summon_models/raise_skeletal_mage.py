from __future__ import annotations

import math
from summon_engine.expr import eval_expr, seg5
from summon_engine.loader import get_missile, extract_missile_elem_scaling
from summon_engine.stats import round_down
from summon_models.base import SummonResult


class RaiseSkeletalMageModel:
    """
    Raise Skeletal Mage (D2R)

    - count (petmax) + alias summon_count
    - RSL (sumsk1calc) -> missile scaling
    - HP (pct jen na base HP, SM flat zvlášť)
    - DEF
    - Elemental dmg z missiles necromage1..4 škálované podle RSL (seg5)

    Duration:
      - Poison: z missiles (ELen=100 frames => 4s), total = raw*frames/256
      - Cold length: NE ze missiles.
          ColdLengthSeconds = RSM skill level
          Difficulty penalty: NM 1/2, Hell 1/4
          => frames = floor(RSM_lvl * 25 * penalty)
    """

    MAGE_MISSILES = {
        "poison": "necromage1",
        "cold": "necromage2",
        "fire": "necromage3",
        "lightning": "necromage4",
    }

    @staticmethod
    def _difficulty_cold_penalty(difficulty: str) -> float:
        d = (difficulty or "").lower()
        if d == "nightmare":
            return 0.5
        if d == "hell":
            return 0.25
        return 1.0

    def compute(self, ctx) -> SummonResult:
        res = SummonResult(
            skill_key=ctx.skill_key,
            monster_id=ctx.monster_id,
            difficulty=ctx.inp.difficulty.lower(),
        )

        # -----------------------
        # LEVELS
        # -----------------------
        rsm_lvl = int(ctx.eval_ctx.lvl)
        sm_lvl = int(ctx.eval_ctx.skill_levels_total.get("skeleton_mastery", 0))

        # -----------------------
        # COUNT
        # -----------------------
        count_expr = str(ctx.skill_rec.get("petmax") or "(lvl < 4) ? lvl : (2 + lvl/3)")
        count_val = eval_expr(count_expr, ctx.eval_ctx)
        count = int(round_down(count_val))

        # -----------------------
        # RSL (missile level)
        # -----------------------
        rsl_expr = str(
            ctx.skill_rec.get("sumsk1calc")
            or "max(skill('Skeleton Mastery'.lvl) + ((lvl < 4)?0:((lvl-2)/2)),1)"
        )
        rsl = int(round_down(eval_expr(rsl_expr, ctx.eval_ctx)))

        # -----------------------
        # DEF
        # -----------------------
        def_bonus_expr = "(lvl + skill('Skeleton Mastery'.lvl)) * par5"
        def_bonus = float(eval_expr(def_bonus_expr, ctx.eval_ctx))
        base_def = float(ctx.base_stats.defense)
        defense_final = base_def + def_bonus

        # -----------------------
        # HP
        # -----------------------
        hp_pct_expr = "(lvl < 4) ? 0 : (par2 * (lvl - 3))"
        hp_pct = float(eval_expr(hp_pct_expr, ctx.eval_ctx))

        base_hp = float(ctx.base_stats.hp)
        hp_flat_from_sm = float(sm_lvl) * 8.0
        hp_final = (base_hp * (1.0 + hp_pct / 100.0)) + hp_flat_from_sm

        # -----------------------
        # Cold duration from RSM lvl + difficulty penalty
        # -----------------------
        penalty = self._difficulty_cold_penalty(ctx.inp.difficulty)
        cold_len_frames = int(math.floor(rsm_lvl * 25.0 * penalty))
        cold_len_sec = cold_len_frames / 25.0 if cold_len_frames > 0 else 0.0

        # ======================================================
        # ELEMENT DAMAGE (MISSILES) scaled by RSL
        # ======================================================
        elemental = {}

        for label, missile_id in self.MAGE_MISSILES.items():
            try:
                missile = get_missile(ctx.dataset, missile_id)
            except Exception as ex:
                res.warnings.append(f"Missile {missile_id} not found: {ex}")
                continue

            sc = extract_missile_elem_scaling(missile)

            emin = float(sc["emin"])
            emax = float(sc["emax"])
            minelev = sc["minelev"]
            maxelev = sc["maxelev"]

            # raw damage at RSL
            raw_min = seg5(rsl, emin, *minelev)
            raw_max = seg5(rsl, emax, *maxelev)

            entry = {}

            if label == "poison":
                # poison duration from missiles (ELen base), raw values are (damage_per_frame * 256)
                elen_base = int(sc.get("elen_base") or 0)  # expected 100
                len_frames = int(elen_base)

                if len_frames > 0:
                    entry["duration_frames"] = len_frames
                    entry["duration_sec"] = len_frames / 25.0

                total_min = (raw_min * len_frames) / 256.0
                total_max = (raw_max * len_frames) / 256.0
                entry["total_min"] = round_down(total_min)
                entry["total_max"] = round_down(total_max)

                if len_frames > 0:
                    sec = len_frames / 25.0
                    entry["dps_min"] = round_down(total_min / sec)
                    entry["dps_max"] = round_down(total_max / sec)

            elif label == "cold":
                # cold duration derived from RSM lvl + difficulty penalty
                entry["min"] = round_down(raw_min)
                entry["max"] = round_down(raw_max)
                entry["duration_frames"] = cold_len_frames
                entry["duration_sec"] = cold_len_sec
                entry["_duration_source"] = "rsm_lvl_with_difficulty_penalty"

            else:
                entry["min"] = round_down(raw_min)
                entry["max"] = round_down(raw_max)

            elemental[label] = entry

        res.final = {
            "count": count,
            "summon_count": count,

            "rsl": rsl,

            "hp": hp_final,
            "hp_rounded_down": round_down(hp_final),

            "defense": defense_final,
            "defense_rounded_down": round_down(defense_final),

            "elemental_damage": elemental,
        }

        res.debug = {
            "rsm_lvl": rsm_lvl,
            "sm_lvl": sm_lvl,
            "rsl": rsl,
            "cold_penalty": penalty,
            "cold_len_frames": cold_len_frames,
            "cold_len_sec": cold_len_sec,
        }

        return res
