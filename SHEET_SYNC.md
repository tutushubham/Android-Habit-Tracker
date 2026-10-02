# Google Sheet plan sync

The app uses one private Google spreadsheet as the source of truth for dated sessions. On each device, open **Settings → Plan sync**, paste the same spreadsheet URL, choose **Save link**, then **Connect & sync** with the Google account that can edit it. The app refreshes on launch/foreground and uploads check-offs for `Plan` rows when online. It keeps the last downloaded plan and your check-offs locally while offline.

The linked workout workbook currently contains `Food`, `Workout`, and `Marathon Plan`. The first successful authorized sync creates a separate `Plan` tab and uploads the app's seeded October–December 2026 plan, including study, routines, and avoidance habits. The three existing tabs are not modified. If `Plan` already exists and has a valid table, the Sheet wins over the app's starter plan; habits that exist only on the device are appended for OND. A blank `Plan` tab is initialized from the local starter plan.

## Plan tab

One row per planned session, with these six headers in row 1:

| ID | Date | Habit | Session | Done | Skip |
| --- | --- | --- | --- | --- | --- |
| `run-2026-10-01` | `2026-10-01` | `Run` | `Easy 6 km` | `FALSE` | `FALSE` |

- `ID` must be unique and stay unchanged when a session is edited or moved. Two rows can have the same `Habit` and `Date`—for example, a run and a second training session. Each has its own check-off. Use a real Google Sheets date or ISO `YYYY-MM-DD` text.
- `Session` is the morning instruction. Edit it and `Date` in the Sheet to reschedule; set `Skip` to `TRUE` for a holiday/rest session. `Done` is the evening check-off, written by the app. Google Sheets checkboxes are supported.
- Add a new row for an extra session, with a new unique ID. Deleting a row removes that planned session on the next refresh but retains its historical completion record locally.
- The OND Excel workbook also has `Area` and `Source` columns: `ID,Date,Area,Habit,Session,Done,Skip,Source`. The app accepts this layout when used as the `Plan` tab and writes check-offs to its `Done` column. It does not import `.xlsx` files directly.
- The app leaves columns outside the supported table and all other tabs alone. Do not reorder the headers.

When a Sheet is linked, the in-app Plan screen is read-only and offers **Open Google Sheet**. This prevents local edits that would be overwritten by the Sheet. The app writes pending local `Done` changes to their matching Sheet IDs, then pulls the latest plan and other completion values. OND daily routines are given a row for each day; outside OND, daily habits without a `Plan` row remain local-only until rows are added. On a brand-new device's first connection, existing Sheet values win. If two devices edit the same check-off before either syncs, the last upload can win; this minimal client does not offer conflict history.

## Google Cloud setup

1. In the Google Cloud project for OAuth project `412589914724`, enable the **Google Sheets API** and configure the OAuth consent screen. While it is in Testing, add the Google account used on both devices as a test user.
2. Android OAuth client ID: `412589914724-c3ocjq68kjc33lva9bgb3t5pfl4j69bm.apps.googleusercontent.com`. It must register package `com.habitsheet.app` and the certificate SHA-1 of the installed build. The current debug certificate SHA-1 is `6E:7F:68:8C:53:6C:B2:E5:52:B6:BE:92:B6:48:90:AE:DD:0C:84:46`; release signing needs its own registration. Android Google Authorization resolves the client by package and certificate, so the client ID is not passed to its SDK at runtime.
3. iOS OAuth client ID: `412589914724-409r1ph8o60ue3k6huaj26ob08po2ot8.apps.googleusercontent.com`, for bundle ID `com.habitsheet.app`. It is configured in `Info.plist` with its reversed URL scheme.
4. Ensure the Google account can **edit** the linked spreadsheet. The app requests the `spreadsheets` read/write scope. The linked workbook was publicly viewable when inspected; restrict its sharing if you want the plan to be private. OAuth does not make a publicly shared file private.

The Sheets scope is sensitive. Broad public distribution may require Google's OAuth verification. No client secret, service-account key, access token, or refresh token is stored in the workbook or app database; platform Google libraries manage account sessions. Google API access and a two-device live sync still require on-device sign-in to verify.
