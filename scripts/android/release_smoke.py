#!/usr/bin/env python3
"""Install the RELEASE APK (R8-shrunk, obfuscated) on an emulator and drive a first-run smoke path through it.

    python scripts/android/release_smoke.py                  # build with a throwaway key, install, run
    python scripts/android/release_smoke.py --no-build       # reuse composeApp/build/outputs/apk/release
    python scripts/android/release_smoke.py --serial emulator-5554
    python scripts/android/release_smoke.py --with-instrumented   # then also run the device test suite (debug build)
    python scripts/android/release_smoke.py --self-test      # parser checks only, needs no device

Why this is not the instrumented suite: the instrumented tests are compiled against the unobfuscated classes, so they
cannot run against an R8 build. This script uses only what a person sees (text and content descriptions read from
`uiautomator dump`), so it exercises the shrunk app as shipped: start, tutorial, add a habit, check it off, restart.

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
HABIT = "Smoke"

# The app's English copy used below (composeResources/values/strings.xml).
SKIP_TUTORIAL = "Skip tutorial"
FIRST_HABIT = "Add your first habit"
ADD_DAILY = "Add Daily Habit"
SAVE = "Save"
TRACKER = "Tracker"
ONE_OF_ONE = re.compile(r"\b1 / 1 completed\b")


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
  <node text="Add your first habit" content-desc="" class="android.widget.Button" clickable="false" bounds="[0,400][1080,520]" />
  <node text="" content-desc="" class="android.widget.Button" clickable="true" bounds="[0,400][1080,520]" />
  <node text="Name" content-desc="" class="android.widget.EditText" clickable="true" bounds="[40,800][1040,900]" />
  <node text="" content-desc="" class="android.widget.EditText" clickable="true" bounds="[40,300][1040,400]" />
  <node text="0 / 3 completed" content-desc="" class="android.widget.TextView" bounds="[0,0][0,0]" />
  <node text="Smoke, Study" content-desc="" class="android.view.View" clickable="true" bounds="[0,1000][1080,1100]" />
</hierarchy>"""
    nodes = parse_dump(sample)
    assert len(nodes) == 6, nodes  # the zero-size node is dropped
    assert find(nodes, "Skip tutorial").center == (200, 100)
    assert find(nodes, FIRST_HABIT).bounds == (0, 400, 1080, 520)
    assert find(nodes, "Smoke", contains=True).center == (540, 1050)
    assert find(nodes, "nothing") is None
    assert [f.bounds[1] for f in edit_fields(nodes)] == [300, 800], "fields are ordered top to bottom"
    assert ONE_OF_ONE.search("1 / 1 completed • 100%") and not ONE_OF_ONE.search("11 / 12 completed")
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
    print("1/6 started")
    first = wait_for(
        device,
        lambda n: find(n, SKIP_TUTORIAL) or find(n, FIRST_HABIT),
        "the tutorial or the empty state",
    )
    if find(device.dump(), SKIP_TUTORIAL):
        device.tap(first)
        print("2/6 tutorial skipped")
    else:
        print("2/6 no tutorial shown")

    device.tap(wait_for(device, lambda n: find(n, FIRST_HABIT), "'Add your first habit'"))
    device.tap(wait_for(device, lambda n: find(n, ADD_DAILY, contains=True), "'Add Daily Habit'"))
    fields = wait_for(device, lambda n: edit_fields(n) if len(edit_fields(n)) >= 2 else None, "the name and goal fields")
    device.tap(fields[0])
    device.type_text(HABIT)
    device.shell("input keyevent KEYCODE_BACK")  # close the keyboard
    fields = wait_for(device, lambda n: edit_fields(n) if len(edit_fields(n)) >= 2 else None, "the editor again")
    device.tap(fields[1])
    device.type_text("12")
    device.shell("input keyevent KEYCODE_BACK")
    device.tap(wait_for(device, lambda n: find(n, SAVE), "'Save'"))
    wait_for(device, lambda n: find(n, HABIT, contains=True), "the new habit in the list")
    print("3/6 habit added")

    device.tap(wait_for(device, lambda n: find(n, TRACKER), "the Tracker tab"))
    row = wait_for(device, lambda n: find(n, HABIT, contains=True), "the habit in today's list")
    device.tap(row)
    wait_for(device, lambda n: any(ONE_OF_ONE.search(x.text) for x in n), "'1 / 1 completed'")
    print("4/6 checked off")

    device.shell(f"am force-stop {PACKAGE}")
    launch()
    wait_for(device, lambda n: any(ONE_OF_ONE.search(x.text) for x in n), "the check-off after a restart")
    print("5/6 survived a restart")

    log = device.run("logcat", "-d", "-t", "2000", check=False)
    crashes = [line for line in log.splitlines() if "FATAL EXCEPTION" in line or f"Process: {PACKAGE}" in line]
    if crashes:
        raise SystemExit("FAILED: the app crashed:\n" + "\n".join(crashes[:10]))
    print("6/6 no crash in the log\nrelease smoke test PASSED")


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
