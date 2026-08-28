#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import argparse
import csv
import json
import re
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


# -----------------------------
# Utils
# -----------------------------

def read_tsv(path: Path) -> List[Dict[str, str]]:
    """
    Reads TSV exported from D2R excel txt files (tab-separated, with header).
    Normalizes keys/values: strips whitespace; returns "" for missing.
    """
    rows: List[Dict[str, str]] = []
    with path.open("r", encoding="utf-8", errors="replace", newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for r in reader:
            rr: Dict[str, str] = {}
            for k, v in (r or {}).items():
                kk = (k or "").strip()
                vv = (v or "")
                if vv is None:
                    vv = ""
                vv = str(vv).strip()
                rr[kk] = vv
            rows.append(rr)
    return rows


def to_int(s: Optional[str]) -> Optional[int]:
    if s is None:
        return None
    s = str(s).strip()
    if s == "":
        return None
    try:
        return int(float(s))
    except Exception:
        return None


def to_float(s: Optional[str]) -> Optional[float]:
    if s is None:
        return None
    s = str(s).strip()
    if s == "":
        return None
    try:
        return float(s)
    except Exception:
        return None


def norm_key(s: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", (s or "").strip().casefold())


def pick(row: Dict[str, str], *candidates: str) -> str:
    """
    Pick first non-empty value by column name (exact match),
    then by normalized column name (alnum+casefold).
    """
    for c in candidates:
        if c in row and row[c] != "":
            return row[c]
    nmap = {norm_key(k): k for k in row.keys()}
    for c in candidates:
        nk = norm_key(c)
        if nk in nmap:
            val = row.get(nmap[nk], "")
            if val != "":
                return val
    return ""


def build_id_map(rows: List[Dict[str, str]], id_col: str = "Id") -> Dict[str, Dict[str, str]]:
    m: Dict[str, Dict[str, str]] = {}
    for r in rows:
        rid = pick(r, id_col, "id")
        if rid:
            m[rid] = r
    return m


def resolve_ref(
    ref: str,
    id_map: Dict[str, Any],
) -> Tuple[Optional[str], str]:
    """
    Resolve reference robustly:
    - exact
    - case-insensitive
    - normalized (alnum)
    Returns (resolved_id or None, how)
    """
    if not ref:
        return None, "empty"

    if ref in id_map:
        return ref, "exact"

    lower_map = {k.casefold(): k for k in id_map.keys()}
    key = ref.casefold()
    if key in lower_map:
        return lower_map[key], "case_insensitive"

    nref = norm_key(ref)
    nmap = {norm_key(k): k for k in id_map.keys()}
    if nref in nmap:
        return nmap[nref], "normalized"

    return None, "unresolved"


# -----------------------------
# Builders
# -----------------------------

def build_monlvl_ratios(monlvl_rows: List[Dict[str, str]], cap: int) -> Dict[str, Any]:
    """
    Build monlvl difficulty multipliers indexed by monster level (1..cap).
    Stores per-difficulty ratio arrays for AC/TH/HP/DM/XP.
    """
    def make_arr(field: str) -> List[Optional[float]]:
        arr: List[Optional[float]] = [None] * (cap + 1)
        for r in monlvl_rows:
            lvl = to_int(pick(r, "Level", "lvl"))
            if not lvl or lvl < 1 or lvl > cap:
                continue
            val = to_float(pick(r, field))
            arr[lvl] = val
        return arr

    # D2R monlvl.txt commonly has:
    # Normal:  AC, TH, HP, DM, XP
    # Nightmare: AC(N), TH(N), HP(N), DM(N), XP(N)
    # Hell: AC(H), TH(H), HP(H), DM(H), XP(H)
    out = {
        "meta": {"schema": "monlvl_ratios.v1", "source": "monlvl.txt", "cap": cap},
        "normal": {
            "ac": make_arr("AC"),
            "th": make_arr("TH"),
            "hp": make_arr("HP"),
            "dm": make_arr("DM"),
            "xp": make_arr("XP"),
        },
        "nightmare": {
            "ac": make_arr("AC(N)"),
            "th": make_arr("TH(N)"),
            "hp": make_arr("HP(N)"),
            "dm": make_arr("DM(N)"),
            "xp": make_arr("XP(N)"),
        },
        "hell": {
            "ac": make_arr("AC(H)"),
            "th": make_arr("TH(H)"),
            "hp": make_arr("HP(H)"),
            "dm": make_arr("DM(H)"),
            "xp": make_arr("XP(H)"),
        },
    }
    return out


def build_skills_raw(skills_rows: List[Dict[str, str]]) -> Dict[str, Any]:
    skills: Dict[str, Any] = {}
    for r in skills_rows:
        sid = pick(r, "skill", "Skill", "Id", "id")
        if not sid:
            continue
        skills[sid] = r
    return {"meta": {"schema": "skills_raw.v1", "source": "skills.txt"}, "skills": skills}


def build_skilldesc_raw(skilldesc_rows: List[Dict[str, str]]) -> Dict[str, Any]:
    desc: Dict[str, Any] = {}
    for r in skilldesc_rows:
        did = pick(r, "skilldesc", "SkillDesc", "Id", "id")
        if not did:
            continue
        desc[did] = r
    return {"meta": {"schema": "skilldesc_raw.v1", "source": "skilldesc.txt"}, "skilldesc": desc}


def build_missiles_raw(missiles_rows: List[Dict[str, str]]) -> Dict[str, Any]:
    missiles: Dict[str, Any] = {}
    for r in missiles_rows:
        mid = pick(r, "Missile", "Id", "id")
        if not mid:
            continue
        missiles[mid] = {
            "id": mid,
            "row": r,
            "etype": pick(r, "EType") or None,
            "emin": to_int(pick(r, "EMin")),
            "emax": to_int(pick(r, "EMax")),
            "elen": to_int(pick(r, "ELen")),
            "minelev": to_int(pick(r, "MinELev")),
            "maxelev": to_int(pick(r, "MaxELev")),
        }
    return {"meta": {"schema": "missiles_raw.v1", "source": "missiles.txt"}, "missiles": missiles}


def build_states_raw(states_rows: List[Dict[str, str]]) -> Dict[str, Any]:
    states: Dict[str, Any] = {}
    for r in states_rows:
        sid = pick(r, "state", "State", "Id", "id")
        if not sid:
            continue
        states[sid] = r
    return {"meta": {"schema": "states_raw.v1", "source": "states.txt"}, "states": states}


def build_pettype_raw(pettype_rows: List[Dict[str, str]]) -> Dict[str, Any]:
    pets: Dict[str, Any] = {}
    for r in pettype_rows:
        pid = pick(r, "pettype", "PetType", "Id", "id")
        if not pid:
            continue
        pets[pid] = r
    return {"meta": {"schema": "pettype_raw.v1", "source": "pettype.txt"}, "pettype": pets}


def build_itemstatcost_raw(itemstatcost_rows: List[Dict[str, str]]) -> Dict[str, Any]:
    stats: Dict[str, Any] = {}
    for r in itemstatcost_rows:
        sid = pick(r, "Stat", "stat", "Id", "id")
        if not sid:
            continue
        stats[sid] = r
    return {"meta": {"schema": "itemstatcost_raw.v1", "source": "itemstatcost.txt"}, "itemstatcost": stats}


def build_monsters_base(monstats_rows: List[Dict[str, str]]) -> Dict[str, Any]:
    monsters: Dict[str, Any] = {}
    for r in monstats_rows:
        mid = pick(r, "Id", "id")
        if not mid:
            continue

        def diff_block(suffix: str) -> Dict[str, Any]:
            suf = suffix
            return {
                "level": to_int(pick(r, f"Level{suf}")),
                "min_hp": to_int(pick(r, f"MinHP{suf}")),
                "max_hp": to_int(pick(r, f"MaxHP{suf}")),
                "ac": to_int(pick(r, f"AC{suf}")),
                "a1_min_d": to_int(pick(r, f"A1MinD{suf}")),
                "a1_max_d": to_int(pick(r, f"A1MaxD{suf}")),
                "a1_th": to_int(pick(r, f"A1TH{suf}")),
                # NEW: Attack 2 (needed e.g. Fire Golem has A2 filled in monstats)
                "a2_min_d": to_int(pick(r, f"A2MinD{suf}")),
                "a2_max_d": to_int(pick(r, f"A2MaxD{suf}")),
                "a2_th": to_int(pick(r, f"A2TH{suf}")),
                "s1_min_d": to_int(pick(r, f"S1MinD{suf}")),
                "s1_max_d": to_int(pick(r, f"S1MaxD{suf}")),
                "s1_th": to_int(pick(r, f"S1TH{suf}")),
            }

        monsters[mid] = {
            "id": mid,
            "name_str": pick(r, "NameStr") or None,
            "base": {"normal": diff_block(""), "nightmare": diff_block("(N)"), "hell": diff_block("(H)")},
        }

    return {"meta": {"schema": "monsters_base.v1", "source": "monstats.txt"}, "monsters": monsters}


_SKILL_REF_RE = re.compile(r"skill\(\s*'([^']+)'\s*\.\s*(?:lvl|blvl|par\d+)\s*\)", re.IGNORECASE)

def build_skills_refs(skills_raw: Dict[str, Any]) -> Dict[str, Any]:
    """
    Parse expressions in skills_raw and build dependency edges:
    skill -> referenced_skill(s)
    """
    edges: Dict[str, List[str]] = {}
    unresolved: Dict[str, List[str]] = {}

    skills = skills_raw.get("skills", {})
    skill_keys = set(skills.keys())
    for sk, row in skills.items():
        refs: List[str] = []
        for k, v in (row or {}).items():
            if not isinstance(v, str) or v == "":
                continue
            for m in _SKILL_REF_RE.finditer(v):
                ref = m.group(1)
                if not ref:
                    continue
                refs.append(ref)
        if refs:
            edges[sk] = sorted(set(refs))
            miss = [r for r in edges[sk] if r not in skill_keys]
            if miss:
                unresolved[sk] = miss

    return {
        "meta": {"schema": "skills_refs.v1", "source": "skills.txt"},
        "edges": edges,
        "unresolved": unresolved,
    }


# -----------------------------
# Main
# -----------------------------

def main() -> None:
    ap = argparse.ArgumentParser(description="Rebuild D2R helpers/generated dataset (v3).")
    ap.add_argument("--excel-dir", type=str, required=True, help="Path to D2R global/excel folder containing txt files.")
    ap.add_argument("--out-dir", type=str, required=True, help="Output directory for generated JSON dataset.")
    ap.add_argument("--cap", type=int, default=60, help="Max level for monlvl ratios arrays.")
    args = ap.parse_args()

    excel_dir = Path(args.excel_dir)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    skills_rows = read_tsv(excel_dir / "skills.txt")
    skilldesc_rows = read_tsv(excel_dir / "skilldesc.txt")
    missiles_rows = read_tsv(excel_dir / "missiles.txt")
    monstats_rows = read_tsv(excel_dir / "monstats.txt")
    monlvl_rows = read_tsv(excel_dir / "monlvl.txt")
    pettype_rows = read_tsv(excel_dir / "pettype.txt")
    states_rows = read_tsv(excel_dir / "states.txt")
    itemstatcost_rows = read_tsv(excel_dir / "itemstatcost.txt")

    skills_raw = build_skills_raw(skills_rows)
    skilldesc_raw = build_skilldesc_raw(skilldesc_rows)
    missiles_raw = build_missiles_raw(missiles_rows)
    monsters_base = build_monsters_base(monstats_rows)
    monlvl_ratios = build_monlvl_ratios(monlvl_rows, args.cap)
    pettype_raw = build_pettype_raw(pettype_rows)
    states_raw = build_states_raw(states_rows)
    itemstatcost_raw = build_itemstatcost_raw(itemstatcost_rows)
    skills_refs = build_skills_refs(skills_raw)

    # Write
    (out_dir / "skills_raw.json").write_text(json.dumps(skills_raw, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "skilldesc_raw.json").write_text(json.dumps(skilldesc_raw, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "missiles_raw.json").write_text(json.dumps(missiles_raw, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "monsters_base.json").write_text(json.dumps(monsters_base, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "monlvl_ratios.json").write_text(json.dumps(monlvl_ratios, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "pettype_raw.json").write_text(json.dumps(pettype_raw, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "states_raw.json").write_text(json.dumps(states_raw, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "itemstatcost_raw.json").write_text(json.dumps(itemstatcost_raw, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "skills_refs.json").write_text(json.dumps(skills_refs, ensure_ascii=False, indent=2), encoding="utf-8")

    meta = {
        "schema": "generated_index.v1",
        "excel_dir": str(excel_dir),
        "cap": args.cap,
        "files": [
            "skills_raw.json",
            "skilldesc_raw.json",
            "missiles_raw.json",
            "monsters_base.json",
            "monlvl_ratios.json",
            "pettype_raw.json",
            "states_raw.json",
            "itemstatcost_raw.json",
            "skills_refs.json",
        ],
        "notes": [
            "monsters_base now includes A2MinD/A2MaxD/A2TH fields (Attack2) from monstats to support summons like Fire Golem."
        ],
    }
    (out_dir / "index.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"[OK] Generated dataset into: {out_dir}")


if __name__ == "__main__":
    main()
