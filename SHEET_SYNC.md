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

When a Sheet is linked, the in-app Plan screen is read-only and offers **Open Google Sheet**. This prevents local edits that would be overwritten by the Sheet. The app writes pending local `Done` changes to their matching Sheet IDs, then pulls the latest plan and other completion values. Daily habits without a `Plan` row are given one row per day inside the sync window; habits outside it stay local-only until rows are added. On a brand-new device's first connection, existing Sheet values win. If two devices edit the same check-off before either syncs, the last upload can win; this minimal client does not offer conflict history.

## Google Cloud setup

1. In your Google Cloud project, enable the **Google Sheets API** and configure the OAuth consent screen. While it is in Testing, add the Google account used on your devices as a test user.
2. Android OAuth client: register package `com.habitsheet.app` and the SHA-1 of the signing certificate of the installed build (debug and release certificates each need registration). Android Google Authorization resolves the client by package and certificate, so the client ID is not passed to its SDK at runtime.
3. iOS OAuth client: create one for the app's bundle ID and put its client ID and reversed URL scheme in `Info.plist`.
4. Client IDs and signing details: `<configured via build config — see P0-C>`.
5. Ensure the Google account can **edit** the linked spreadsheet. The app requests the `spreadsheets` read/write scope. A publicly shared file is not made private by OAuth, so restrict sharing if the plan should be private.

The Sheets scope is sensitive. Broad public distribution may require Google's OAuth verification. No client secret, service-account key, access token, or refresh token is stored in the spreadsheet or app database; platform Google libraries manage account sessions. Live sync still requires on-device sign-in to verify.

## Troubleshooting

- **Account picker appears, then nothing happens (Android):** the Google OAuth Android client does not match the build's package and signing certificate. `adb logcat` shows `UNREGISTERED_ON_API_CONSOLE`. Get the SHA-1 with `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` (each machine's debug keystore differs) and add it to the Android OAuth client in Google Cloud Console; allow a few minutes to propagate.
