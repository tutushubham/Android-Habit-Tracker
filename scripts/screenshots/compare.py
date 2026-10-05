"""compare.py <dirA> <dirB> [diffdir]: pixel comparison of same-named PNGs."""
import os, sys
from PIL import Image, ImageChops
a, b = sys.argv[1], sys.argv[2]
diffdir = sys.argv[3] if len(sys.argv) > 3 else None
bad = 0
for name in sorted(os.listdir(a)):
    if not name.endswith(".png"): continue
    pb = os.path.join(b, name)
    if not os.path.exists(pb):
        print(f"MISSING {name}"); bad += 1; continue
    ia, ib = Image.open(os.path.join(a, name)).convert("RGB"), Image.open(pb).convert("RGB")
    # The status bar (demo-mode clock and icons) still flickers between runs; compare everything below it.
    ia, ib = ia.crop((0, 64, ia.width, ia.height)), ib.crop((0, 64, ib.width, ib.height))
    if ia.size != ib.size:
        print(f"SIZE    {name} {ia.size} vs {ib.size}"); bad += 1; continue
    diff = ImageChops.difference(ia, ib)
    box = diff.getbbox()
    if box is None:
        print(f"SAME    {name}")
    else:
        n = sum(1 for p in diff.get_flattened_data() if p != (0, 0, 0))
        print(f"DIFF    {name}: {n} px in {box}"); bad += 1
        if diffdir:
            os.makedirs(diffdir, exist_ok=True)
            diff.point(lambda v: 255 if v else 0).save(os.path.join(diffdir, name))
print("identical" if bad == 0 else f"{bad} differ")
