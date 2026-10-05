"""Builds a realistic Habit Sheet database (schema v5) for screenshot comparisons. Usage: seed_db.py <schema 5.db> <out.db> <YYYY-MM-DD today>"""
import shutil, sqlite3, sys, datetime as dt

src, out, today_s = sys.argv[1], sys.argv[2], sys.argv[3]
today = dt.date.fromisoformat(today_s)
shutil.copy(src, out)
db = sqlite3.connect(out)
c = db.cursor()
c.execute("PRAGMA user_version = 5")
now = 1_759_600_000_000
cats = ["Career 💼", "Diet 🍎", "Family ❣️", "Fitness 💪", "Health ❤️‍🩹", "Money 💰", "Productivity 🕺", "Sleep 💤", "Social 👫", "Study 📚"]
for i, n in enumerate(cats):
    c.execute("INSERT INTO categoryEntity VALUES (?,?,?,?,?)", (f"category-{i+1}", n, i, 1, now))
start = today - dt.timedelta(days=60)
habits = [
    ("read", "Read 20 pages", "category-10", 25, 0, "ACTION", 0),
    ("water", "Drink 2L water", "category-5", 28, 1, "ACTION", 0),
    ("sugar", "No sugar", "category-2", 20, 2, "AVOIDANCE", 0),
    ("run", "Run", "category-4", 12, 3, "ACTION", 1),
    ("sleep", "In bed by 11", "category-8", 22, 4, "ACTION", 0),
]
for hid, name, cat, goal, order, kind, dated in habits:
    c.execute("INSERT INTO dailyHabitEntity VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
              (hid, name, cat, goal, order, 1, start.isoformat(), None, now, now, kind, dated))
c.execute("INSERT INTO weeklyHabitEntity VALUES (?,?,?,?,?,?,?,?,?)", ("call", "Call parents", "category-3", 0, 1, start.isoformat(), None, now, now))
c.execute("INSERT INTO weeklyHabitEntity VALUES (?,?,?,?,?,?,?,?,?)", ("budget", "Review budget", "category-6", 1, 1, start.isoformat(), None, now, now))
# Run is dated-only: sessions on Mon/Wed/Sat, two sessions on Saturdays.
d = start
while d <= today + dt.timedelta(days=14):
    if d.weekday() in (0, 2):
        c.execute("INSERT INTO dayPlanEntity VALUES (?,?,?,?,?,?)", (f"run-{d}", "run", d.isoformat(), "Easy · 5 km", 0, now))
    if d.weekday() == 5:
        c.execute("INSERT INTO dayPlanEntity VALUES (?,?,?,?,?,?)", (f"run-{d}-am", "run", d.isoformat(), "Long · 12 km", 0, now))
        c.execute("INSERT INTO dayPlanEntity VALUES (?,?,?,?,?,?)", (f"run-{d}-pm", "run", d.isoformat(), "Strides", 0, now))
    d += dt.timedelta(days=1)
c.execute("INSERT INTO weeklyPlanEntity VALUES (?,?,?,?)", ("read", 1, "Chapter review", now))
c.execute("INSERT INTO weeklyPlanEntity VALUES (?,?,?,?)", ("read", 4, "New book", now))
# Check-offs: deterministic pattern over the past 60 days (today partly done).
d = start
i = 0
while d <= today:
    for k, (hid, *_rest) in enumerate(habits):
        if hid == "run":
            continue
        if (i * 7 + k * 3) % 5 != 0 and not (d == today and k > 1):
            c.execute("INSERT INTO dailyCompletionEntity VALUES (?,?,?,?,?,?)", (f"{hid}|{d}", hid, d.isoformat(), 1, now + i, 0))
    if d.weekday() == 0 and i % 2 == 0:
        c.execute("INSERT INTO dailyCompletionEntity VALUES (?,?,?,?,?,?)", (f"run-{d}", "run", d.isoformat(), 1, now + i, 0))
    if d.weekday() == 5:
        c.execute("INSERT INTO dailyCompletionEntity VALUES (?,?,?,?,?,?)", (f"run-{d}-am", "run", d.isoformat(), 1, now + i, 0))
    d += dt.timedelta(days=1)
    i += 1
first = today.replace(day=1)
for w in range(0, 3):
    ws = first + dt.timedelta(days=7 * w)
    if ws < today:
        c.execute("INSERT INTO weeklyCompletionEntity VALUES (?,?,?,?)", ("call", ws.isoformat(), 1, now))
for key, value in [("defaults_seeded", 1), ("onboarding_completed", 1), ("theme_mode", 0)]:
    c.execute("INSERT OR REPLACE INTO settingsEntity VALUES (?,?)", (key, value))
db.commit()
db.close()
print("seeded", out)
