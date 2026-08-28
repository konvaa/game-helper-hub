"""
Pytest root config.

The engine lives under scripts/ as a set of importable packages
(summon_engine, summon_models) and scripts/tests/*.py drive them by
shelling out to scripts/summon_cli.py in a subprocess. Both paths need
scripts/ on PYTHONPATH: direct imports for anything that imports the
packages in-process, and the environment variable for the subprocess
calls made via subprocess.run(...), which inherit this process's env.

This file removes the need to manually `export PYTHONPATH=scripts`
before running pytest.
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent
SCRIPTS_DIR = str(PROJECT_ROOT / "scripts")

if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

existing = os.environ.get("PYTHONPATH", "")
if SCRIPTS_DIR not in existing.split(os.pathsep):
    os.environ["PYTHONPATH"] = os.pathsep.join(filter(None, [SCRIPTS_DIR, existing]))
