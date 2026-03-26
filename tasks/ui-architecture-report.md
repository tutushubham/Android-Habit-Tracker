# UI + Architecture Report

> Generated: March 26, 2026  
> Package: `com.tutushubham.pokidex`  
> Stack: Kotlin, Jetpack Compose (Material 3), Room, single-Activity, MVI

---

## 1. Screens

The app is **Compose-only** — no Fragments, no XML layouts. One `ComponentActivity`.

### Screen Destinations

| Screen | File | Purpose |
|--------|------|---------|
| **TodayScreen** | `feature_today/TodayScreen.kt` | Main hub — today's sessions, goal progress, focus header, overload banner, focus-override bottom sheet |
| **WelcomeScreen** | `feature_onboarding/WelcomeScreen.kt` | Onboarding landing with branding |
| **GoalsScreen** | `feature_onboarding/GoalsScreen.kt` | Add/edit goals with deadlines and targets |
| **DayStructureScreen** | `feature_onboarding/DayStructureScreen.kt` | Configure time blocks for the day |
| **BlockAssignmentScreen** | `feature_onboarding/BlockAssignmentScreen.kt` | Map domains to day blocks |
| **FocusSetupScreen** | `feature_onboarding/FocusSetupScreen.kt` | Per-domain focus names (chips + add) |
| **StrategySetupScreen** | `feature_onboarding/StrategySetupScreen.kt` | Per-domain rotation strategy selection |
| **PreviewScreen** | `feature_onboarding/PreviewScreen.kt` | Sample day preview from engine output |
| **DomainSelectionScreen** | `feature_onboarding/DomainSelectionScreen.kt` | Domain picker — **unused**, not wired in nav graph |
| **FocusOverviewScreen** | `feature_focus/FocusOverviewScreen.kt` | Domain summary: focus list + strategy |
| **FocusListScreen** | `feature_focus/FocusListScreen.kt` | Scrollable focus list with add |
| **FocusStrategyScreen** | `feature_focus/FocusStrategyScreen.kt` | Choose strategy per domain |
| **FocusConfirmScreen** | `feature_focus/FocusConfirmScreen.kt` | Day-by-day preview + confirm |

### Navigation Hosts

| Host | File | Purpose |
|------|------|---------|
| `OnboardingHost` | `feature_onboarding/OnboardingNavGraph.kt` | Onboarding flow NavHost |
| `MainAppHost` | `MainActivity.kt` (private) | Main app NavHost (`today` + `focus/{domain}`) |
| `FocusHost` / `FocusHostWithViewModel` | `feature_focus/FocusNavGraph.kt` | Focus settings inner NavHost |

**Total: 13 screen composables, 3 NavHosts, 1 Activity, 0 Fragments**

---

## 2. Navigation

### Route Definitions

```
Root NavHost (MainActivity)
├── "onboarding"  →  OnboardingHost
│   ├── onboarding/welcome
│   ├── onboarding/goals
│   ├── onboarding/day_structure
│   ├── onboarding/block_assignment
│   ├── onboarding/focuses
│   ├── onboarding/strategy
│   └── onboarding/preview  →  finish → navigate("main")
│
└── "main"  →  MainAppHost
    ├── "today"  (startDestination)
    │   ├── onOpenFocusSettings → "focus/{domain}"
    │   └── onNavigateToOnboarding → root "onboarding"
    │
    └── "focus/{domain}"  →  FocusHostWithViewModel
        ├── focus/overview
        ├── focus/list
        ├── focus/strategy
        └── focus/confirm  →  exit → popBackStack → "today"
```

### Entry Points

- **Single entry:** `MainActivity` — `MAIN` / `LAUNCHER` intent filter only
- **No deep links** — no `navDeepLink`, no custom schemes, no `VIEW` / `BROWSABLE` filters
- **Root decision:** `AppStateRepository.isOnboardingCompleted()` → route to `main` or `onboarding`

### Navigation Patterns

- Forward navigation: `navController.navigate(route)`
- Back: `navController.popBackStack()`
- Root transitions: `popUpTo` with `inclusive = true` to clear back stack
- Focus flow: VM effects (`NavigateToFocusList`, `Exit`, etc.) drive navigation via `LaunchedEffect`

---

## 3. UI Architecture

### Pattern: MVI (Model-View-Intent)

Each feature follows the same contract pattern:

```
State     →  data class (single source of truth)
Event     →  sealed class (user actions)
Effect    →  sealed class (one-shot side effects: navigation, toast, timer)
```

### ViewModels

| ViewModel | File | Responsibilities |
|-----------|------|------------------|
| **TodayViewModel** | `feature_today/TodayViewModel.kt` | Load today plan, session lifecycle (start/skip/complete), timer tracking, focus override, empty state derivation |
| **OnboardingViewModel** | `feature_onboarding/OnboardingViewModel.kt` | In-memory wizard state, preview generation via `TodayEngine`, persist via `OnboardingRepository` |
| **FocusViewModel** | `feature_focus/FocusViewModel.kt` | Load focuses + config for one domain, edit strategy/rotation/weights, preview generation, persist config |

### State Models

| Contract | State | Key Fields |
|----------|-------|------------|
| `TodayContract` | `TodayState` | `isLoading`, `sessions`, `progressList`, `activeFocusByDomain`, `activeSessionId`, `elapsedMinutes`, `maxOverloadSeverity`, `overloadedIntentIds`, `emptyState`, `error` |
| `OnboardingContract` | `State` | `goals`, `dayBlocks`, `blockDomainMap`, `focusesByDomain`, `strategiesByDomain`, `rotationOrders`, `weights`, `previewLines`, `isLoading`, `error` |
| `FocusContract` | `FocusState` | `domain`, `focuses`, `strategy`, `manualFocusId`, `rotationOrder`, `weights`, `preview`, `isLoading`, `error` |

### State Flow Pattern

```
ViewModel
  _state: MutableStateFlow<State>     →  state: StateFlow<State>
  _effect: Channel<Effect>            →  effect: Flow<Effect>

Composable
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect { viewModel.effect.collect { handle(it) } }
  onClick = { viewModel.onEvent(Event.Something) }
```

- **No LiveData** — pure `StateFlow` + `Channel`
- **Local mutable state** (`mutableStateOf`) used only for transient UI (text fields, dialogs)
- ViewModels created via manual `Factory` classes (no Hilt/Koin)

---

## 4. Components

### Reusable Composables

| Component | Location | What it renders |
|-----------|----------|-----------------|
| `SessionCard` | `TodayScreen.kt` | Card: domain, minutes, Start/Complete/Skip |
| `ProgressCard` | `TodayScreen.kt` | Progress bar, status chip (Critical/Behind/On track), deficit text |
| `TodayFocusHeader` | `TodayScreen.kt` | Per-domain focus name with "Change" button |
| `FocusOverrideSheet` | `TodayScreen.kt` | Bottom sheet: focus alternatives list |
| `EmptyCard` | `TodayScreen.kt` | Centered card for empty/onboarding states |
| `LoadingView` | `TodayScreen.kt` | Centered `CircularProgressIndicator` |
| `GoalCard` | `GoalsScreen.kt` | Form card: title, domain chips, deadline picker, target fields |
| `DayBlockItem` | `DayStructureScreen.kt` | Switch + minute preset chips |
| `FocusChip` / `AddFocusInput` | `FocusSetupScreen.kt` | Chip with remove + text field to add |
| `StrategyOption` (x2) | `StrategySetupScreen.kt` / `FocusStrategyScreen.kt` | Strategy selector (different implementations) |
| `RotationEditor` / `WeightedEditor` | `StrategySetupScreen.kt` | Rotation order list / weight buttons |
| `PreviewItem` | `PreviewScreen.kt` | Single preview line in Surface |
| `DomainItem` | `DomainSelectionScreen.kt` | Checkbox + domain info (unused) |
| `ColumnWithBackgroundImage` | `util/ui/Composables.kt` | Background image layout (unused) |

### UI Patterns

| Pattern | Usage |
|---------|-------|
| **LazyColumn** | Sessions list, goals, previews, focus list, strategy setup |
| **ModalBottomSheet** | Focus override picker (TodayScreen) |
| **DatePickerDialog** | Goal deadline (GoalsScreen) |
| **FilterChip** | Domain selection, minute presets |
| **Card / Surface** | Session cards, progress cards, goal cards |
| **LinearProgressIndicator** | Goal progress bars |
| **Switch** | Day block toggle |
| **RadioButton** | Focus strategy selection |

### What's Missing

- **No Scaffold** — no top app bar, no bottom nav
- **No shared component package** — components are colocated with features
- **No design system module** — just `ui/theme/`
- **Duplicate `StrategyOption`** composable in two features (not shared)

---

## 5. Current UI Gaps

### Missing Screens

| Screen | Impact |
|--------|--------|
| **Settings** | No way to edit day structure, blocks, or general preferences post-onboarding |
| **Session Detail / Timer** | `onNavigateToSession` is a no-op lambda — no dedicated session/timer screen |
| **Goal Management** | `onNavigateToAddGoal` is empty — no way to add/edit goals post-onboarding |
| **Analytics / Stats** | Behavior data (fatigue, momentum, learned estimates) exists but has zero UI |
| **History** | No past sessions view, no streak/calendar view |
| **Profile / Account** | Welcome has a sign-in stub; no profile screen exists |
| **Capture** | `CaptureRepository` + DB table exist but repo is never constructed |

### Weak UX Areas

| Area | Issue |
|------|-------|
| **TodayScreen overload** | Single screen handles: sessions, progress, focus header, overload banner, override sheet, 4 empty states. ~700+ lines |
| **No top/bottom navigation** | No Scaffold, no app bar, no bottom nav — feels like a prototype |
| **Error display** | `TodayState.error` is set but **never read by the UI** — errors are silently swallowed |
| **Dark mode disabled** | `isSystemInDarkTheme()` is commented out; app is always light |
| **Session interaction** | No timer UI, no detail view — "Start" just marks in-progress, elapsed shown inline |
| **Onboarding re-entry** | Can restart full onboarding from Today, but no way to edit individual settings |
| **No confirmation dialogs** | Skip/complete session are instant — no undo, no confirmation |

### Hardcoded / Placeholder UI

| Item | Location |
|------|----------|
| Empty lambda: `onNavigateToSession` | `MainActivity.kt` |
| Empty lambda: `onNavigateToAddGoal` | `MainActivity.kt` |
| Empty lambda: `onNavigateToStructureSettings` | `MainActivity.kt` |
| Unused composable: `DomainSelectionScreen` | Not in nav graph |
| Unused composable: `EmptyStateView` | Only in preview |
| Unused composable: `ColumnWithBackgroundImage` | No callers |
| Sign-in stub in WelcomeScreen | Non-functional |
| Package name: `pokidex` | Likely leftover from template |

---

## 6. Data → UI Mapping

### Exposed in UI

| Engine / Data Output | Where Shown | Fields Used |
|----------------------|-------------|-------------|
| `TodayPlan.sessions` | `SessionCard` | domain, plannedMinutes, status |
| `IntentProgress` | `ProgressCard` | title, progress ratio, completed/target, daysLeft, deficit, severity |
| Overload severity | Banner in `TodayScreen` | `maxOverloadSeverity` only |
| Resolved `Focus` | `TodayFocusHeader` | domain + focus.name |
| `DomainFocusConfig` | `FocusOverviewScreen` | strategy label |
| Focus preview (N days) | `FocusConfirmScreen` | focus names per day |
| Onboarding state | All onboarding screens | goals, blocks, focuses, strategies |
| Preview lines | `PreviewScreen` | engine-generated strings |

### NOT Exposed (Important)

| Data Available | UI Status | Why It Matters |
|----------------|-----------|----------------|
| **`IntentBehaviorProfile`** (fatigue, momentum, learned estimates) | **Hidden** | Core adaptive intelligence — user can't see how the engine "sees" them |
| **`FatigueSignal`** (score, trend, confidence) | **Hidden** | Could show "You seem fatigued in FITNESS" |
| **`MomentumSignal`** (score, trend, streak) | **Hidden** | Could show streaks, momentum indicators |
| **`LearnedEstimate`** (effective minutes, confidence) | **Hidden** | Engine adjusts session length but user doesn't know why |
| **`DomainBehaviorProfile`** (preferred duration) | **Hidden** | User can't see learned preferences |
| **`OverloadDetail`** (per-intent needed/capacity/severity) | **Partially** | Only max severity shown; per-goal breakdown hidden |
| **`UserIntentStats`** (persisted learning) | **Hidden** | No visibility into what the system "learned" |
| **Session history** (actual minutes, skip reasons, timestamps) | **Hidden** | Fields exist on Session but no history view |
| **`Capture` / `Artifact` / `Signal`** | **Hidden** | Tables exist, no repo wiring, no UI |
| **`TodayState.error`** | **Set but never read** | Errors silently lost |
| **`Session.actualMinutes`**, `skipReason`, `startedAt`, `endedAt` | **Hidden** | Even completed sessions don't show actual vs planned |
| Several `SessionRepository` aggregate queries | **Dead code** | `getTotalActualMinutes`, `getSkippedSessionCount` — no callers |

---

## 7. Theming

### Theme Setup

- **`PokidexTheme`** wraps `MaterialTheme` with color scheme + typography
- **Dynamic color** on API 31+ (wallpaper-based) when `dynamicColor = true`
- Static fallback: custom purple/pink palette

### Color Tokens (`Color.kt`)

| Token | Hex | Role |
|-------|-----|------|
| `Purple80` | `#D0BCFF` | Dark scheme primary |
| `PurpleGrey80` | `#CCC2DC` | Dark scheme secondary |
| `Pink80` | `#EFB8C8` | Dark scheme tertiary |
| `Purple40` | `#6650A4` | Light scheme primary |
| `PurpleGrey40` | `#625B71` | Light scheme secondary |
| `Pink40` | `#7D5260` | Light scheme tertiary |

Only `primary`, `secondary`, `tertiary` are overridden — all other roles use Material 3 defaults.

### Typography (`Type.kt`)

- Only `bodyLarge` customized: `FontFamily.Default`, 16sp, lineHeight 24sp
- All other text styles are Material 3 defaults

### Dark Mode

| Layer | Status |
|-------|--------|
| Compose `darkColorScheme` | **Defined** but not active |
| `isSystemInDarkTheme()` | **Commented out** — `darkTheme` defaults to `false` |
| Dynamic dark on API 31+ | Won't trigger (requires `darkTheme = true`) |
| XML `values-night/` | **Does not exist** |
| Preview composables | Some have `darkTheme = true` variants (preview-only) |

**Verdict:** Dark mode is implemented but deliberately disabled. The app is always light.

### Shapes

- No `Shape.kt` — Material 3 defaults
- Inline corner radii: `12.dp`, `16.dp`, `20.dp`, `50.dp`, `CircleShape` scattered across screens

### Legacy XML

- `themes.xml`: `Theme.Material.Light.NoActionBar` (activity chrome only)
- `colors.xml`: Legacy palette (`purple_200`, `teal_200`, etc.) — **unused by Compose code**

---

## Summary: Architecture Health

| Dimension | Grade | Notes |
|-----------|-------|-------|
| **MVI Pattern** | A | Clean contract-based MVI with StateFlow + Channel effects |
| **Navigation** | B | Proper Compose Navigation with nested graphs; no deep links |
| **State Management** | A- | Consistent pattern; `error` field unused in Today |
| **Component Reuse** | C | Components colocated with features, duplicated `StrategyOption` |
| **Feature Completeness** | D | Many placeholder lambdas, missing screens (settings, history, analytics, timer) |
| **Data Utilization** | D | Engine produces rich behavioral data; ~60% is invisible to users |
| **Theming** | C- | Minimal customization, dark mode disabled, no shape system |
| **Code Organization** | B | Feature-based packages, but `TodayScreen.kt` is a monolith |
