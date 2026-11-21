# Nabungah — Architecture & Agent Guide

Nabungah is a single-user, fully offline Android money tracker. This document describes how the
codebase is put together, which behaviors are contractual, and how to change things without
breaking the product. Read `GUIDE.md` for the product/behavior specification; this file explains
the implementation that satisfies it.

---

## 1. At a glance

| Item | Value |
|---|---|
| Language / UI | Kotlin 2.2.10, Jetpack Compose (Material 3); UI copy and formatting in Indonesian (`id-ID`) |
| Persistence | Plain `SQLiteOpenHelper` + hand-written SQL (no Room, no DAO codegen) |
| Screens | One activity; four tabs (Home, Activity, Review, Settings) + modal bottom sheets |
| Architecture | Activity → Compose shell → `MainViewModel` → `Repo` → SQLite |
| Dependency injection | None (manual construction) |
| Navigation | Bottom bar, or a rail above 600 dp; selected tab is Compose state (no NavHost) |
| Networking | None. No `INTERNET` permission, no analytics/crash SDK |
| min / target / compile SDK | 24 / 35 / 35 |
| Build | AGP 8.9.1, Gradle 8.14.3, JDK 17 |
| Version | 1.4.1 (`versionCode` 5), schema v4 |
| Release APK | ~1.5 MB (`nabungah-release.apk`), R8 minified + resource shrunk |

The whole app is deliberately small. Do not add DI graphs, ORMs, navigation libraries, image
assets, or background job frameworks; they conflict with the product's reason to exist.

---

## 2. File map

```
app/src/main/java/com/nabungah/
├── MainActivity.kt          Single activity; edge-to-edge; hosts Dashboard
├── data/
│   ├── Models.kt            Wallet, Trx, QuickAction, Goal, Snapshot, AppData, audit models
│   ├── NabungDb.kt          Schema v4 (wallets, transactions, goals, transfers, snapshots)
│   └── Repo.kt              All SQL reads/writes, validation, JSON backup/restore
└── ui/
    ├── Theme.kt             Colors, status/semantic colors, NabungTheme
    ├── Fmt.kt               Money/date/fee formatting + parsing (pure, unit-tested)
    ├── MainViewModel.kt     AppData state, IO offloading, write/reload orchestration
    ├── Dashboard.kt         App shell: bottom tabs/nav rail, snackbar, FAB, Sheet host
    ├── HomeTab.kt           Balance hero, quick entry, wallets, goals, recent list
    ├── TransactionsTab.kt   Search, wallet/month/type filters, list/calendar, bulk select
    ├── MonthReport.kt       Pure monthly report math (totals, balance, top spending, fee)
    ├── ReviewTab.kt         Live month report, fee, auto/manual snapshots, history
    ├── SettingsTab.kt       Backup/restore, wallet & goal setup, delete tools, about
    ├── Rows.kt              Shared TransactionRow, SnapshotRow, AmountRow
    ├── Components.kt        SectionCard, fields, pickers, empty states, dialog
    └── Sheets.kt            All bottom-sheet forms, detail/edit, delete flows, clipboard text

app/src/main/res/
├── drawable/ic_launcher*.xml          Vector launcher + adaptive foreground/monochrome
├── mipmap-anydpi*/ic_launcher.xml     Legacy and adaptive launcher icons
├── values/strings.xml                 app_name
└── values[-night]/themes.xml          Launch theme, light/dark window background

app/src/test/java/com/nabungah/       JVM unit tests (Fmt, copy blocks, reports)
app/src/androidTest/java/com/nabungah/ Repo, migration and Compose UI tests
```

Package root is `com.nabungah` (application ID `com.nabungah`).

---

## 3. Runtime architecture

```
┌──────────────────── MainActivity (ComponentActivity) ────────────────────┐
│ enableEdgeToEdge(); setContent { NabungTheme { Surface { Dashboard } } } │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │ viewModel()
┌──────────────────────────────────▼───────────────────────────────────────┐
│ MainViewModel (AndroidViewModel)                                         │
│  var data: AppData  (Compose mutableStateOf)                             │
│  var loaded: Boolean                                                     │
│  write(block): launches viewModelScope → Dispatchers.IO → Repo call      │
│                → repo.load() → data = fresh → onDone()/onError()         │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │ synchronous calls on IO dispatcher
┌──────────────────────────────────▼───────────────────────────────────────┐
│ Repo                                                                    │
│  load(): AppData (wallets, trx, quick actions, goals, snapshots,        │
│          currentMonth, monthIncome, monthExpense)                        │
│  mutations: addWallet/addTransaction/delete*/transfer/upsertSnapshot…    │
│  throws ValidationException(message) for user-facing errors             │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │
                     SQLiteOpenHelper → nabungah.db (WAL)
```

Rules that keep this safe:

- **No DB call may run on the main thread.** `MainViewModel` wraps every read and write in
  `withContext(Dispatchers.IO)`. Compose only ever reads the in-memory `data` snapshot.
- **Every mutation reloads the entire `AppData`** after committing. Derived values (totals, filter
  counts, progress) are never stored; they are recomputed from the DB each time. This is cheap at
  this data volume and eliminates stale-UI bugs.
- **Validation lives in `Repo`, not only in composables.** The UI may pre-check for nicer UX
  (e.g. Tukar Uang's ordering), but `Repo` re-checks against database state and is the source of
  truth. When a `ValidationException` is thrown, the sheet shows `e.message` via `FormError`.
- **Database is a singleton per `Repo`.** `MainViewModel` owns one `Repo` for the process. Do not
  construct a second `Repo` in a composable.

### State ownership

| State | Owner | Lifetime |
|---|---|---|
| All entities + current-month totals | `MainViewModel.data` | app process |
| Loading flag | `MainViewModel.loaded` | app process |
| Selected tab | `Dashboard` (`rememberSaveable`, key `home`/`transactions`/`review`/`settings`) | composition + config change |
| Activity search/filters/limit | `TransactionsTab` (`rememberSaveable`) | composition |
| Review month | `ReviewTab` (`rememberSaveable`) | composition |
| Global fee percent | SharedPreferences (`nabungah_sync`, key `fee_percent`) | persistent |
| Snapshot source | `month_snapshot.source` (`auto`/`manual`, schema v2 column) | database |
| Open sheet / form field values | `Dashboard` + sheet composables | sheet lifetime |
| Confirmation dialogs | sheet composables (`ConfirmDialog`) | dialog lifetime |
| Backup status message | `SettingsTab` (`remember`) | composition |

The fee percent is saved globally and edited from Settings or Review. Live previews use it; each
snapshot keeps the fee it was saved with.

---

## 4. Data layer

### Schema (`NabungDb.kt`)

```sql
wallet(id PK, name, balance >= 0, starting_balance >= 0, created_at, updated_at)

trx(id PK, title, amount CHECK(amount > 0),
    type CHECK(type IN ('income','expense')),
    wallet_id REFERENCES wallet(id) ON DELETE SET NULL,
    note DEFAULT '', occurred_at, month 'YYYY-MM')
  index idx_trx_month(month)
  index idx_trx_occurred(occurred_at DESC)

quick_action(id PK, title, amount > 0, type, created_at, updated_at)

goal(id PK, title, target_amount > 0, saved_amount >= 0,
     status IN ('in_progress','completed','cancelled'),
     created_at, updated_at)

transfer_log(id PK, amount > 0,
             from_wallet_id REFERENCES wallet(id) ON DELETE SET NULL,
             to_wallet_id REFERENCES wallet(id) ON DELETE SET NULL,
             occurred_at, month)  -- Activity only; never income/expense

month_snapshot(month TEXT PRIMARY KEY,
               total_balance >= 0, total_income >= 0, total_expense >= 0,
               fee_percent REAL CHECK (0..100), fee_expense >= 0, updated_at,
               source IN ('auto','manual'))
```

- Money is **integer Rupiah** everywhere; only `fee_percent` is decimal (`REAL`).
- Timestamps are epoch millis. Display is device-local time; `month` is always derived from
  `occurred_at` with `Fmt.monthKey(...)` and indexed. A date picker can backdate a transaction;
  v4 migration and the consistency check repair any old month/date mismatch.
- `wallet.starting_balance` stores the balance before transactions, so
  `starting_balance + Σ income − Σ expense` can be audited against `balance`.
- `wallet` deletion keeps transaction history readable via `ON DELETE SET NULL`
  (`Unknown Wallet` fallback in UI).
- WAL is enabled through `SQLiteOpenHelper.setWriteAheadLoggingEnabled(true)` in `NabungDb.init`.
  **Do not use `execSQL("PRAGMA journal_mode=WAL")`** — Android throws
  `Queries can be performed using SQLiteDatabase query or rawQuery methods only` at startup.
- Foreign keys are enabled in `onConfigure`.

### Atomicity contracts (must preserve)

All of these run inside `db.beginTransaction()/setTransactionSuccessful()/endTransaction()`:

| Operation | Writes in the same transaction |
|---|---|
| Add transaction | insert `trx` + adjust wallet balance (expense guarded against negative) |
| Edit transaction | reverse old effect, validate/apply new effect, update row/month |
| Delete transaction | adjust wallet balance back (clamped at 0) + delete row; undo re-inserts |
| Bulk delete | reverse each selected row, then delete them in one transaction |
| Delete all transactions | collect rows, reverse each effect, then delete all rows |
| Transfer (Tukar Uang) | debit source + credit destination + insert `transfer_log` (balance read from DB at submit) |
| Delete all wallets / quick actions / snapshots / data | one transaction around the bulk delete |

`addTransaction` and `transfer` read the wallet balance **inside** the transaction; never trust
the list shown in the form.

### Product invariants (§13 of GUIDE.md) — do not "fix" these

1. **Transaction delete reverses wallet effects** (recommended option chosen in the spec).
   Reversal is clamped so a wallet can never go below 0.
2. **Transfers never become income/expense.** They write only to `transfer_log`, appear in
   Activity as informational rows, and do not change Total Balance. Reports and snapshots read
   `trx` only.
3. **Goals are decoupled from wallets.** Creating/editing/completing a goal never changes a
   balance or creates a transaction.
4. **Fee Expense is informational.** It is computed for display/snapshot only and never deducted.
5. **Snapshots are automatic but editable, and upserted by month** (`CONFLICT_REPLACE`).
   Finished months become `auto` snapshots when the calendar month changes; saving an edit marks a
   month `manual` and auto-sync never overwrites it. Deleting snapshots never touches
   transactions/wallets, and vice versa.
6. **Wallets cannot be edited or renamed**, but transactions can be edited: the old effect is
   reversed and the new type/amount/wallet/date is applied atomically. Goals remain editable.
7. **"Current month" for Monthly Overview comes from the device clock**, independent of the
   transaction filter.

---

## 5. Backup & restore

The **Backup & restore** card in the Settings tab exports/imports **one JSON file** through the
Storage Access Framework — no storage permissions and no external dependencies.

- **Export** opens `ActivityResultContracts.CreateDocument("application/json")`, default file name
  `nabungah-backup-yyyyMMdd-HHmm.json` (`Fmt.backupFileName`). `Repo.exportJson()` serializes every
  table with stable ids and a header:
  ```json
  { "app": "nabungah", "version": 5, "exportedAt": 0, "feePercent": 2.5,
    "wallets": [...], "transactions": [...], "quickActions": [...],
    "goals": [...], "snapshots": [{ ..., "source": "auto" }],
    "transfers": [...] }
  ```
- **Import** is destructive and always confirmed first: it **replaces** all local data rather than
  merging. `Repo.importJson()` parses and validates the whole document *before* opening the write
  transaction, so a malformed file cannot wipe the database. Header (`app`), `version` (currently
  `5`; `1`–`4` are still accepted, with starting balances derived, the saved fee defaulting to
  2.5%, and missing transfers ignored) and every row are validated, including duplicate ids,
  types, months, wallet references and non-negative snapshot numbers. The global fee percentage
  is exported and restored with the data. Before replacing anything, the UI shows a preview of the
  file's record counts and fee; DB constraints are the final guard and roll the transaction back.
- Wallet ids are preserved so transaction links survive a round trip. After restore the view model
  reloads all state.
- File I/O happens on `Dispatchers.IO` in `MainViewModel.exportBackup` / `importBackup`; the UI
  only shows a short success/error line in the Settings card.

When the backup schema changes, bump `Repo.BACKUP_VERSION`, update `exportJson`/`importJson`, and
add a round-trip case to `RepoTest`.

---

## 6. Formatting rules (`Fmt.kt`)

All pure functions, no Android dependencies — keep them that way so they stay unit-testable.

| Function | Behavior |
|---|---|
| `rp(Long)` | `"Rp 1.500.000"` (dot grouping, always `Rp ` prefix) |
| `grouped(Long)` | `"1.500.000"` |
| `groupDigits(String)` | raw digits → grouped display for inputs (`"20000"` → `"20.000"`); strips leading zeros and yields `"0"` for a non-empty zero input |
| `digitsToLong(String)` | ignores dots, returns `0` on empty/invalid |
| `sanitizeFeeInput(String)` | digits + at most **one** `,` or `.`; second separator ends input |
| `parseFee(String)` | accepts `2,5` and `2.5` |
| `feePercentLabel(Double)` | `"2,5"` (Indonesian decimal comma) |
| `feeExpense(income, percent)` | `round(income × percent / 100)` in whole Rupiah |
| `timestamp(millis)` | `"Sab, 21/11/2026, 14:05"` (`EEE, dd/MM/yyyy, HH:mm`, `id-ID`, device TZ) |
| `monthKey(millis)` | `"2026-11"` (device TZ) |

Money text fields store **digits only** in state (`TextInputField`-style grouping happens at the
display layer). Never store formatted strings or floats for money.

### Exact clipboard blocks (do not reformat)

`trxCopyText`, `snapshotCopyText`, and `monthCopyText` in `Sheets.kt` produce the plain,
professional clipboard blocks from GUIDE.md §4 and §9. They are asserted character-for-character
in `CopyTextTest`. The `*Nabungah — …*` title, field order, compact month detail lines, the `-`
empty-note placeholder, and `Rp` formatting are contractual.

---

## 7. UI structure

`Dashboard` is the app shell: a `Scaffold` with a `NavigationBar` and one shared sheet host. Each
destination owns its own `LazyColumn`, so only the visible tab composes.

```
Dashboard (Scaffold)
├── bottomBar   NavigationBar: Home | Activity | Review | Settings
├── FAB         "Add" → NewTransaction (Home and Activity only)
└── content (by tab)
    ├── HomeTab       header, hero balance (month in/out), wide wallet cards, Add/Transfer/Goal,
    │                 large quick-action cards, savings goal cards, last 5 transactions
    ├── TransactionsTab  search, List/Calendar toggle, wallet/month/type filters, "Bulan ini",
    │                 day-grouped rows or calendar dots, selected-day list, bulk select/delete,
    │                 row opens a read-only detail sheet with Edit/Delete
    ├── ReviewTab     month stepper (defaults to current month), stat dashboard (income, expense,
    │                 net, average, count, fee, month-over-month trend, biggest spending), fee card,
    │                 auto/manual status, manual edit sheet, saved-months history
    └── SettingsTab   data summary, backup & restore, saved fee, wallets, quick actions, goals,
                      data & reset (incl. delete-all-data), about
```

- **Destructive actions live in Settings**, not on the dashboard: delete wallets/transactions/
  quick actions/snapshots each open their existing sheet from the "Data & reset" card. The
  dashboard and Activity stay read-mostly.
- **Snapshots maintain themselves.** `MainViewModel.reload()` calls
  `Repo.syncAutoSnapshots()`, which is a no-op unless the month key changed. It backfills on first
  run, then only new months, skipping any month whose row is `manual`. Review's "Switch back to
  automatic" recomputes one month on demand.
- **Tab state** is a `Tab` enum looked up by its string key with `rememberSaveable`; no navigation
  library. Switching tabs disposes the previous tab's `remember` state (filters, review month).
- **Sheet host:** `Dashboard` holds `var sheet: Sheet?`; `Sheet` is a sealed interface in
  `Dashboard.kt` and is rendered through a single `ModalBottomSheet`. Tabs receive
  `onSheet: (Sheet) -> Unit`. Add a new sheet by adding a `Sheet` subtype, a composable in
  `Sheets.kt`, and a branch in the host `when`.
- **Forms:** each sheet owns its form state (`remember { mutableStateOf(...) }`), calls the VM
  with `onError = { error = it }` and `onDone = { sheet = null }`. Do not close a sheet before the
  VM confirms success.
- **Buttons:** every secondary/destructive action uses `OutlinedActionButton` (visible outline,
  red border for deletes); filled `Button` is reserved for the primary save/export action.
- **Destructive actions:** always a `ConfirmDialog`; "Delete All" is a separate red outlined
  action with its own confirmation, and "Delete all data" additionally requires typing `HAPUS`.
  Wallet deletion copy must warn that the balance is not moved.
- **Insets:** `MainActivity` calls `enableEdgeToEdge()` and the dashboard applies
  `windowInsetsPadding(WindowInsets.safeDrawing)`. Sheets add `imePadding()` +
  `navigationBarsPadding()` in `SheetColumn`.
- **Theme:** follows the system light/dark setting (`isSystemInDarkTheme()`). Semantic colors
  (`IncomeGreen`, `ExpenseRed`, `StatusOrange`, `Muted`, `CardBorder`, `CanvasBg`, tints) are
  composable getters in `Theme.kt`; `appCard()` uses `MaterialTheme.colorScheme.surface`. Reuse
  them rather than hardcoding white or a new color.
- **Layout:** below 600 dp the shell uses a bottom bar; above it, a `NavigationRail`. Content
  remains one readable column.
- **Components:** `SectionCard`, `CardAction`, `OutlinedActionButton`, `StatTile`, `EmptyState`,
  `LabeledTextField`, `MoneyField`, `PickerField`, `FormError`, `SelectableRow`, `ConfirmDialog`,
  `CopyButton`, `EmptyText` cover the recurring patterns. Prefer extending one of these over adding
  a one-off widget.

### Goal progress

`rawProgress = (saved / target).coerceIn(0, 1)` with `target <= 0 → 0`; the bar animates with
`animateFloatAsState(tween(500))`; the label uses the unanimated fraction, rounded and clamped
at 100.

---

## 8. Change recipes

### "Add a field to an entity" (e.g. wallet color)

1. Add the column in `NabungDb.onCreate` (and write an `onUpgrade` migration + bump version).
2. Add the property to the model in `Models.kt`.
3. Read/write it in `Repo` (`load` cursor mapping + insert/update `ContentValues`).
4. Surface it in the relevant card/row and sheet.

### "Add a new entity / sheet"

1. Table + indexes in `NabungDb.onCreate` (bump `version` and add `ALTER TABLE` migrations).
2. Model in `Models.kt`, include it in `AppData` and `Repo.load`.
3. Mutation methods in `Repo` with `ValidationException` messages; wrap multi-row writes in one
   DB transaction.
4. Add the mutation pass-through in `MainViewModel.write`.
5. Add `Sheet` subtype + composable in `Sheets.kt` + host branch in `Dashboard`.
   If the table belongs in backups, add it to `Repo.exportJson`/`importJson` too.
6. Add a section card to the tab that owns it (`HomeTab`, `ReviewTab` or `SettingsTab`); rows
   should be `items(...)` if the list can grow. Put delete entry points under Settings.
7. Add instrumentation coverage in `RepoTest` for the money invariants.

### "Change the review month"

`ReviewTab` steps through every month that has transactions or a snapshot, defaulting to the current
month. Every figure is derived by `buildMonthReport()`; never store totals except in a snapshot.
Keep the snapshot flow tied to the selected month. Activity's filters are independent state — don't
merge the two.

### What not to add

Accounts/auth, sync, network calls, telemetry, charts, budgets, recurring transactions, CSV
export, wallet editing, goal↔wallet auto-deduction, or letting `transfer_log` feed
income/expense totals. Transaction editing is allowed and must stay atomic.

---

## 9. Build & verify

```bash
./gradlew :app:assembleDebug            # debug APK
./gradlew :app:assembleRelease          # minified signed APK
./gradlew :app:testDebugUnitTest        # JVM: formatting, month math, clipboard blocks

adb install -r app/build/outputs/apk/release/nabungah-release.apk
adb shell am start -n com.nabungah/.MainActivity
```

### Build from Windows `cmd`

Use `gradlew.bat` (not `./gradlew`) and backslashes. Open **Command Prompt** in the project root —
the folder containing `gradlew.bat`:

```bat
cd /d D:\webvibecoding\nabungah\nabungah-apk

gradlew.bat --version
rem First run downloads the Gradle distribution; later runs use the cache.

gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease
```

Or without changing directory:

```bat
gradlew.bat -p D:\webvibecoding\nabungah\nabungah-apk :app:assembleRelease
```

Install and open it (device connected with USB debugging, or an emulator running):

```bat
adb install -r app\build\outputs\apk\release\nabungah-release.apk
adb shell am start -n com.nabungah/.MainActivity
```

If PowerShell is used instead, prefix with `.\`: `.\gradlew.bat :app:assembleDebug`.

Requirements for a local build:

- **JDK 17** on `PATH` (`java -version` should report 17). The Android Gradle Plugin targets 17.
- **Android SDK** installed; `local.properties` must contain `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`
  (the file is git-ignored and machine-specific).
- Release signing: if `keystore.properties` exists in the project root, `assembleRelease` uses it.
  Otherwise it falls back to the debug key, so the APK still installs but cannot update the
  published release. CI supplies the same values through `NABUNGAH_KEYSTORE_BASE64`,
  `NABUNGAH_KEYSTORE_PASSWORD`, `NABUNGAH_KEY_ALIAS`, `NABUNGAH_KEY_PASSWORD`.
- Verify the result: `gradlew.bat :app:testDebugUnitTest` must print `BUILD SUCCESSFUL`, and the
  APK should appear at the output path below.

**Where the APK lands** — the module is `app`, but artifacts are named after the app:

| Build | Output |
|---|---|
| Debug | `app/build/outputs/apk/debug/nabungah-debug.apk` |
| Release | `app/build/outputs/apk/release/nabungah-release.apk` |

`build/` is git-ignored, so APKs are never committed; run the Gradle command again after a clean.

- `local.properties` points at the local SDK (`sdk.dir=…`) and is git-ignored; each machine needs
  its own. Everything else is committed (including the Gradle wrapper).
- The release build prefers `keystore.properties` (or the CI env vars) and only falls back to the
  debug keystore when no release key is available. Keep `nabungah-release.jks` and
  `keystore.properties` backed up: without them an update cannot keep the same signing identity.
- Instrumentation tests use the real database: each test deletes `nabungah.db` in `@Before` and
  `@After`, and closes its `Repo`. Keep them independent and order-free.
- Verification expectations: `lintVitalRelease` runs as part of `assembleRelease`. If you change a
  validation rule, update the corresponding message assertions in the tests.

### Toolchain gotchas

- `PRAGMA journal_mode` cannot run through `execSQL`; use `setWriteAheadLoggingEnabled(true)`.
- `ExposedDropdownMenuBox` needs `Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)` and the
  `ExperimentalMaterial3Api` opt-in.
- Snapshot rows/transactions are keyed items in the parent `LazyColumn`; never nest a
  `LazyColumn` inside another.
- `Fmt` deliberately has no Android imports; don't pull `Context` or `Resources` into it.

---

## 10. Cold start

Startup work that exists today (all kept deliberately small):

- `MainViewModel` is created in `onCreate` before `setContent`, so the first database read
  overlaps Compose initialization instead of starting after the first frame.
- Until data arrives, the dashboard draws a plain background box — no spinner, no Material
  composition, no blocking work. `reportFullyDrawn()` fires once data is loaded.
- `AppData.totalBalance` and `AppData.walletNames` are computed once per loaded snapshot, not on
  every composition/row read.
- The APK embeds the Compose baseline profile (`assets/dexopt/baseline.prof`); R8 full mode and
  resource shrinking are on for release.

### Measured (Android 16 x86_64 emulator, release APK, `cmd package compile -m speed -f`)

Method: animations off, Play Store/Wellbeing/Messenger disabled or idle, 3 cold starts via
`adb shell am start -W`, plus `Displayed`/`Fully drawn` logcat lines and temporary in-app
`SystemClock` markers.

| Metric (clean idle run) | Value |
|---|---|
| `onCreate` (process → activity) | ~290 ms |
| First frame (`Displayed`) | ~735 ms |
| Data loaded (DB open + 7 queries) | ~930 ms |
| Dashboard interactive (`reportFullyDrawn`) | ~1.47 s |

Before the trims, `Displayed` was 1.68–1.96 s; after them, 1.61–1.79 s in the cleanest runs.
**The <1 s target is not demonstrated on this emulator.** The renderer is software SwiftShader
(ANGLE fell back to it), and host GPU acceleration is unavailable, so absolute timings also
swing between 1.5 s and 3.5 s depending on background load. Composition/rendering, not the
database, dominates: DB load is ~200 ms and the full dashboard composition ~500–750 ms on this
software renderer.

Before claiming <1 s, measure on a physical low-end device. If the target must be met on
emulator-class software rendering, a View/RecyclerView UI would be required instead of Compose;
that rewrite is explicitly out of scope for this rebuild.

---

## 11. Release history

| Version | Notes |
|---|---|
| 1.4.1 (`versionCode` 5) | Copy action moved into each Activity row, quick-action corner "+" removed, saved fee included in backup v5. |
| 1.4 (`versionCode` 4) | Schema v4 (`starting_balance`, checks), transaction edit/backdate/detail, bulk delete, undo snackbar, wallet filter, backup preview/info, consistency audit, dark theme, adaptive icon, nav rail, Compose UI tests. |
| 1.3 (`versionCode` 3) | Transfer log, saved global fee, Pengaturan redesign, calendar Activity, report stat dashboard, copy blocks, Windows build guide. |
| 1.2 (`versionCode` 2) | Live monthly report, automatic/manual snapshots (schema v2), load error state. |

---

## 12. Known limitations / future work

- Backup is whole-app replace-only (no merge, no partial restore) and has no automatic/scheduled
  export or file encryption. It covers the six data tables, not the device's Android auto-backup.
- Auto snapshots are maintained at month rollover, not continuously: deleting transactions from an
  old month won't refresh its `auto` snapshot until the user taps "Refresh automatic report" (or a
  later sync includes that month). The current month is never auto-saved while it is running.
- The month-end balance in a report is reconstructed by undoing later transactions; deleting a
  wallet (which removes balance without leaving a transaction) can skew historical balances.
- Compose UI tests cover tab navigation, adding a transaction, and the wallet filter. They compile
  and are ready for an emulator/device; they have not been run on this machine (`adb devices` empty).
- Cold-start timing from the software-rendered emulator is indicative only; re-measure on a
  physical low-end device before treating the <1 s target as met. The APK already embeds the
  Compose baseline profile (`assets/dexopt/baseline.prof`).
- Dark theme follows the system setting; themed icons require Android 13+. Tablet layout is a
  navigation rail only — Home/Activity two-column layouts are still future work.
