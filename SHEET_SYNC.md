# Google Sheet plan sync

The app uses one private Google spreadsheet as the source of truth for dated sessions. On each device, open **Settings → Plan sync**, paste the same spreadsheet URL, choose **Save link**, then **Connect & sync** with the Google account that can edit it. The app refreshes on launch/foreground and uploads check-offs for `Plan` rows when online. It keeps the last downloaded plan and your check-offs locally while offline.

The first successful authorized sync creates a separate `Plan` tab and uploads the app's planned sessions for a rolling window (from 31 days before the start of the current month to 180 days ahead). Other tabs in the spreadsheet are not modified. If `Plan` already exists and has a valid table, the Sheet wins over local plans; habits that exist only on the device are appended. A blank `Plan` tab is initialized from the local plan.

## Plan tab

One row per planned session, with these six headers in row 1:

| ID | Date | Habit | Session | Done | Skip |
| --- | --- | --- | --- | --- | --- |
| `run-2026-10-01` | `2026-10-01` | `Run` | `Easy 6 km` | `FALSE` | `FALSE` |

- `ID` must be unique and stay unchanged when a session is edited or moved. Two rows can have the same `Habit` and `Date`—for example, a run and a second training session. Each has its own check-off. Use a real Google Sheets date or ISO `YYYY-MM-DD` text.
- `Session` is the morning instruction. Edit it and `Date` in the Sheet to reschedule; set `Skip` to `TRUE` for a holiday/rest session. `Done` is the evening check-off, written by the app. Google Sheets checkboxes are supported.
- Add a new row for an extra session, with a new unique ID. Deleting a row removes that planned session on the next refresh but retains its historical completion record locally.
- An optional eight-column layout adds `Area` and `Source` columns: `ID,Date,Area,Habit,Session,Done,Skip,Source`. The app accepts this layout when used as the `Plan` tab and writes check-offs to its `Done` column. `Area` is matched to an existing category by name; otherwise the habit is uncategorized. The app does not import `.xlsx` files directly; upload one to Google Drive and open it as a Google Sheet first.
- The app leaves columns outside the supported table and all other tabs alone. Do not reorder the headers.

When a Sheet is linked, the in-app Plan screen is read-only and offers **Open Google Sheet**. This prevents local edits that would be overwritten by the Sheet. The app writes pending local `Done` changes to their matching Sheet IDs, then pulls the latest plan and other completion values (see the conflict rule below). Daily habits without a `Plan` row are given one row per day inside the sync window; habits outside it stay local-only until rows are added. On a brand-new device's first connection, existing Sheet values win.

## When syncs run

Check-offs are saved locally at once. A burst of taps triggers **one** sync, about 2.5 seconds after the last tap (the app waits for a quiet moment). Taps made while a sync is running produce exactly one follow-up sync. The app also syncs when it comes to the foreground, and **Connect & sync / Sync now** syncs immediately.

## Conflict rule (check-offs)

Every check-off made on this device is marked *pending upload* in the local database until its value has been written to the sheet. On each sync, for every sheet row:

1. **Pending local check** whose value differs from the sheet: the local value is written to the sheet, then the pending mark is cleared. If the sheet already holds the same value, the mark is simply cleared.
2. **No pending mark:** the sheet's value is applied locally.
3. If you tap a check *while* a sync is running, your newer tap stays pending and is uploaded by the next sync; the sheet value read earlier never overwrites it.

No device clocks are compared. If two devices change the same check before either syncs, whoever syncs last wins; there is no conflict history. Linking a different sheet discards pending marks (the new sheet is authoritative). Restoring a backup also starts with nothing pending. Upload failures leave the mark in place so the next sync retries.

## Which habit does a row belong to? (identity)

The `ID` column identifies a *session*. The habit it belongs to is found like this:

1. If the `ID` is already known on this device, the row belongs to **that session's habit**, whatever the `Habit` text says.
2. Otherwise the habit is matched by name (ignoring case and spaces).
3. Otherwise a new habit is created from the row (dated sessions only, with no weekly plan).

What that means in practice:

- **Rename a habit in the sheet** by changing the `Habit` text on *all* of its rows to the same new name: the app renames its habit and keeps its history. If you change only some rows, those sessions *move* to the habit with the new name (created if it does not exist).
- **Two habits with the same name on the device** no longer stop syncing. Rows with a known `ID` sync normally; a *new* row whose name matches both is skipped with a note ("1 row skipped: ... rename one"). Rename one habit and the row is picked up on the next sync.
- **Names are controlled by the sheet.** Renaming a sheet-managed habit inside the app is undone on the next sync, because the app does not write names back. Rename it in the sheet instead.
- A typo in the `Habit` cell of a *new* row creates a new habit; fix the text and delete the stray habit in the app.
- If the `Plan` tab has no sessions at all but an earlier sync had imported some, the app keeps its local sessions and says so instead of deleting everything. Restore the rows (or **Disconnect**) to continue.

## What the app will and will not write

- It only ever writes to a tab named exactly `Plan`: it creates the tab if it does not exist, rewrites it only when it is the first upload to a brand-new/blank tab, and otherwise only appends rows for habits the sheet does not know and updates `Done` cells of rows it recognises.
- If a tab called `Plan` exists but its header row is not the six (or eight) documented columns in the documented order, or a row is malformed (bad date, missing ID/Habit/Session, duplicate ID, `Done`/`Skip` not a checkbox or TRUE/FALSE), the app **does not modify it**. It shows what is wrong (including the row number) and waits for you to fix the sheet.
- All other tabs (Food, Workout, Marathon Plan, and so on) are never read for data or written. A different tab whose name only differs by capitalisation (for example `plan`) makes Google reject creating `Plan`; the sync then fails without touching either tab.
- Local changes are applied to the device database in one transaction, so a failure part-way never leaves a half-synced plan.

## Messages, errors and offline behaviour

| Situation | What you see | What the app does |
| --- | --- | --- |
| No connection, DNS failure or timeouts | "Will sync when online." (not shown as an error) | Retries a few times with backoff; your check-offs stay marked pending. While the app is open it tries again by itself after 30 s, then 60 s, doubling up to 5 min, and also on the next check-off, app foreground or **Connect & sync** |
| Google session ended, access revoked, sign-in cancelled | "Google sign-in needed. Tap Connect & sync to sign in again." | No data is changed; sign in again |
| 403, no edit access | "No access to this sheet. Use a Google account that can edit it." | Not retried |
| 404 | "Spreadsheet not found. Check the link and the Google account." | Not retried |
| 429 / quota | "Google is limiting requests. Sync will retry shortly." | Waits as long as Google asks (`Retry-After`, up to 30 s per wait) between up to 4 attempts, otherwise stops |
| `Plan` tab problem | "Plan tab, row N: ... Fix the sheet, then sync again." | Nothing is written |
| Anything else (for example Google 5xx after retries) | "Sync failed. Try again in a moment." | Local data unchanged |

Network requests have connect (15 s), socket (30 s) and total (45 s) timeouts. Retries use exponential backoff with jitter. Requests that could be applied twice (appending rows, creating the tab) are not retried after an ambiguous failure, so rows are never duplicated by a retry. Sign-in requests are queued one at a time and give up after three minutes if the Google sign-in screen is abandoned.

## Disconnect

**Settings → Plan sync → Disconnect** unlinks the spreadsheet, forgets the last-sync state, sync keys and pending marks, and clears the status line. Habits, plans and check-offs already on the device are **kept**; the in-app Plan screen becomes editable again. To stop the app's Google access completely, remove it in your Google Account under **Security → Third-party access**. Linking the same or another sheet later starts a fresh sync where that sheet is authoritative.

## Google Cloud setup

1. In your Google Cloud project, enable the **Google Sheets API** and configure the OAuth consent screen. While it is in Testing, add the Google account used on your devices as a test user.
2. Android OAuth client: register package `com.habitsheet.app` and the SHA-1 of the signing certificate of the installed build (debug and release certificates each need registration). Android Google Authorization resolves the client by package and certificate, so the client ID is not passed to its SDK at runtime.
3. iOS OAuth client: create one for the app's bundle ID and put its client ID and reversed URL scheme in `Info.plist`.
4. Client IDs and signing details: `<configured via build config — see P0-C>`.
5. Ensure the Google account can **edit** the linked spreadsheet. The app requests the `spreadsheets` read/write scope. A publicly shared file is not made private by OAuth, so restrict sharing if the plan should be private.

The Sheets scope is sensitive. Broad public distribution may require Google's OAuth verification. No client secret, service-account key, access token, or refresh token is stored in the spreadsheet or app database; platform Google libraries manage account sessions. Live sync still requires on-device sign-in to verify.

## Troubleshooting

- **Account picker appears, then nothing happens (Android):** the Google OAuth Android client does not match the build's package and signing certificate. `adb logcat` shows `UNREGISTERED_ON_API_CONSOLE`. Get the SHA-1 with `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` (each machine's debug keystore differs) and add it to the Android OAuth client in Google Cloud Console; allow a few minutes to propagate.
