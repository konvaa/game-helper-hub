from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Tuple, Optional
import re


def normalize_key(s: str) -> str:
    if s is None:
        return ""
    s = str(s).strip()
    if not s:
        return ""
    s = s.replace("-", " ")
    s = re.sub(r"\s+", " ", s)
    s = s.replace(" ", "_")
    return s.lower()


def load_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def _to_float(v: Any) -> Optional[float]:
    if v is None:
        return None
    s = str(v).strip()
    if s == "":
        return None
    try:
        return float(s)
    except Exception:
        return None


def _to_int(v: Any) -> Optional[int]:
    if v is None:
        return None
    s = str(v).strip()
    if s == "":
        return None
    try:
        return int(float(s))
    except Exception:
        return None


def make_vars(skill_rec: Dict[str, Any]) -> Dict[str, float]:
    out: Dict[str, float] = {}
    for i in range(1, 13):
        k = f"Param{i}"
        v = str(skill_rec.get(k, "") or "").strip()
        out[f"par{i}"] = float(v) if v != "" else 0.0
    out.setdefault("enms", 0.0)
    out.setdefault("exms", 0.0)
    out.setdefault("edmn", 0.0)
    out.setdefault("edmx", 0.0)
    return out


@dataclass
class Dataset:
    generated_dir: Path
    skills_raw: Dict[str, Any]
    monsters_base: Dict[str, Any]
    missiles_raw: Dict[str, Any]

    skills_by_key: Dict[str, Dict[str, Any]]
    vars_by_key: Dict[str, Dict[str, float]]

    missiles_by_id: Dict[str, Dict[str, Any]]


def load_dataset(generated_dir: str) -> Dataset:
    gd = Path(generated_dir)
    if not gd.exists():
        raise SystemExit(f"[FAIL] generated dir does not exist: {gd}")

    skills_raw = load_json(gd / "skills_raw.json")
    monsters_base = load_json(gd / "monsters_base.json")

    missiles_path = gd / "missiles_raw.json"
    missiles_raw: Dict[str, Any] = {}
    if missiles_path.exists():
        missiles_raw = load_json(missiles_path)

    skills = skills_raw.get("skills") or skills_raw
    if not isinstance(skills, dict):
        raise SystemExit("[FAIL] skills_raw.json missing top-level 'skills' dict")

    skills_by_key: Dict[str, Dict[str, Any]] = {}
    vars_by_key: Dict[str, Dict[str, float]] = {}

    for k, rec in skills.items():
        if not isinstance(rec, dict):
            continue
        nk = normalize_key(k)
        skills_by_key[nk] = rec
        vars_by_key[nk] = make_vars(rec)

    missiles_by_id: Dict[str, Dict[str, Any]] = {}
    missiles = missiles_raw.get("missiles") or missiles_raw
    if isinstance(missiles, dict):
        for k, rec in missiles.items():
            if not isinstance(rec, dict):
                continue
            missiles_by_id[normalize_key(k)] = rec

    return Dataset(
        generated_dir=gd,
        skills_raw=skills_raw,
        monsters_base=monsters_base,
        missiles_raw=missiles_raw,
        skills_by_key=skills_by_key,
        vars_by_key=vars_by_key,
        missiles_by_id=missiles_by_id,
    )


def get_skill(dataset: Dataset, skill_key: str) -> Dict[str, Any]:
    k = normalize_key(skill_key)
    if k not in dataset.skills_by_key:
        raise SystemExit(f"[FAIL] skill not found: {skill_key} (normalized: {k})")
    return dataset.skills_by_key[k]


def get_skill_vars(dataset: Dataset, skill_key: str) -> Dict[str, float]:
    k = normalize_key(skill_key)
    return dataset.vars_by_key.get(k, {})


def get_skill_field_float(dataset: Dataset, skill_key: str, field: str) -> Optional[float]:
    k = normalize_key(skill_key)
    rec = dataset.skills_by_key.get(k)
    if not rec:
        return None
    return _to_float(rec.get(field))


def pick_summon_monster_id(skill_rec: Dict[str, Any]) -> str:
    sm = str(skill_rec.get("summon", "") or "").strip()
    if not sm:
        raise SystemExit("[FAIL] skill has no 'summon' monster id")
    return normalize_key(sm)


def get_monster(dataset: Dataset, monster_id: str) -> Dict[str, Any]:
    mons = dataset.monsters_base.get("monsters")
    if not isinstance(mons, dict):
        raise SystemExit("[FAIL] monsters_base.json missing top-level 'monsters' dict")

    mid = normalize_key(monster_id)
    if mid in mons and isinstance(mons[mid], dict):
        return mons[mid]

    low = str(monster_id).casefold()
    for k, v in mons.items():
        if str(k).casefold() == low and isinstance(v, dict):
            return v

    raise SystemExit(f"[FAIL] monster not found: {monster_id}")


def get_missile(dataset: Dataset, missile_id: str) -> Dict[str, Any]:
    mid = normalize_key(missile_id)
    if mid in dataset.missiles_by_id:
        return dataset.missiles_by_id[mid]
    raise SystemExit(f"[FAIL] missile not found: {missile_id}")


def extract_monster_base_stats(mon_rec: Dict[str, Any], difficulty: str) -> Tuple[float, float, float, float, float]:
    diff = difficulty.lower()

    base = mon_rec.get("base")
    if isinstance(base, dict):
        b = base.get(diff)
        if isinstance(b, dict):
            hp = float(b.get("max_hp", b.get("min_hp", 0)) or 0.0)
            dmin = float(b.get("a1_min_d", 0) or 0.0)
            dmax = float(b.get("a1_max_d", 0) or 0.0)
            ac = float(b.get("ac", 0) or 0.0)
            th = float(b.get("a1_th", 0) or 0.0)
            return hp, dmin, dmax, ac, th

    hp = float(mon_rec.get(f"life_{diff}", 0.0) or 0.0)
    dmin = float(mon_rec.get(f"damage_physical_min_{diff}", 0.0) or 0.0)
    dmax = float(mon_rec.get(f"damage_physical_max_{diff}", 0.0) or 0.0)
    ac = float(mon_rec.get(f"defense_{diff}", 0.0) or 0.0)
    th = float(mon_rec.get(f"attack_rating_{diff}", 0.0) or 0.0)
    return hp, dmin, dmax, ac, th


def _row_get_int(row: Dict[str, Any], key: str, default: int = 0) -> int:
    v = row.get(key)
    iv = _to_int(v)
    return int(iv) if iv is not None else int(default)


def extract_missile_elem_scaling(missile_rec: Dict[str, Any]) -> Dict[str, Any]:
    """
    Normalized view over missile elemental damage + elemental length scaling.

    Uses excel-like row fields inside missile_rec['row'].
    """
    row = missile_rec.get("row") or {}
    if not isinstance(row, dict):
        row = {}

    etype = (row.get("EType") or row.get("etype") or "").strip().lower()
    emin = _row_get_int(row, "EMin", 0)
    emax = _row_get_int(row, "EMax", 0)

    minelev = [
        _row_get_int(row, "MinELev1", 0),
        _row_get_int(row, "MinELev2", 0),
        _row_get_int(row, "MinELev3", 0),
        _row_get_int(row, "MinELev4", 0),
        _row_get_int(row, "MinELev5", 0),
    ]
    maxelev = [
        _row_get_int(row, "MaxELev1", 0),
        _row_get_int(row, "MaxELev2", 0),
        _row_get_int(row, "MaxELev3", 0),
        _row_get_int(row, "MaxELev4", 0),
        _row_get_int(row, "MaxELev5", 0),
    ]

    # Base elemental length in frames + segment increments (ELevLen1..5)
    elen = _row_get_int(row, "ELen", 0)
    elevlen = [
        _row_get_int(row, "ELevLen1", 0),
        _row_get_int(row, "ELevLen2", 0),
        _row_get_int(row, "ELevLen3", 0),
        _row_get_int(row, "ELevLen4", 0),
        _row_get_int(row, "ELevLen5", 0),
    ]

    return {
        "etype": etype,
        "emin": emin,
        "emax": emax,
        "minelev": minelev,
        "maxelev": maxelev,
        "elen_base": elen,
        "elevlen": elevlen,
    }
