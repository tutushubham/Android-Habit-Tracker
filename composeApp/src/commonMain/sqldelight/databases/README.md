# Schema snapshots

`N.db` is the SQLite schema of database version N, as released. SQLDelight compiles the migrations against these
and `verifyCommonMainHabitsDatabaseMigration` (part of `./gradlew check`) opens each one, applies the `.sqm` files
up to the current schema and fails if the result differs from a fresh install.

| File | Meaning | Origin |
|---|---|---|
| `1.db` | first release, before any `.sqm` | DDL of `Habits.sq` at commit `5161dfa`, executed with SQLite |
| `2.db` | + `weeklyPlanEntity`, `dayPlanEntity` (after `1.sqm`) | derived from `1.db` by applying `1.sqm` (no commit ever had this schema) |
| `3.db` | + `textSettingsEntity` (after `2.sqm`) | derived from `2.db` by applying `2.sqm` |
| `4.db` | + habit `kind`/`dated_only`, ids on plans and completions (after `3.sqm`) | DDL of `Habits.sq` at commit `53d074e`; equal to `3.db` + `3.sqm` apart from how SQLite spells the added columns |
| `5.db` | + `pending_upload` (after `4.sqm`) | `generateCommonMainHabitsDatabaseSchema` |

## When you change the schema

1. Add `5.sqm` (migrates version 5 to 6). Never edit a shipped `.sqm` (one exception, recorded in `3.sqm`: a comment and
   a `DROP INDEX IF EXISTS` that does not change the resulting database but lets SQLDelight compile it against `1.db`).
2. Run `./gradlew :composeApp:generateCommonMainHabitsDatabaseSchema` and commit the new `6.db`.
3. Add the new version to `SchemaMigrationTest` (`fill(...)` must know how that version stored rows) and bump its
   `latest` assertion. `./gradlew :composeApp:check` must pass.
4. Never edit or delete an existing `N.db`: they stand for databases that exist on people's phones.
