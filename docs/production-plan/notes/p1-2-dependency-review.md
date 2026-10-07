# P1-2 step 11: dependency review (2026-10-05)

Checked against Maven Central / Google Maven / the Gradle plugin portal on 2026-10-05. **Nothing was upgraded in
P1-2** (the plan asks to report, not apply); on 2026-10-07 Dependabot's PRs for Gradle (#6), kotlinx-serialization (#5) and SQLDelight (#4) were tested and merged (see "Result of the Dependabot PRs" at the end). Each remaining upgrade below is its own change with the full `./gradlew check`, the
iOS compile and the screenshot comparison from step 7 (`scripts/screenshots/`).

| Dependency | In use | Latest stable | Recommendation |
|---|---|---|---|
| Kotlin (+ Compose compiler, serialization plugin) | 2.3.20 | 2.4.20 | Upgrade together with Compose Multiplatform; check the iOS klib and detekt's Kotlin pin. |
| Compose Multiplatform | 1.11.1 | 1.12.1 | Upgrade with Kotlin. Re-check `BackHandler` (deprecated in favour of `NavigationEventHandler`) and previews. |
| CMP material3 | 1.9.0 | 1.9.0 | Current. |
| Android Gradle Plugin | 9.3.3 | 9.4.1 | Minor; first do the `androidApp` module split (below), which AGP 9 asks for. |
| SQLDelight | 2.3.2 | 2.4.0 | Minor; run `verifyCommonMainHabitsDatabaseMigration` and `SchemaMigrationTest`. |
| Ktor | 3.3.3 | 3.6.0 | Minor; `SheetSyncErrorsTest` and the fake-server tests cover timeouts and retries. |
| kotlinx-coroutines | 1.11.0 | 1.11.0 | Current. |
| kotlinx-serialization | 1.8.0 | 1.11.0 | Upgrade; the frozen `BackupFixtures` (v1–v4) must still parse byte-for-byte. |
| kotlinx-datetime | 0.8.0 | 0.8.0 | Current. |
| JetBrains lifecycle (KMP) | 2.9.6 | 2.11.0 | Upgrade with Compose Multiplatform (they are released together). |
| androidx.activity:activity-compose | 1.10.1 | 1.13.0 | Upgrade with the above; 1.12+ routes back through `navigationevent`, which would allow the non-deprecated `NavigationEventHandler`. |
| play-services-auth | 21.5.1 | 22.0.0 | Major: read the release notes; sign-in is only testable on a device (plan B2). |
| Gradle | 9.8.0 | 9.8.0 | **Done** (PR #6). |
| Spotless / ktlint / detekt | 8.10.3 / 1.8.0 / 1.23.8 | same | detekt 2.0 is still alpha (2.0.0-alpha.6); when it is stable it removes the Kotlin 2.0.21 pin. |

## Confirmed in this review

- Ktor: `HttpTimeout` is installed (`createSheetsHttpClient`: connect 15 s, socket 30 s, request 45 s); retries are the
  app's own bounded backoff with jitter and `Retry-After` (`RetryPolicy`), not Ktor's retry plugin, so they are tested
  without network (`SheetSyncErrorsTest`).
- `kotlinx-collections-immutable` is **not** added: no profiling shows recomposition problems (`MonthUiState` is
  `@Immutable`, leaves get plain values).
- No DI, ORM or navigation library added (ADRs 0003, 0004).
- Libraries added in P1-2, all parts of Compose Multiplatform itself (same version): `ui-backhandler`,
  `ui-tooling-preview` (`ui-tooling` for debug builds only).

## Known duplicates in the dependency graph

The shared-metadata compilation reports "KLIB resolver: the same unique_name … found in more than one library" for
lifecycle, savedstate, compose runtime and collection: Google's AndroidX KMP artifacts (pulled in by newer androidx
libraries) and JetBrains' re-published ones are both on the classpath. Harmless today (the platform compilations,
which build the app, are warning-free and compiled with `allWarningsAsErrors`); it disappears when Compose
Multiplatform and lifecycle are upgraded together to versions that depend on the same AndroidX artifacts.

## Structural item carried forward

The Kotlin Gradle plugin warns that `com.android.application` inside the KMP module is deprecated (AGP 9). The fix is
an `androidApp` module holding `MainActivity`, the manifest, resources and the widget, with `composeApp` becoming a KMP
library. That changes paths that tests rely on (`DestructiveCallSitesTest`, `UiStructureTest`, `NavigationWiringTest`,
`SchemaMigrationTest` locate sources and `.db` snapshots relative to the module) and the Xcode project's Gradle call;
the plan lists multi-module as out of scope unless chosen, so it is left as a separate task.

## Result of the Dependabot PRs (tested 2026-10-07)

Each PR was merged into current `master` in a throwaway worktree and run through `./gradlew check`, `assembleDebug` and the iOS
compile; the combination was also built (debug and release/R8) and exercised on an emulator.

| PR | Change | Result |
|---|---|---|
| #6 | Gradle 9.5.0 → 9.8.0 | passed, merged (wrapper jar checked against Gradle's published SHA-256) |
| #5 | kotlinx-serialization 1.8.0 → 1.11.0 | passed, merged (frozen backup fixtures still parse) |
| #4 | SQLDelight 2.3.2 → 2.4.0 | passed, merged after a one-line conflict with #5 was resolved |
| #3 | Ktor 3.3.3 → 3.6.0 | **does not build**: Ktor 3.6.0 brings OkHttp 5.5.0, which needs compileSdk 37 (app: 35). Ktor 3.5.2 (OkHttp 5.3.2) and 3.4.3 pass everything. |
| #2 | Kotlin 2.4.20, Compose 1.12.1, lifecycle 2.11.0, AGP 9.4.1 | **does not build**: AGP 9.4.1 needs Gradle ≥ 9.6 (now satisfied) and Compose 1.12.1 needs compileSdk 37 (SDK 37 is not installed; moving `compileSdk` is its own decision). Not tested beyond that failure, so the Kotlin 2.4 step itself is unverified. |

Next dependency work, in order: (1) Ktor 3.5.2 (a version edit, tested); (2) decide on compileSdk 37 (install the platform, raise
`compileSdk` only, keep `targetSdk` at 35, run `check`, lint and the screenshot comparison), then retry #2 and #3 (Ktor 3.6.0);
(3) when Kotlin moves, re-check detekt's pinned Kotlin and the deprecated `BackHandler`. Real Google Sheets sync over the network
is covered only by mock-server tests: try Connect & sync on a device after any Ktor change.
