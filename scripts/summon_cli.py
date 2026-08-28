#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import argparse
import json
from typing import Dict

from summon_engine.runner import EngineInput, run_compute
from summon_engine.loader import normalize_key


def parse_set_args(set_args) -> (Dict[str, int], Dict[str, int]):
    """
    --set skeleton_mastery.lvl=20
    --set skeleton_mastery.blvl=20
    """
    totals: Dict[str, int] = {}
    bases: Dict[str, int] = {}

    for s in set_args or []:
        if "=" not in s:
            raise SystemExit(f"[FAIL] Bad --set '{s}', expected key=value")
        k, v = s.split("=", 1)
        k = k.strip()
        v = v.strip()

        try:
            ival = int(float(v))
        except Exception:
            raise SystemExit(f"[FAIL] Bad --set '{s}', value must be a number")

        if "." not in k:
            raise SystemExit(f"[FAIL] Bad --set '{s}', expected <skill>.<lvl|blvl>=N")

        sk, attr = k.rsplit(".", 1)
        sk = normalize_key(sk)
        attr = attr.lower()

        if attr == "lvl":
            totals[sk] = ival
        elif attr == "blvl":
            bases[sk] = ival
        else:
            raise SystemExit(f"[FAIL] Bad --set '{s}', attr must be lvl or blvl")

    return totals, bases


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--generated-dir", required=True)
    ap.add_argument("--skill", required=True)
    ap.add_argument("--slvl", type=int, required=True)
    ap.add_argument("--blvl", type=int, required=True)
    ap.add_argument("--difficulty", required=True, choices=["normal", "nightmare", "hell"])
    ap.add_argument("--set", action="append", default=[])
    ap.add_argument("--json", action="store_true", help="Print result as JSON")
    args = ap.parse_args()

    ot, ob = parse_set_args(args.set)

    inp = EngineInput(
        generated_dir=args.generated_dir,
        skill_key=args.skill,
        slvl=args.slvl,
        blvl=args.blvl,
        difficulty=args.difficulty,
        overrides_total=ot,
        overrides_base=ob,
    )

    res = run_compute(inp)

    if args.json:
        print(json.dumps({
            "skill_key": res.skill_key,
            "monster_id": res.monster_id,
            "difficulty": res.difficulty,
            "final": res.final,
            "breakdown": res.breakdown,
            "warnings": res.warnings,
        }, ensure_ascii=False, indent=2))
        return

    print("=== Summon Result ===")
    print(f"Skill: {res.skill_key}")
    print(f"Monster: {res.monster_id}")
    print(f"Difficulty: {res.difficulty}")
    print("")
    print("Final:")
    for k, v in res.final.items():
        print(f"  {k}: {v}")

    if res.breakdown:
        print("\nBreakdown:")
        for row in res.breakdown:
            print(f"  - {row.get('label')}: {row.get('value')}")

    if res.warnings:
        print("\nWarnings:")
        for w in res.warnings:
            print(f"  - {w}")


if __name__ == "__main__":
    main()
