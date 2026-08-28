import json
from pathlib import Path

skills_p = Path(r".\data\diablo2\resurrected\helpers\generated\skills_raw.json")
desc_p   = Path(r".\data\diablo2\resurrected\helpers\generated\skilldesc_raw.json")

skills = json.loads(skills_p.read_text(encoding="utf-8"))
desc   = json.loads(desc_p.read_text(encoding="utf-8"))

skills_root = skills.get("skills") or skills
if not isinstance(skills_root, dict):
    print("[FAIL] Unexpected skills_raw.json shape:", type(skills_root))
    raise SystemExit(1)

# internal key
internal = "raise_skeleton"
srec = skills_root.get(internal)
if srec is None:
    # fallback: case-insensitive
    for k,v in skills_root.items():
        if str(k).lower() == internal:
            srec = v
            break
if srec is None:
    print("[FAIL] raise_skeleton not found in skills_raw.json")
    raise SystemExit(1)

display_name = str(srec.get("skill","")).strip()
print("Internal key:", internal)
print("Display name:", display_name)

desc_root = desc.get("rows") or desc.get("skilldesc") or desc
if not isinstance(desc_root, dict):
    print("[FAIL] Unexpected skilldesc_raw.json shape:", type(desc_root))
    raise SystemExit(1)

# find by display name (case-insensitive)
target = display_name.casefold()
rec = None
real_key = None
for k,v in desc_root.items():
    if str(k).strip().casefold() == target:
        rec = v
        real_key = k
        break

if rec is None:
    print("[FAIL] Could not find skilldesc row by display name.")
    # show closest matches containing the words
    toks = [t for t in display_name.casefold().split() if t]
    matches = []
    for k in desc_root.keys():
        kk = str(k).casefold()
        if all(t in kk for t in toks):
            matches.append(k)
    print("Partial matches (contains all tokens):", matches[:30])
    print("Sample keys:", list(desc_root.keys())[:30])
    raise SystemExit(1)

print("Matched skilldesc key:", real_key)

# print only fields that look like display-calcs
out = {}
if isinstance(rec, dict):
    for k,v in rec.items():
        lk = str(k).lower()
        if ("calc" in lk) or ("dsc" in lk) or ("desc" in lk):
            if str(v).strip():
                out[k] = v

print(json.dumps(out, indent=2, ensure_ascii=False))
