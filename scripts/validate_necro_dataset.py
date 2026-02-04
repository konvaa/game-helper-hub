import json
from pathlib import Path
from typing import Any, Dict, List


ROOT = Path("data/diablo2/resurrected/helpers/necromancer_summons")
SUMMONS_PATH = ROOT / "summons.json"
SKILLS_PATH = ROOT / "skills.json"


def load_json(path: Path) -> Dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as e:
        raise SystemExit(f"[FAIL] Missing file: {path}") from e
    except json.JSONDecodeError as e:
        raise SystemExit(f"[FAIL] Invalid JSON in {path}: {e}") from e


def expect(cond: bool, msg: str, errors: List[str]) -> None:
    if not cond:
        errors.append(msg)


def check_array_len(arr: Any, expected: int, label: str, errors: List[str]) -> None:
    if not isinstance(arr, list):
        errors.append(f"[TYPE] {label}: expected list, got {type(arr).__name__}")
        return
    if len(arr) != expected:
        errors.append(f"[LEN] {label}: expected {expected}, got {len(arr)}")


def warn_if_strings_in_list(arr: Any, label: str, warnings: List[str]) -> None:
    if not isinstance(arr, list):
        return
    for i, v in enumerate(arr):
        if isinstance(v, str):
            warnings.append(f"[WARN] String inside numeric table at {label}[{i}]: {v!r}")


def main() -> None:
    print("[INFO] Running dataset validation...")

    errors: List[str] = []
    warnings: List[str] = []

    summons = load_json(SUMMONS_PATH)
    skills = load_json(SKILLS_PATH)

    # --- meta checks
    expect("meta" in summons, "[STRUCT] summons.json missing meta", errors)
    expect("meta" in skills, "[STRUCT] skills.json missing meta", errors)

    level_cap_ui = summons.get("meta", {}).get("level_cap_ui")
    rsl_cap = summons.get("meta", {}).get("rsl_cap")

    expect(isinstance(level_cap_ui, int) and level_cap_ui > 0,
           f"[META] summons.meta.level_cap_ui must be positive int, got {level_cap_ui!r}", errors)
    expect(isinstance(rsl_cap, int) and rsl_cap > 0,
           f"[META] summons.meta.rsl_cap must be positive int, got {rsl_cap!r}", errors)

    # --- summons structure
    s_summons = summons.get("summons")
    expect(isinstance(s_summons, dict), "[STRUCT] summons.summons must be object", errors)

    # --- skills structure
    s_skills = skills.get("skills")
    expect(isinstance(s_skills, dict), "[STRUCT] skills.skills must be object", errors)

    if errors:
        print("\n".join(errors))
        raise SystemExit(1)

    assert isinstance(s_summons, dict)
    assert isinstance(s_skills, dict)
    assert isinstance(level_cap_ui, int)
    assert isinstance(rsl_cap, int)

    # required summon ids
    required_summon_ids = [
        "raise_skeleton",
        "raise_skeleton_mage",
        "clay_golem",
        "blood_golem",
        "iron_golem",
        "fire_golem",
        "revive",
    ]
    for sid in required_summon_ids:
        expect(sid in s_summons, f"[MISSING] summons.summons.{sid} missing", errors)

    # check table lengths in summons.json
    for sid, sdef in s_summons.items():
        t_skill = sdef.get("tables_by_skill_level", {})
        if isinstance(t_skill, dict):
            for key, arr in t_skill.items():
                check_array_len(arr, level_cap_ui, f"summons.{sid}.tables_by_skill_level.{key}", errors)
                warn_if_strings_in_list(arr, f"summons.{sid}.tables_by_skill_level.{key}", warnings)

        t_rsl = sdef.get("tables_by_rsl")
        if t_rsl is not None:
            if sid != "raise_skeleton_mage":
                warnings.append(f"[WARN] summons.{sid} has tables_by_rsl but only raise_skeleton_mage is expected to.")
            if isinstance(t_rsl, dict):
                for key, arr in t_rsl.items():
                    check_array_len(arr, rsl_cap, f"summons.{sid}.tables_by_rsl.{key}", errors)
                    warn_if_strings_in_list(arr, f"summons.{sid}.tables_by_rsl.{key}", warnings)
            else:
                errors.append(f"[TYPE] summons.{sid}.tables_by_rsl must be object, got {type(t_rsl).__name__}")

    # cross-reference: skill spawns -> summons ids
    for kid, kdef in s_skills.items():
        spawns = kdef.get("spawns")
        if spawns is None:
            continue
        if not isinstance(spawns, list):
            errors.append(f"[TYPE] skills.{kid}.spawns must be list")
            continue
        for spawn_id in spawns:
            if spawn_id not in s_summons:
                errors.append(f"[REF] skills.{kid}.spawns references missing summons id '{spawn_id}'")

    # check: skeleton_mastery has RSL rule targeting raise_skeleton_mage
    sm = s_skills.get("skeleton_mastery")
    expect(isinstance(sm, dict), "[STRUCT] skills.skeleton_mastery missing or not object", errors)
    if isinstance(sm, dict):
        special = sm.get("special_rules", {}).get("skeletal_mage_rsl")
        expect(isinstance(special, dict), "[STRUCT] skeleton_mastery.special_rules.skeletal_mage_rsl missing", errors)
        if isinstance(special, dict):
            expect(special.get("applies_to_summon_id") == "raise_skeleton_mage",
                   "[RSL] skeletal_mage_rsl.applies_to_summon_id must be 'raise_skeleton_mage'", errors)
            formula = special.get("formula")
            expect(isinstance(formula, dict), "[RSL] skeletal_mage_rsl.formula must be object", errors)
            if isinstance(formula, dict):
                definition = formula.get("definition")
                expect(isinstance(definition, str) and "RSL" in definition,
                       "[RSL] formula.definition missing or does not contain 'RSL'", errors)

    # check numeric tables in skills effects (value_by_level)
    for kid, kdef in s_skills.items():
        effects = kdef.get("effects")
        if not isinstance(effects, list):
            continue
        for ei, eff in enumerate(effects):
            vbl = eff.get("value_by_level")
            if vbl is None:
                continue
            check_array_len(vbl, kdef.get("level_cap_ui", level_cap_ui), f"skills.{kid}.effects[{ei}].value_by_level", errors)
            warn_if_strings_in_list(vbl, f"skills.{kid}.effects[{ei}].value_by_level", warnings)

    if errors:
        print("\n".join(errors))
        raise SystemExit(1)

    print("[OK] Dataset structure looks consistent.")
    if warnings:
        print("\n".join(warnings))


if __name__ == "__main__":
    main()
