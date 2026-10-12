"""Print line/branch coverage per package from the Kover XML report.

    ./gradlew :composeApp:koverXmlReport
    python scripts/coverage-by-package.py [path/to/report.xml] [--depth N]

--depth N groups packages to their first N segments after com.habitsheet (default 2, 0 = full package names).
"""
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

DEFAULT = "composeApp/build/reports/kover/report.xml"
args = [a for a in sys.argv[1:] if not a.startswith("--")]
depth = 2
if "--depth" in sys.argv:
    depth = int(sys.argv[sys.argv.index("--depth") + 1])
    args = [a for a in args if a != str(depth)]
path = args[0] if args else DEFAULT

root = ET.parse(path).getroot()
totals = defaultdict(lambda: defaultdict(lambda: [0, 0]))  # group -> counter -> [missed, covered]
for package in root.iter("package"):
    name = package.get("name").replace("/", ".")
    parts = name.split(".")
    if depth and name.startswith("com.habitsheet"):
        key = ".".join(parts[: 2 + depth]) if len(parts) > 2 else name
    else:
        key = name
    for counter in package.findall("counter"):
        t = totals[key][counter.get("type")]
        t[0] += int(counter.get("missed"))
        t[1] += int(counter.get("covered"))


def pct(counts):
    missed, covered = counts
    return f"{100 * covered / (missed + covered):5.1f}%" if missed + covered else "    - "


print(f"{'package':<46}{'line':>8}{'branch':>9}{'lines':>8}")
for key in sorted(totals):
    line, branch = totals[key]["LINE"], totals[key]["BRANCH"]
    print(f"{key:<46}{pct(line):>8}{pct(branch):>9}{sum(line):>8}")
all_line = [0, 0]
all_branch = [0, 0]
for key in totals:
    for i in (0, 1):
        all_line[i] += totals[key]["LINE"][i]
        all_branch[i] += totals[key]["BRANCH"][i]
print(f"{'TOTAL':<46}{pct(all_line):>8}{pct(all_branch):>9}{sum(all_line):>8}")
