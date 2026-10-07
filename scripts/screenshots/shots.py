"""Installs an APK on the running emulator, loads the seeded DB and screenshots every screen.
Usage: shots.py <apk> <outdir> [phone|tablet] [--back-test]"""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PKG = "com.habitsheet.app"
S = os.path.dirname(os.path.abspath(__file__))
apk, out = sys.argv[1], sys.argv[2]
layout = sys.argv[3] if len(sys.argv) > 3 else "phone"
back_test = "--back-test" in sys.argv
dark = "--dark" in sys.argv
fresh = "--fresh" in sys.argv
prefix = layout + ("-fresh" if fresh else "-dark" if dark else "")
os.makedirs(out, exist_ok=True)


def adb(*args, check=True, **kw):
    # A loaded emulator drops an adb call now and then: retry a few times before giving up.
    for attempt in range(4):
        result = subprocess.run([ADB, *args], capture_output=True, text=kw.get("text", True))
        if result.returncode == 0 or not check or attempt == 3:
            if check and result.returncode != 0:
                raise subprocess.CalledProcessError(result.returncode, result.args, result.stdout, result.stderr)
            return result
        time.sleep(3)


def sh(cmd):
    return adb("shell", cmd).stdout


def setup():
    if layout == "tablet":
        sh("wm size 2560x1600"); sh("wm density 320")
    else:
        sh("wm size reset"); sh("wm density reset")
    for k in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        sh(f"settings put global {k} 0")
    sh("settings put global sysui_demo_allowed 1")
    for c in ("-e command enter", "-e command clock -e hhmm 1200", "-e command battery -e level 100 -e plugged false",
              "-e command network -e wifi show -e level 4 -e mobile hide", "-e command notifications -e visible false"):
        sh(f"am broadcast -a com.android.systemui.demo {c}")
    adb("install", "-r", "-d", apk)
    sh(f"am force-stop {PKG}")
    sh(f"run-as {PKG} sh -c 'mkdir -p databases && rm -f databases/*'")
    if not fresh:
        with open(os.path.join(S, "seeded.db"), "rb") as f:
            subprocess.run([ADB, "exec-in", "run-as", PKG, "sh", "-c", "cat > databases/habit-sheet.db"], stdin=f, check=True)
    sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(4)


def dump():
    sh("uiautomator dump /sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml").stdout
    return ET.fromstring(xml)


def find(label):
    root = dump()
    # A busy emulator shows "<system app> isn't responding": keep waiting instead of tapping through it.
    for node in root.iter("node"):
        if "isn't responding" in (node.get("text") or ""):
            for n2 in root.iter("node"):
                if n2.get("text") == "Wait":
                    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n2.get("bounds")))
                    sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
            time.sleep(2)
            root = dump()
            break
    for node in root.iter("node"):
        if label in (node.get("text"), node.get("content-desc")):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(label, wait=1.5):
    pos = find(label)
    for _ in range(3):
        if pos is not None:
            break
        w, h = (2560, 1600) if layout == "tablet" else (1080, 2400)
        sh(f"input swipe {w * 2 // 3} {h * 3 // 4} {w * 2 // 3} {h // 4} 300")
        time.sleep(1)
        pos = find(label)
    if pos is None:
        raise SystemExit(f"not found: {label}")
    sh(f"input tap {pos[0]} {pos[1]}")
    time.sleep(wait)


def tap_until(label, expect_label, tries=4):
    for _ in range(tries):
        tap(label)
        if expect_label in current_labels():
            return
    raise SystemExit(f"tapping {label} never showed {expect_label}")


def shot(name):
    time.sleep(0.8)
    png = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout
    with open(os.path.join(out, f"{prefix}-{name}.png"), "wb") as f:
        f.write(png)


def current_labels():
    return {n.get("text") or n.get("content-desc") for n in dump().iter("node")} - {None, ""}


def back(wait=1.5):
    sh("input keyevent KEYCODE_BACK")
    time.sleep(wait)


setup()
if fresh:
    shot("00-tutorial")
    tap("Skip tutorial")
    shot("01-tracker-empty")
    tap_until("Month", "Previous month"); shot("01b-month-empty"); tap_until("Day", "Previous day")
    tap("Habits"); shot("03-manage-empty")
    print("done", out)
    sys.exit(0)
if dark:
    tap("Settings"); tap("Dark")
    if layout == "phone":
        tap("Back")
    else:
        tap("Tracker")
shot("01-tracker")
tap_until("Month", "Previous month"); shot("01b-month"); tap_until("Day", "Previous day")
if layout == "phone":
    tap("Plan"); shot("02-plan"); tap("Back")
    tap("Habits"); shot("03-manage"); tap("Tracker")
    tap("Settings"); shot("04-settings")
    tap("Data & Backup"); shot("05-backup"); tap("Back")
    tap("About Habit Sheet"); shot("06-about"); tap("Back"); tap("Back")
else:
    tap("Plan"); shot("02-plan")
    tap("Habits"); shot("03-manage")
    tap("Settings"); shot("04-settings")
    tap("Data & Backup"); shot("05-backup"); tap("Back")
    tap("About Habit Sheet"); shot("06-about"); tap("Back"); tap("Tracker")
shot("07-tracker-again")

if back_test:
    results = []
    def expect(step, label):
        ok = label in current_labels()
        results.append(f"{'OK  ' if ok else 'FAIL'} {step}: expected '{label}' visible")
    nav_settings = "Settings"
    tap(nav_settings); tap("Data & Backup"); back(); expect("system back from Data & Backup", "MANAGEMENT")
    back(); expect("system back from Settings", "Share day")
    tap("Settings"); tap("About Habit Sheet"); back(); expect("system back from About", "MANAGEMENT")
    back()
    tap("Plan"); back(); expect("system back from Plan", "Share day")
    tap("Habits"); back(); expect("system back from Habits", "Share day")
    # Process death: open About, background, kill, relaunch -> About again.
    tap("Settings"); tap("About Habit Sheet")
    sh("input keyevent KEYCODE_HOME"); time.sleep(1.5)
    sh(f"am kill {PKG}"); time.sleep(1)
    sh(f"am start -W -n {PKG}/.MainActivity"); time.sleep(2.5)
    shot("08-after-process-death")
    expect("after process death", "About")
    back(); back()
    sh("input keyevent KEYCODE_BACK"); time.sleep(1.5)
    top = sh("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -1")
    results.append(("OK   " if PKG not in top else "FAIL ") + "system back on the Tracker leaves the app: " + top.strip())
    print("\n".join(results))
print("done", out)
