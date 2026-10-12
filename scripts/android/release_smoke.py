#!/usr/bin/env python3
"""Install the RELEASE APK (R8-shrunk, obfuscated) on an emulator and drive a first-run smoke path through it.

    python scripts/android/release_smoke.py                  # build with a throwaway key, install, run
    python scripts/android/release_smoke.py --no-build       # reuse composeApp/build/outputs/apk/release
    python scripts/android/release_smoke.py --serial emulator-5554
    python scripts/android/release_smoke.py --with-instrumented   # then also run the device test suite (debug build)
    python scripts/android/release_smoke.py --self-test      # parser checks only, needs no device

Why this is not the instrumented suite: the instrumented tests are compiled against the unobfuscated classes, so they
cannot run against an R8 build. This script uses only what a person sees (text and content descriptions read from
`uiautomator dump`), so it exercises the shrunk app as shipped: start, the first-run setup wizard with one plan
template (enum- and resource-driven code that R8 must keep), the coach marks, a check-off, restart.

It UNINSTALLS the app from the target first (that is how a differently signed build gets installed) and so wipes its
data. It therefore refuses anything that is not an emulator unless --allow-physical is given. Assumes the emulator
language is English.
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

PACKAGE = "com.habitsheet.app"
ACTIVITY = f"{PACKAGE}/{PACKAGE}.MainActivity"
ROOT = Path(__file__).resolve().parents[2]

# The app's English copy used below (composeResources/values/strings.xml).
GET_STARTED = "Get started"
GOAL = "Healthy routine"
SHOW_PLAN = "Show my plan"
CREATE_PLAN = "Create my plan"
NOT_NOW = "Not now"
DONE = "Go to my plan"
SKIP_TUTORIAL = "Skip tutorial"
HABIT = "Drink 2 litres of water"  # an every-day habit of that plan: due today whatever the weekday
ONE_DONE = re.compile(r"\b1 / \d+ completed\b")


@dataclass
class Node:
    text: str
    desc: str
    cls: str
    bounds: tuple[int, int, int, int]
    clickable: bool

    @property
    def center(self) -> tuple[int, int]:
        left, top, right, bottom = self.bounds
        return (left + right) // 2, (top + bottom) // 2


_BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def parse_dump(xml_text: str) -> list[Node]:
    """Every node of a `uiautomator dump` that has a size."""
    nodes = []
    for element in ET.fromstring(xml_text).iter("node"):
        match = _BOUNDS.fullmatch(element.get("bounds", ""))
        if not match:
            continue
        bounds = tuple(int(v) for v in match.groups())
        if bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
            continue
        nodes.append(
            Node(
                text=element.get("text", ""),
                desc=element.get("content-desc", ""),
                cls=element.get("class", ""),
                bounds=bounds,  # type: ignore[arg-type]
                clickable=element.get("clickable") == "true",
            )
        )
    return nodes


def find(nodes: list[Node], label: str, *, contains: bool = False) -> Node | None:
    """The node whose text or content description is [label] (or contains it), preferring the clickable one."""
    def hit(node: Node) -> bool:
        values = (node.text, node.desc)
        return any((label in v) if contains else (v == label) for v in values if v)

    hits = [n for n in nodes if hit(n)]
    return next((n for n in hits if n.clickable), hits[0] if hits else None)


def edit_fields(nodes: list[Node]) -> list[Node]:
    return sorted((n for n in nodes if n.cls.endswith("EditText")), key=lambda n: n.bounds[1])


def self_test() -> None:
    sample = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node text="" content-desc="Skip tutorial" class="android.view.View" clickable="true" bounds="[100,50][300,150]" />
  <node text="Get started" content-desc="" class="android.widget.Button" clickable="false" bounds="[0,400][1080,520]" />
  <node text="" content-desc="" class="android.widget.Button" clickable="true" bounds="[0,400][1080,520]" />
  <node text="Name" content-desc="" class="android.widget.EditText" clickable="true" bounds="[40,800][1040,900]" />
  <node text="" content-desc="" class="android.widget.EditText" clickable="true" bounds="[40,300][1040,400]" />
  <node text="0 / 3 completed" content-desc="" class="android.widget.TextView" bounds="[0,0][0,0]" />
  <node text="Smoke, Study" content-desc="" class="android.view.View" clickable="true" bounds="[0,1000][1080,1100]" />
</hierarchy>"""
    nodes = parse_dump(sample)
    assert len(nodes) == 6, nodes  # the zero-size node is dropped
    assert find(nodes, "Skip tutorial").center == (200, 100)
    assert find(nodes, GET_STARTED).bounds == (0, 400, 1080, 520)
    assert find(nodes, "Smoke", contains=True).center == (540, 1050)
    assert find(nodes, "nothing") is None
    assert [f.bounds[1] for f in edit_fields(nodes)] == [300, 800], "fields are ordered top to bottom"
    assert ONE_DONE.search("1 / 4 completed • 25%") and not ONE_DONE.search("11 / 12 completed")
    print("self-test ok")


class Device:
    def __init__(self, serial: str | None) -> None:
        self.base = ["adb"] + (["-s", serial] if serial else [])

    def run(self, *args: str, check: bool = True, timeout: int = 120) -> str:
        done = subprocess.run([*self.base, *args], capture_output=True, text=True, timeout=timeout, encoding="utf-8", errors="replace")
        if check and done.returncode != 0:
            raise SystemExit(f"adb {' '.join(args)} failed: {done.stderr.strip() or done.stdout.strip()}")
        return done.stdout

    def shell(self, command: str, **kw) -> str:
        return self.run("shell", command, **kw)

    def dump(self) -> list[Node]:
        self.shell("uiautomator dump /sdcard/habitsheet-ui.xml", check=False)
        xml_text = self.run("exec-out", "cat", "/sdcard/habitsheet-ui.xml", check=False)
        start = xml_text.find("<?xml")
        return parse_dump(xml_text[start:]) if start >= 0 else []

    def tap(self, node: Node) -> None:
        x, y = node.center
        self.shell(f"input tap {x} {y}")

    def type_text(self, text: str) -> None:
        self.shell("input text " + text.replace(" ", "%s"))


def wait_for(device: Device, predicate, what: str, timeout: float = 30.0):
    """Polls the screen until [predicate] returns something truthy. Waiting on UI state, not on a fixed delay."""
    deadline = time.monotonic() + timeout
    while True:
        nodes = device.dump()
        result = predicate(nodes)
        if result:
            return result
        if time.monotonic() > deadline:
            seen = sorted({n.text or n.desc for n in nodes if n.text or n.desc})[:25]
            raise SystemExit(f"FAILED: waiting for {what}. Visible: {seen}")


def build_release(keep_dir: Path) -> Path:
    env = dict(os.environ)
    if not (ROOT / "keystore.properties").exists() and "HABITSHEET_STORE_FILE" not in env:
        store = keep_dir / "smoke.jks"
        subprocess.run(
            ["keytool", "-genkeypair", "-keystore", str(store), "-storepass", "smokepass", "-keypass", "smokepass",
             "-alias", "smoke", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=smoke"],
            check=True, capture_output=True,
        )
        env.update(HABITSHEET_STORE_FILE=str(store), HABITSHEET_STORE_PASSWORD="smokepass",
                   HABITSHEET_KEY_ALIAS="smoke", HABITSHEET_KEY_PASSWORD="smokepass")
        print("built with a throwaway signing key (nothing is uploaded or kept)")
    gradlew = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    subprocess.run([gradlew, ":composeApp:assembleRelease", "--console=plain", "-q"], cwd=ROOT, env=env, check=True)
    return newest_apk()


def newest_apk() -> Path:
    apks = sorted((ROOT / "composeApp/build/outputs/apk/release").glob("*.apk"), key=lambda p: p.stat().st_mtime)
    if not apks:
        raise SystemExit("no release APK under composeApp/build/outputs/apk/release (run without --no-build)")
    return apks[-1]


def check_target(device: Device, allow_physical: bool) -> None:
    if device.run("get-state", check=False).strip() != "device":
        raise SystemExit("no device or emulator: start an emulator or pass --serial")
    emulator = device.shell("getprop ro.kernel.qemu", check=False).strip() == "1" or \
        "sdk" in device.shell("getprop ro.product.model", check=False).lower() or \
        device.shell("getprop ro.boot.qemu", check=False).strip() == "1"
    if not emulator and not allow_physical:
        raise SystemExit("this is not an emulator and the script wipes the app's data: pass --allow-physical to override")


def smoke(device: Device, apk: Path) -> None:
    print(f"installing {apk.name} ({apk.stat().st_size / 1e6:.1f} MB)")
    device.run("uninstall", PACKAGE, check=False)
    device.run("install", "-r", "-t", str(apk))
    device.run("logcat", "-c", check=False)

    def launch() -> None:
        device.shell(f"am start -W -n {ACTIVITY}")

    launch()
    print("1/7 started")
    device.tap(wait_for(device, lambda n: find(n, GET_STARTED), "the setup wizard"))
    device.tap(wait_for(device, lambda n: find(n, GOAL), f"the goal '{GOAL}'"))
    device.tap(wait_for(device, lambda n: find(n, SHOW_PLAN), f"'{SHOW_PLAN}'"))
    device.tap(wait_for(device, lambda n: find(n, CREATE_PLAN), f"'{CREATE_PLAN}'"))
    print("2/7 plan created")
    device.tap(wait_for(device, lambda n: find(n, NOT_NOW), "the reminder step"))
    device.tap(wait_for(device, lambda n: find(n, NOT_NOW), "the sheet step"))
    device.tap(wait_for(device, lambda n: find(n, DONE), f"'{DONE}'"))
    print("3/7 wizard finished")
    device.tap(wait_for(device, lambda n: find(n, SKIP_TUTORIAL), "the coach marks"))
    print("4/7 coach marks skipped")

    device.tap(wait_for(device, lambda n: find(n, HABIT, contains=True), "the plan's habit in today's list"))
    wait_for(device, lambda n: any(ONE_DONE.search(x.text) for x in n), "'1 / … completed'")
    print("5/7 checked off")

    device.shell(f"am force-stop {PACKAGE}")
    launch()
    wait_for(device, lambda n: any(ONE_DONE.search(x.text) for x in n), "the check-off after a restart")
    if find(device.dump(), GET_STARTED):
        raise SystemExit("FAILED: the setup wizard opened again after it was finished")
    print("6/7 survived a restart, no second wizard")

    log = device.run("logcat", "-d", "-t", "2000", check=False)
    crashes = [line for line in log.splitlines() if "FATAL EXCEPTION" in line or f"Process: {PACKAGE}" in line]
    if crashes:
        raise SystemExit("FAILED: the app crashed:\n" + "\n".join(crashes[:10]))
    print("7/7 no crash in the log\nrelease smoke test PASSED")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--no-build", action="store_true", help="use the APK that is already built")
    parser.add_argument("--apk", type=Path, help="install this APK instead of building one")
    parser.add_argument("--serial", help="adb serial (default: the only connected device)")
    parser.add_argument("--allow-physical", action="store_true", help="allow a phone; it wipes the app's data")
    parser.add_argument("--with-instrumented", action="store_true", help="afterwards run connectedDebugAndroidTest")
    parser.add_argument("--self-test", action="store_true", help="check the dump parser and exit")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return

    device = Device(args.serial)
    check_target(device, args.allow_physical)
    work = Path(tempfile.mkdtemp(prefix="habitsheet-smoke-"))
    try:
        apk = args.apk or (newest_apk() if args.no_build else build_release(work))
        smoke(device, apk)
    finally:
        shutil.rmtree(work, ignore_errors=True)

    if args.with_instrumented:
        gradlew = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
        subprocess.run([gradlew, ":composeApp:connectedDebugAndroidTest", "--console=plain"], cwd=ROOT, check=True)


if __name__ == "__main__":
    sys.exit(main())
