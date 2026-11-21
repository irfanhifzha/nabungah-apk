# Nabungah — Architecture & Agent Guide

Nabungah is a single-user, fully offline Android money tracker. This document describes how the
codebase is put together, which behaviors are contractual, and how to change things without
breaking the product. Read `GUIDE.md` for the product/behavior specification; this file explains
the implementation that satisfies it.

---

## 1. At a glance

| Item | Value |
|---|---|
| Language / UI | Kotlin 2.2.10, Jetpack Compose (Material 3) |
| Persistence | Plain `SQLiteOpenHelper` + hand-written SQL (no Room, no DAO codegen) |
| Screens | One activity, one scrolling dashboard, modal bottom sheets |
| Architecture | Activity → Compose screen → `MainViewModel` → `Repo` → SQLite |
| Dependency injection | None (manual construction) |
| Navigation | None (no NavHost, no fragments, no routes) |
| Networking | None. No `INTERNET` permission, no analytics/crash SDK |
| min / target / compile SDK | 24 / 35 / 35 |
| Build | AGP 8.9.1, Gradle 8.14.3, JDK 17 |
| Release APK | ~1.2 MB, R8 minified + resource shrunk |

The whole app is deliberately small. Do not add DI graphs, ORMs, navigation libraries, image
assets, or background job frameworks; they conflict with the product's reason to exist.

---

## 2. File map

```
app/src/main/java/com/nabungah/
├── MainActivity.kt          Single activity; edge-to-edge; hosts Dashboard
├── data/
│   ├── Models.kt            Wallet, Trx, QuickAction, Goal, Snapshot, AppData
│   ├── NabungDb.kt          Schema (4 tables + snapshots), WAL, foreign keys
│   └── Repo.kt              All SQL reads/writes, validation, JSON backup/restore
└── ui/
    ├── Theme.kt             Colors, status/semantic colors, NabungTheme
    ├── Fmt.kt               Money/date/fee formatting + parsing (pure, unit-tested)
    ├── MainViewModel.kt     AppData state, IO offloading, write/reload orchestration
    ├── Dashboard.kt         Screen layout + section composables + Sheet host
    ├── Components.kt        SectionCard, fields, pickers, confirm dialog, rows
    └── Sheets.kt            All 11 bottom-sheet forms, delete flows, clipboard text

app/src/main/res/
├── drawable/ic_launcher.xml      Vector launcher icon (no PNG assets)
├── values/strings.xml            app_name
└── values/themes.xml             Launch theme (no action bar), background color

app/src/test/java/com/nabungah/       JVM unit tests (Fmt, copy blocks)
app/src/androidTest/java/com/nabungah/RepoTest.kt   SQLite/instrumentation tests
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
| Transaction filters (month/type/limit) | `Dashboard` (`remember`) | composition |
| Fee % draft on the dashboard | `Dashboard` (`remember`, default `"2,5"`) | composition |
| Open sheet / form field values | `Dashboard` + sheet composables | sheet lifetime |
| Confirmation dialogs | sheet composables (`ConfirmDialog`) | dialog lifetime |
| Backup status message | `Dashboard` (`remember`) | composition |

The fee draft intentionally resets to 2.5 on reload/recreation; only snapshots persist a fee.

---

## 4. Data layer

### Schema (`NabungDb.kt`)

```sql
wallet(id PK, name, balance CHECK(balance >= 0), created_at, updated_at)

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

month_snapshot(month TEXT PRIMARY KEY, total_balance, total_income,
               total_expense, fee_percent REAL, fee_expense, updated_at)
```

- Money is **integer Rupiah** everywhere; only `fee_percent` is decimal (`REAL`).
- Timestamps are epoch millis. Display is device-local time; `month` is derived at insert with
  `Fmt.monthKey(now)` and indexed.
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
| Delete transaction | adjust wallet balance back (clamped at 0) + delete row |
| Delete all transactions | collect rows, reverse each effect, then delete all rows |
| Transfer (Tukar Uang) | debit source + credit destination (balance read from DB at submit) |
| Delete all wallets / quick actions / snapshots | one transaction around the bulk delete |

`addTransaction` and `transfer` read the wallet balance **inside** the transaction; never trust
the list shown in the form.

### Product invariants (§13 of GUIDE.md) — do not "fix" these

1. **Transaction delete reverses wallet effects** (recommended option chosen in the spec).
   Reversal is clamped so a wallet can never go below 0.
2. **Transfers create no record.** They never appear in transactions, totals, or history and do
   not change Total Balance.
3. **Goals are decoupled from wallets.** Creating/editing/completing a goal never changes a
   balance or creates a transaction.
4. **Fee Expense is informational.** It is computed for display/snapshot only and never deducted.
5. **Snapshots are manual, editable, and upserted by month** (`CONFLICT_REPLACE`); deleting
   snapshots never touches transactions/wallets, and vice versa.
6. **No editing of wallets or transactions** and no rename; only add/delete (goals are the one
   editable entity).
7. **"Current month" for Monthly Overview comes from the device clock**, independent of the
   transaction filter.

---

## 5. Backup & restore

The Backup card at the bottom of the dashboard exports/imports **one JSON file** through the
Storage Access Framework — no storage permissions and no external dependencies.

- **Export** opens `ActivityResultContracts.CreateDocument("application/json")`, default file name
  `nabungah-backup-yyyyMMdd-HHmm.json` (`Fmt.backupFileName`). `Repo.exportJson()` serializes every
  table with stable ids and a header:
  ```json
  { "app": "nabungah", "version": 1, "exportedAt": 0,
    "wallets": [...], "transactions": [...], "quickActions": [...],
    "goals": [...], "snapshots": [...] }
  ```
- **Import** is destructive and always confirmed first: it **replaces** all local data rather than
  merging. `Repo.importJson()` parses and validates the whole document *before* opening the write
  transaction, so a malformed file cannot wipe the database. Header (`app`), `version` (currently
  `1`) and every row are validated; DB constraints are the final guard and roll the transaction
  back.
- Wallet ids are preserved so transaction links survive a round trip. After restore the view model
  reloads all state.
- File I/O happens on `Dispatchers.IO` in `MainViewModel.exportBackup` / `importBackup`; the UI
  only shows a short success/error line in the Backup card.

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

`trxCopyText` and `snapshotCopyText` in `Sheets.kt` produce the WhatsApp blocks from GUIDE.md §4
and §9. They are asserted character-for-character in `CopyTextTest`. Emojis, `*bold*` markers,
field order, the `-` empty-note placeholder, and `Rp` formatting are contractual. Update the tests
only if the spec itself changes.

---

## 7. UI structure

`Dashboard` is the only screen:

```
LazyColumn (16dp padding, 14dp gaps)
├── App header ("Nabungah")
├── WalletsCard          — balances + Add / Delete / Tukar Uang
├── GoalsCard            — goal cards (tap = Edit Goal) + permanent wallet note
├── QuickActionsCard     — 2-column grid, tap pre-fills New Transaction
├── TransactionsHeaderCard — filters (month / type / limit) + count line + Add / Delete
├── TransactionRow items — lazy, one card per row, Copy button
├── MonthlyOverviewCard  — current month totals, fee input, Sync Data
├── HistoryHeaderCard    — Delete action
├── SnapshotRow items    — lazy, Copy button
└── Empty cards for every list when empty
```

- **Sheet host:** `Dashboard` holds `var sheet: Sheet?`; `Sheet` is a sealed interface in
  `Dashboard.kt` and is rendered through a single `ModalBottomSheet`. Add a new sheet by adding a
  `Sheet` subtype, a composable in `Sheets.kt`, and a branch in the host `when`.
- **Forms:** each sheet owns its form state (`remember { mutableStateOf(...) }`), calls the VM
  with `onError = { error = it }` and `onDone = { sheet = null }`. Do not close a sheet before the
  VM confirms success.
- **Destructive actions:** always a `ConfirmDialog`; "Delete All" is a separate red text action
  with its own confirmation. Wallet deletion copy must warn that the balance is not moved.
- **Insets:** `MainActivity` calls `enableEdgeToEdge()` and the dashboard applies
  `windowInsetsPadding(WindowInsets.safeDrawing)`. Sheets add `imePadding()` +
  `navigationBarsPadding()` in `SheetColumn`.
- **Theme:** light-only `lightColorScheme`. Semantic colors (`IncomeGreen`, `ExpenseRed`,
  `StatusOrange`, `Muted`, `CardBorder`, `CanvasBg`) are declared in `Theme.kt`; reuse them rather
  than hardcoding new colors.
- **Components:** `SectionCard`, `CardAction`, `LabeledTextField`, `MoneyField`, `PickerField`,
  `FormError`, `SelectableRow`, `ConfirmDialog`, `CopyButton`, `EmptyText` cover the recurring
  patterns. Prefer extending one of these over adding a one-off widget.

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
6. Add a section card in the `LazyColumn`; rows should be `items(...)` if the list can grow.
7. Add instrumentation coverage in `RepoTest` for the money invariants.

### "Add a month selector to Monthly Overview"

Allowed by §13.5, but keep the **current month as the default** and keep the snapshot flow tied to
the selected month. Transactions filters are independent; don't merge the two states.

### What not to add

Accounts/auth, sync, network calls, telemetry, charts, budgets, recurring transactions, CSV
export, wallet/transaction editing (without an explicit spec decision), goal↔wallet
auto-deduction, or a transfer history table feeding income/expense totals.

---

## 9. Build & verify

```bash
./gradlew :app:assembleDebug            # debug APK
./gradlew :app:assembleRelease          # minified installable APK
./gradlew :app:testDebugUnitTest        # JVM: formatting, clipboard blocks, file names (10 tests)
./gradlew :app:connectedDebugAndroidTest # DB behavior + backup tests (12 tests)

adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.nabungah/.MainActivity
```

- `local.properties` points at the local SDK (`sdk.dir=…`) and is git-ignored; each machine needs
  its own. Everything else is committed (including the Gradle wrapper).
- The release build signs with the **debug keystore** so the APK is installable locally. Replace
  `signingConfig` with a real keystore before distribution.
- Instrumentation tests use the real database: each test deletes `nabungah.db` in `@Before` and
  `@After`, and closes its `Repo`. Keep them independent and order-free.
- Verification expectations: `lintVitalRelease` runs as part of `assembleRelease`. If you change a
  validation rule, update the corresponding message assertions in `RepoTest`.

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

## 11. Known limitations / future work

- Backup is whole-app replace-only (no merge, no partial restore) and has no automatic/scheduled
  export. It covers the five tables, not the device's Android auto-backup.
- No UI/instrumentation tests for composables — behavior is covered at the `Repo` and formatting
  layers plus manual emulator smoke tests.
- Cold-start timing from the software-rendered emulator is indicative only; re-measure on a
  physical low-end device before treating the <1 s target as met. The APK already embeds the
  Compose baseline profile (`assets/dexopt/baseline.prof`).
- Light theme only; no dark theme, per "out of scope".
