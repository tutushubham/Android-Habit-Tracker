import fs from 'node:fs/promises';
import path from 'node:path';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';

// The same rows generate the import-ready workbook and the app's offline seed.
const start = new Date('2026-10-01T00:00:00Z');
const end = new Date('2026-12-31T00:00:00Z');
const day = 86400000;
const iso = date => date.toISOString().slice(0, 10);
const weekday = date => date.getUTCDay();
const rows = [];
function add(date, area, habit, session, skip = false, source = 'OND plan') {
  const base = `${habit.toLowerCase().replaceAll(' ', '-')}-${date}`;
  const count = rows.filter(row => row.id === base || row.id.startsWith(`${base}-`)).length;
  rows.push({ id: count ? `${base}-${count + 1}` : base, date, area, habit, session, done: false, skip, source });
}

// Direct transcription of the dated running sessions in the supplied Marathon Plan tab.
const taper = {
  '2026-10-01': 'Easy run · 6–7 km @ 8:20–8:50/km; mobility',
  '2026-10-03': 'Easy run · 5 km @ 8:30–9:00/km; mobility',
  '2026-10-04': 'Long run · 13–14 km easy; recovery only',
  '2026-10-06': 'Easy run · 5–6 km; mobility',
  '2026-10-08': 'Steady run · 7 km including about 3 km moderate; mobility',
  '2026-10-10': 'Very easy run · 4–5 km; mobility',
  '2026-10-11': 'Peak long run · 16–17 km easy; recovery only',
  '2026-10-13': 'Easy run · 5 km; mobility',
  '2026-10-14': 'Easy run · 4 km + 3 × 20 sec strides; mobility',
  '2026-10-16': 'Shakeout · 3 km very easy; mobility',
  '2026-10-18': 'Half marathon · 21.1 km; easy recovery walk',
};
const recoveryRun = {
  '2026-10-27': 'Easy run/walk · 3 km, only if fully recovered',
  '2026-10-29': 'Easy run · 4 km, only if pain-free',
  '2026-11-01': 'Easy run · 5 km, only if pain-free',
};
const runWeeks = [
  [4, 4, 6], [4, 5, 7], [5, 5, 8], [4, 4, 6],
  [5, 6, 9], [5, 6, 10], [5, 5, 8], [6, 6, 10],
  [6, 7, 11], [5, 5, 8], [6, 7, 10], [5, 6, 8],
];
function running(date) {
  const key = iso(date);
  if (key <= '2026-10-18') return taper[key] ?? null;
  if (key <= '2026-10-25') return null; // recovery week after the race
  if (key <= '2026-11-01') return recoveryRun[key] ?? null;
  const week = Math.min(Math.floor((Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()) - Date.UTC(2026, 10, 2)) / (7 * day)), runWeeks.length - 1);
  const distances = runWeeks[week];
  const index = { 2: 0, 4: 1, 0: 2 }[weekday(date)];
  if (index === undefined) return null;
  const kind = index === 2 ? 'Long easy run' : 'Easy run';
  return `${kind} · ${distances[index]} km, conversational pace; 5–10 min mobility`;
}

const preRaceWorkout = {
  '2026-10-02': 'Light upper body + core · 2 rounds; no failure',
  '2026-10-07': 'Light upper body + core · 2 rounds; no heavy legs',
  '2026-10-09': 'Light upper body + core · 2 rounds; no failure',
};
const upper = 'Upper body + core · 2–3 rounds: seated press 12–15, row 12–15, shoulder press 10–12, dead bug 10/side, bird dog 10/side';
const lower = 'Lower body + ankle · 2–3 rounds: glute bridge 15, hip thrust 12, hamstring curl 15, calf raise 10–15, supported balance 20–30 sec/side; pain-free only';
const recovery = 'Recovery + ankle mobility · gentle walk, ankle pumps/circles, calf stretch; no hard lifting';
function workout(date) {
  const key = iso(date);
  if (key <= '2026-10-18') return preRaceWorkout[key] ?? null;
  if (key <= '2026-10-25') return weekday(date) === 1 || weekday(date) === 3 || weekday(date) === 5 ? recovery : null;
  if (weekday(date) === 1 || weekday(date) === 5) return upper;
  if (weekday(date) === 3) return lower;
  return null;
}

const androidWeeks = [
  'Kotlin refresh: nullability, data classes, coroutines',
  'Compose state, recomposition and UI layout',
  'Navigation, ViewModel and state flows',
  'HTTP, serialization and error states',
  'Room/SQLDelight persistence and offline cache',
  'Repository pattern and dependency injection',
  'Testing: ViewModel, repository and UI basics',
  'Build a small feature end-to-end',
  'Accessibility, adaptive layouts and performance',
  'Background work and notifications',
  'Capstone polish, bugs and release build',
  'Capstone demo, README and portfolio',
  'Review weak spots and ship one small improvement',
];
const dsaWeeks = [
  'Arrays and strings', 'Hash maps and two pointers', 'Sliding window and prefix sums',
  'Stacks and queues', 'Linked lists', 'Binary search and sorting', 'Trees and traversals',
  'Heaps and priority queues', 'Graphs: BFS/DFS', 'Recursion and backtracking',
  'Dynamic programming basics', 'Mixed timed practice', 'Review missed patterns',
];
const sdeWeeks = [
  'Requirements, APIs and HTTP', 'Data modelling and SQL', 'Caching and pagination',
  'Authentication and authorization', 'Queues and background jobs', 'Reliability and retries',
  'Observability and testing', 'Design a notes/task service', 'Scale reads and writes',
  'Consistency and trade-offs', 'Security and privacy review', 'Mock design interview',
  'Document one project architecture',
];
function studyWeek(date) {
  return Math.min(Math.floor((Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()) - Date.UTC(2026, 8, 28)) / (7 * day)), 12);
}

for (let millis = +start; millis <= +end; millis += day) {
  const date = new Date(millis);
  const key = iso(date);
  const dow = weekday(date);
  const week = studyWeek(date);
  const run = running(date);
  if (run) add(key, 'Physical', 'Run', run, false, key <= '2026-10-18' ? 'Marathon Plan' : 'OND plan');
  const gym = workout(date);
  if (gym) add(key, 'Physical', 'Workout', gym, false, key <= '2026-10-18' ? 'Workout / OND plan' : 'OND plan');

  // 45–60 minute blocks. Friday/Sunday SDE is a shorter second block.
  const raceBreak = key >= '2026-10-17' && key <= '2026-10-19';
  if (!raceBreak && [1, 3, 5].includes(dow)) {
    const step = dow === 1 ? 'learn + notes' : dow === 3 ? 'implement one small example' : 'review + commit';
    add(key, 'Mental', 'Android', `${androidWeeks[week]} · ${step}, 45–60 min`);
  }
  if (!raceBreak && [2, 4, 6].includes(dow)) {
    const step = dow === 2 ? 'learn one pattern + 1 easy problem' : dow === 4 ? '2 focused problems' : 'review misses + 1 timed problem';
    add(key, 'Mental', 'DSA', `${dsaWeeks[week]} · ${step}, 45–60 min`);
  }
  if (!raceBreak && [5, 0].includes(dow)) {
    add(key, 'Mental', 'SDE', `${sdeWeeks[week]} · one diagram or short design note, 30–45 min`);
  }
  if (key >= '2026-11-03' && [2, 4].includes(dow)) {
    add(key, 'Physical', 'Mobility', '10–15 min gentle ankle, hip and calf mobility; pain-free range');
  }
  add(key, 'Positive', 'Wake Early', 'Wake at your chosen consistent time');
  add(key, 'Positive', 'Morning Routine', 'Water, daylight and 10 quiet minutes');
  add(key, 'Mental', 'Study', '20 min review or notes; technical sessions count toward this');
  add(key, 'Positive', 'Sleep on Time', 'Start wind-down and sleep at your chosen time');
  add(key, 'Avoidance', 'No Junk Food', 'Keep the day free of junk food');
  add(key, 'Avoidance', 'No Adult Content', 'Keep the day free of adult content');
  add(key, 'Avoidance', 'No Gooning', 'Keep the day free of gooning');
}

// Explicit skips override the app's recurring schedule on taper/recovery days.
const recurring = { Run: [2, 4, 0], Workout: [1, 3, 5], Android: [1, 3, 5], DSA: [2, 4, 6], SDE: [5, 0] };
for (let millis = +start; millis <= +end; millis += day) {
  const date = new Date(millis);
  const key = iso(date);
  for (const [habit, days] of Object.entries(recurring)) {
    if (!days.includes(weekday(date)) || rows.some(r => r.date === key && r.habit === habit)) continue;
    const area = habit === 'Run' || habit === 'Workout' ? 'Physical' : 'Mental';
    const reason = habit === 'Run' || habit === 'Workout' ? 'Rest / recovery' : 'Race week break';
    add(key, area, habit, reason, true);
  }
}

const habitOrder = ['Run', 'Workout', 'Mobility', 'Android', 'DSA', 'SDE', 'Study', 'Wake Early', 'Morning Routine', 'Sleep on Time', 'No Junk Food', 'No Adult Content', 'No Gooning'];
rows.sort((a, b) => a.date.localeCompare(b.date) || habitOrder.indexOf(a.habit) - habitOrder.indexOf(b.habit));

const workbook = Workbook.create();
const plan = workbook.worksheets.add('Plan');
plan.showGridLines = false;
plan.getRange('A1:H1').values = [['ID', 'Date', 'Area', 'Habit', 'Session', 'Done', 'Skip', 'Source']];
const data = rows.map(r => [r.id, new Date(`${r.date}T00:00:00Z`), r.area, r.habit, r.session, r.done, r.skip, r.source]);
plan.getRangeByIndexes(1, 0, data.length, 8).values = data;
plan.getRange(`B2:B${data.length + 1}`).setNumberFormat('yyyy-mm-dd');
plan.getRange(`B2:B${data.length + 1}`).format.horizontalAlignment = 'left';
plan.getRange(`B1:B${data.length + 1}`).format.borders = { right: { style: 'thin', color: '#8FA8BA' } };
plan.getRange(`A1:H${data.length + 1}`).format.font = { name: 'Arial', size: 11, color: '#17212B' };
plan.getRange('A1:H1').format = { fill: '#233A52', font: { name: 'Arial', size: 11, bold: true, color: '#FFFFFF' }, rowHeight: 28 };
plan.getRange(`A2:A${data.length + 1}`).format.columnWidth = 23;
plan.getRange(`B2:B${data.length + 1}`).format.columnWidth = 20;
plan.getRange(`C2:D${data.length + 1}`).format.columnWidth = 15;
plan.getRange(`E2:E${data.length + 1}`).format.columnWidth = 90;
plan.getRange(`F2:G${data.length + 1}`).format.columnWidth = 12;
plan.getRange(`H2:H${data.length + 1}`).format.columnWidth = 22;
plan.getRange(`E2:E${data.length + 1}`).format.wrapText = true;
plan.getRange(`A1:H${data.length + 1}`).format.rowHeight = 30;
plan.freezePanes.freezeRows(1);
plan.tables.add(`A1:H${data.length + 1}`, true, 'PlanTable');

const guide = workbook.worksheets.add('Guide');
guide.showGridLines = false;
guide.getRange('A1:B8').values = [
  ['OND plan 2026', 'Fitness, learning, routines and avoidance habits'],
  ['Date range', '1 Oct–31 Dec 2026'],
  ['Edit a session', 'Change Date, Habit or Session; keep the unique ID when moving the same session.'],
  ['Holiday or rest', 'Set Skip to TRUE. The app should not count it as due.'],
  ['Completion', 'Done is the shared completion value when authenticated sync is configured.'],
  ['Add a session', 'Append a unique ID, date, area, habit and session. Same habit can appear more than once per day.'],
  ['Running', '18 Oct half marathon follows your Marathon Plan. The next week is recovery.'],
  ['Safety', 'If ankle pain/swelling returns, stop and seek clinician guidance before progressing.'],
];
guide.getRange('A1:B8').format.font = { name: 'Arial', size: 11, color: '#17212B' };
guide.getRange('A1:A8').format.columnWidth = 24;
guide.getRange('B1:B8').format.columnWidth = 90;
guide.getRange('A1:B1').format = { fill: '#233A52', font: { name: 'Arial', size: 11, bold: true, color: '#FFFFFF' }, rowHeight: 28 };
guide.getRange('A2:A8').format.font = { name: 'Arial', size: 11, bold: true, color: '#17212B' };
guide.getRange('A1:B8').format.rowHeight = 30;

const outputDir = path.resolve('outputs/ond-2026');
await fs.mkdir(outputDir, { recursive: true });
const preview = await workbook.render({ sheetName: 'Plan', range: 'A1:H12', scale: 1.5, format: 'png' });
await fs.writeFile(path.join(outputDir, 'plan-preview.png'), new Uint8Array(await preview.arrayBuffer()));
const guidePreview = await workbook.render({ sheetName: 'Guide', range: 'A1:B8', scale: 1.5, format: 'png' });
await fs.writeFile(path.join(outputDir, 'guide-preview.png'), new Uint8Array(await guidePreview.arrayBuffer()));
const check = await workbook.inspect({ kind: 'table', range: 'Plan!A1:H12', include: 'values,formulas', tableMaxRows: 12, tableMaxCols: 8, maxChars: 4000 });
console.log(check.ndjson);
const errors = await workbook.inspect({ kind: 'match', searchTerm: '#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!', options: { useRegex: true, maxResults: 30 }, summary: 'final formula error scan' });
console.log(errors.ndjson);
const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(path.join(outputDir, 'OND Plan 2026.xlsx'));

const seedRows = rows.map(r => `    SeedSession("${r.id}", "${r.date}", "${r.area}", "${r.habit}", ${JSON.stringify(r.session)}, ${r.skip}),`).join('\n');
const kotlin = `package com.habitsheet.data\n\n/** Generated from the same OND plan as the import-ready workbook. */\ninternal data class SeedSession(val id: String, val date: String, val area: String, val habit: String, val detail: String, val skipped: Boolean)\n\ninternal object OndSeedData {\n    val sessions: List<SeedSession> = listOf(\n${seedRows}\n    )\n}\n`;
await fs.writeFile('composeApp/src/commonMain/kotlin/com/habitsheet/data/OndSeedData.kt', kotlin);
console.log(`Created ${rows.length} plan rows and seed sessions.`);
