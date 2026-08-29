from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any, Dict, Optional


PROJECT_ROOT = Path(__file__).resolve().parents[2]
CASES_PATH = PROJECT_ROOT / "scripts" / "tests" / "cases_raise_skeleton.json"
BASELINE_PATH = PROJECT_ROOT / "scripts" / "tests" / "baseline_raise_skeleton.json"


# Hard pointy jsou v D2 omezene sloupcem maxlvl v skills.txt (= 20 pro vsechny
# necro summon skilly). Efektivni uroven nad 20 pochazi z +skills na itemech,
# ktere zadne hard pointy nepridavaji. Baseline pripady proto deklaruji base
# uroven zastropovanou na 20, ne rovnou efektivni urovni.
#
# Na soucasne vysledky to nema vliv - modely Raise Skeleton ani Skeletal Mage
# hodnotu blvl nectou. Zacne na tom zaviset az synergie (viz
# docs/SKILLS_PROJECTION.md sekce 5), ktere se pocitaji prave z hard pointu.
HARD_POINT_CAP = 20


def base_level(effective_level: int) -> int:
    """Base (hard-point) uroven odpovidajici dane efektivni urovni."""
    return min(int(effective_level), HARD_POINT_CAP)


def run_cli(generated_dir: str, rs: int, sm: int, difficulty: str) -> Dict[str, Any]:
    cli_path = PROJECT_ROOT / "scripts" / "summon_cli.py"
    cmd = [
        sys.executable,
        str(cli_path),
        "--generated-dir",
        str(generated_dir),
        "--skill",
        "raise_skeleton",
        "--slvl",
        str(rs),
        "--blvl",
        str(base_level(rs)),
        "--difficulty",
        str(difficulty),
        "--set",
        f"skeleton_mastery.lvl={sm}",
        "--set",
        f"skeleton_mastery.blvl={base_level(sm)}",
        "--json",
    ]

    proc = subprocess.run(
        cmd,
        cwd=str(PROJECT_ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode != 0:
        raise AssertionError(
            "summon_cli.py failed\n"
            f"CMD: {' '.join(cmd)}\n"
            f"STDOUT:\n{proc.stdout}\n"
            f"STDERR:\n{proc.stderr}\n"
        )

    out = proc.stdout
    start = out.find("{")
    assert start != -1, f"CLI output did not contain JSON.\nSTDOUT:\n{proc.stdout}\nSTDERR:\n{proc.stderr}"
    out = out[start:]
    return json.loads(out)


def pick_breakdown_value(payload: Dict[str, Any], label: str) -> Optional[Dict[str, Any]]:
    for row in payload.get("breakdown", []):
        if row.get("label") == label:
            return row.get("value")
    return None


def extract_runtime(payload: Dict[str, Any]) -> Dict[str, Any]:
    rs_internal = pick_breakdown_value(payload, "RS internal flat (seg5)")
    skill_bonus = pick_breakdown_value(payload, "Skill bonus (flat)")

    return {
        "final": payload.get("final", {}),
        "extract": {
            "rs_internal_flat": rs_internal.get("rs_internal_flat") if isinstance(rs_internal, dict) else None,
            "total_skill_bonus": skill_bonus.get("total_skill_bonus") if isinstance(skill_bonus, dict) else None,
        },
    }


def test_baseline_file_exists():
    assert BASELINE_PATH.exists(), (
        f"Missing baseline file: {BASELINE_PATH}\n"
        f"Generate it with:\n"
        f"  python .\\scripts\\tests\\generate_baseline_raise_skeleton.py --generated-dir "
        f"\".\\data\\diablo2\\resurrected\\helpers\\generated\""
    )


def test_raise_skeleton_cases_match_baseline():
    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    generated_dir = baseline["meta"]["generated_dir"]

    baseline_cases = baseline.get("cases", [])
    assert baseline_cases, "Baseline has no cases."

    for bc in baseline_cases:
        case_id = bc["id"]
        rs = int(bc["rs"])
        sm = int(bc["sm"])
        diff = str(bc.get("difficulty", "hell"))

        expected = bc["expected"]
        payload = run_cli(generated_dir, rs, sm, diff)
        runtime = extract_runtime(payload)

        assert runtime["final"] == expected["final"], (
            f"[{case_id}] Final mismatch\n"
            f"Expected: {expected['final']}\n"
            f"Got:      {runtime['final']}\n"
        )

        # These are great early-warning signals when internals change:
        assert runtime["extract"] == expected["extract"], (
            f"[{case_id}] Extract mismatch (internal signals)\n"
            f"Expected: {expected['extract']}\n"
            f"Got:      {runtime['extract']}\n"
        )
