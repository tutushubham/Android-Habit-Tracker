# Screenshot comparison (visual regression check)

Used in P1-2 to prove that refactors changed nothing on screen: the same seeded data is shown by two builds on the
same Android emulator, every screen is captured, and the PNGs are compared pixel by pixel (below the status bar).
Two runs of the same build are identical, so any difference is real.

Needs: a running Android emulator (`adb` on the default SDK path, tested with a 1080×2400 Pixel image), Python 3 with
Pillow (`python3 -m venv .venv && .venv/bin/pip install pillow`), two debug APKs.

```bash
# 1. Data: a schema-5 database with 60 days of habits, sessions (incl. two per day), check-offs and weekly habits.
python3 seed_db.py ../../composeApp/src/commonMain/sqldelight/databases/5.db seeded.db 2026-10-05   # use the emulator's date

# 2. Capture (phone and tablet; light, dark and a fresh install with the tutorial) for the old and the new build.
for apk in old new; do for layout in phone tablet; do for v in "" --dark --fresh; do
  .venv/bin/python shots.py $apk.apk shots/$apk $layout $v; done; done; done

# 3. Compare (writes difference masks for any changed screen).
.venv/bin/python compare.py shots/old shots/new shots/diff
```

`--back-test` additionally checks system back from every screen, process death (`am kill`) and back on the start
screen. `seeded.db`, `shots/` and `.venv/` are scratch output; do not commit them.

Tips: a busy machine makes the emulator show "isn't responding" dialogs (`shots.py` taps Wait and retries failed `adb` calls); with a phone also connected set `ANDROID_SERIAL=emulator-5554`; capture the old and the new build on the same day (the app shows today's date); the grey gesture bar and a half-finished app restart can differ between runs: re-capture the screen before calling it a regression.
