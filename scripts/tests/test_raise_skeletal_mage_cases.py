from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any, Dict, Optional


PROJECT_ROOT = Path(__file__).resolve().parents[2]
BASELINE_PATH = PROJECT_ROOT / "scripts" / "tests" / "baseline_raise_skeletal_mage.json"


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


def run_cli(generated_dir: str, rsm: int, sm: int, difficulty: str) -> Dict[str, Any]:
    cli_path = PROJECT_ROOT / "scripts" / "summon_cli.py"
    cmd = [
        sys.executable,
        str(cli_path),
        "--generated-dir",
        str(generated_dir),
        "--skill",
        "raise_skeletal_mage",
        "--slvl",
        str(rsm),
        "--blvl",
        str(base_level(rsm)),
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


def dig(d: Dict[str, Any], path: str) -> Optional[Any]:
    cur: Any = d
    for part in path.split("."):
        if not isinstance(cur, dict):
            return None
        if part not in cur:
            return None
        cur = cur[part]
    return cur


def extract_runtime(payload: Dict[str, Any]) -> Dict[str, Any]:
    final = payload.get("final", {})
    extract = {
        "rsl": final.get("rsl"),
        "summon_count": final.get("summon_count", final.get("count")),
        "cold_duration_sec": dig(payload, "final.elemental_damage.cold.duration_sec"),
        "poison_total_min": dig(payload, "final.elemental_damage.poison.total_min"),
        "poison_total_max": dig(payload, "final.elemental_damage.poison.total_max"),
    }
    return {"final": final, "extract": extract}


def test_baseline_file_exists():
    assert BASELINE_PATH.exists(), (
        f"Missing baseline file: {BASELINE_PATH}\n"
        f"Generate it with:\n"
        f"  python .\\scripts\\tests\\generate_baseline_raise_skeletal_mage.py --generated-dir "
        f"\".\\data\\diablo2\\resurrected\\helpers\\generated\""
    )


def test_raise_skeletal_mage_cases_match_baseline():
    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    generated_dir = baseline["meta"]["generated_dir"]

    baseline_cases = baseline.get("cases", [])
    assert baseline_cases, "Baseline has no cases."

    for bc in baseline_cases:
        case_id = bc["id"]
        rsm = int(bc["rsm"])
        sm = int(bc["sm"])
        diff = str(bc.get("difficulty", "hell"))

        expected = bc["expected"]
        payload = run_cli(generated_dir, rsm, sm, diff)
        runtime = extract_runtime(payload)

        assert runtime["final"] == expected["final"], (
            f"[{case_id}] Final mismatch\n"
            f"Expected: {expected['final']}\n"
            f"Got:      {runtime['final']}\n"
        )

        assert runtime["extract"] == expected["extract"], (
            f"[{case_id}] Extract mismatch (internal signals)\n"
            f"Expected: {expected['extract']}\n"
            f"Got:      {runtime['extract']}\n"
        )
