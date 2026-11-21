# Nabungah — Rebuild Specification

> **Purpose of this document.** It describes *what the app does* and *exactly how each feature behaves*, so an agent can rebuild Nabungah from scratch with different technology. Read it as a product + behavior spec, not as documentation of the old codebase.
>
> **Target of the rebuild:** a native Android app (APK), single-user, **fully offline**, local database (SQLite), minimal and compact — small APK, fast cold start, low memory, no network dependency, no accounts, no login, no telemetry.

---

## 0. What the app is

Nabungah ("nabung" = saving) is a personal money tracker. You record **where your money physically sits** (cash, e-wallet, bank), log income and spending against those places, set savings targets, and freeze a snapshot of your finances at the end of each month.

- **One user, one device, no account.** There is no sign-up, login, password, session, profile screen, or cloud sync. The app opens straight into the dashboard with the local data.
- **There is no user identity anywhere in the data model** — no `user_id`, no `uid`, no per-user partitioning. All rows belong to the installation.
- The product is a **four-tab shell** — Home (dashboard), Activity (all transactions), Review (monthly figures) and Settings (backup, setup, data tools) — plus small modal dialogs (bottom sheets on Android) for every create/edit/delete action.
- All money values are **Indonesian Rupiah integers** (no cents, no other currency).

### Information order

The same content, split across tabs (the order inside each tab still matters):

**Home** — total balance hero with this month's income/expense, Wallets, quick entry, Quick
Actions, Nabung (savings goals), then the five most recent transactions with a link to Activity.

**Activity** — searchable, filterable transaction log grouped by day, newest first.

**Review** — one month at a time: income/expense/net, the informational fee calculator, saving
(upserting) that month's snapshot, then Monthly History (saved snapshots).

**Settings** — appearance (system/light/dark), backup/restore, wallet and quick-action setup, goal list, destructive data tools,
and the offline/about note.

This split is the product's structure; keep it.

---

## 1. Core concepts

| Concept | Meaning |
|---|---|
| **Wallet** | A place money sits: Cash, DANA, Bank Jago. Holds a balance. |
| **Transaction** | One movement of money into or out of one wallet. `income` or `expense`. Also carries the *title* and an optional note. |
| **Quick Action** | A saved transaction template (title + amount + type). Fills the transaction form in one tap. Never moves money itself. |
| **Goal** | A savings target with an optional description, saved amount, and status. **Not linked to any wallet.** |
| **Transfer ("Tukar Uang")** | Moves a balance between two wallets without income/expense semantics. Creates an activity-log entry only, never a transaction. |
| **Monthly snapshot** | A stored copy of that month's totals. Saved manually. |
| **Fee %** | A notional percentage of income, stored per snapshot. Never deducted from anything. |

---

## 2. Money & number rules (global)

- Store money as **integer Rupiah** (`INTEGER`, value in rupiah, e.g. `1500000`). Never floats.
- **Display format:** `Rp 1.500.000` — `Rp` + space + `id-ID` grouping with dots as thousand separators.
- **Input format:** as the user types, strip everything non-numeric and re-group with dots (`20000` → `20.000`). Keyboard is numeric; text field shows grouped digits.
- Negative balances are invalid everywhere (wallet balance, saved amount, transaction amount, transfer amount).
- Fee percentages are the only decimal value. Accept one decimal separator, preferring `,` or `.` per the user's locale; do not silently drop the fraction part when the user types `2,5`.
- Rounding of computed fee: `round(income * feePercent / 100)` to whole rupiah.

### Date & month rules

- **Transaction dates can be backdated.** The form offers a date picker (default: now); a saved transaction keeps the chosen local timestamp, and its `month` is derived from that date. Future dates are rejected.
- Month key is `YYYY-MM` derived from the local timestamp (`2026-05`).
- Display of a transaction timestamp: weekday short, `dd/MM/yyyy`, `HH:mm`, 24-hour, `id-ID`.
- All grouping/filtering by month uses device-local time. State the timezone assumption explicitly in the rebuild (device timezone, no UTC conversion surprises).

---

## 3. Wallets

**A wallet = `name` + `balance`.**

### Add wallet
- Fields: Wallet Name (required), Starting Balance (Rp, required ≥ 0).
- New wallets hold exactly the starting balance typed — they do **not** start at zero. Copy must make it obvious to enter the *current real* balance.
- Validation: non-empty name after trim; balance must be a valid non-negative integer.
- On save: insert wallet with `created_at = updated_at = now`.

### Display
- List of all wallets: name (small, muted) and balance (large, bold).
- Empty state: "No wallet yet".
- **Total Balance** (used in Monthly Overview) = sum of all wallet balances.

### Delete wallets
- Modal lists all wallets; select one → Delete, or Delete All.
- Both require a confirmation step.
- **Deleting a wallet does not move, refund, or zero anything.** Its money simply leaves Total Balance. Transactions referencing it become orphans and must still render (wallet label fallback: `Unknown Wallet`).
- In the rebuild, prefer a **foreign-key policy that preserves history** (`ON DELETE SET NULL`, or soft-delete the wallet) over cascading deletes of transactions. If you change this, say so — it is a deliberate product decision, not an implementation detail.
- The delete dialog must warn: deleting a wallet does not move its balance.

### Not supported
- No wallet editing/renaming (only add/delete). If added, define explicitly whether renaming touches anything else (it should not).

---

## 4. Transactions

**A transaction = `title`, `amount`, `type` ∈ {income, expense}, `wallet_id`, `note` (optional), `occurred_at`.**

### Add transaction
Entry points:
1. "+ Add" in Recent Transactions (empty form).
2. Tapping a Quick Action (form pre-filled with the action's title, amount, type; wallet still empty).

Form fields: Title, Amount (Rp), Type (Expense `-` / Income `+`, default Expense), Wallet (required select), Note (optional).

Validation, in order:
1. Title non-empty after trim → else "Title is required".
2. Amount > 0 → else "Amount must be greater than 0".
3. Wallet selected → else "Please select wallet".
4. For **expense**: `wallet.balance - amount >= 0` → else "Insufficient wallet balance".
5. Amount ≤ available balance for the chosen wallet (same check, phrased for income it's always true).

On save, **atomically** do both:
- insert transaction;
- set `wallet.balance += amount` (income) or `-= amount` (expense), `wallet.updated_at = now`.

> **Rebuild requirement:** the two writes must be one database transaction. The old version performed them as two independent writes, which could leave the list and the wallets disagreeing. Do not reproduce that.

### Transaction list
- Sorted newest first by `occurred_at`.
- Header line: `Showing {displayed} of {filtered} transactions`.
- Filters (three dropdowns, all defaulting to show everything):
  - **Month**: `All Months` + every month that currently has transactions, newest first.
  - **Type**: `All Types` / `Income` / `Expense`.
  - **Limit**: `10` / `20` / `50` / `100` (default 10).
- The limit only truncates rows. The "of N" count reflects the filtered set *before* the limit.
- Month options are derived from data — a month with no transactions must not appear.
- Empty state: "No transactions yet".

Each row shows: title, formatted timestamp, wallet name, note (only if present), and the signed amount: `+ Rp …` green for income, `- Rp …` red for expense.

### Copy to clipboard
Per-row button copies this exact plain-text block (no emoji, blank line handling as shown):

```
*Nabungah — Transaksi*
{title}
Rp {amount} · {Pemasukan|Pengeluaran}
Dompet: {wallet name}
Tanggal: {weekday short}, {dd}/{MM}/{yyyy}, {HH}:{mm}
Catatan: {note or "-"}
```

- The month report also has a compact copy action: summary lines followed by one line per
transaction (`dd/MM {title} — ± Rp {amount} · {wallet}`).
- The button shows a checkmark for ~1.5 s after copying.
- No file export, no CSV, no sharing intent is required by the original — plain clipboard text.

### Delete transactions
- Modal: select one → Delete, or Delete All. Confirmation on both.
- **Delete All should be a single database transaction.**
- **Product gap to resolve in the rebuild:** the old app did **not** reverse the wallet balance when a transaction was deleted, leaving balances permanently skewed. For the rebuild, choose and implement one of:
  - **(recommended)** deleting a transaction also reverses its effect on the wallet (guarded so balance never goes negative), or
  - keep balances untouched and state it loudly in the delete confirmation.
  
  Whichever you pick, the spec must be explicit and the behavior tested.
- **Transactions can be edited.** Editing reverses the old wallet effect and applies the new type, amount, wallet, and date atomically in one database transaction; a failed edit leaves both row and balance untouched. Wallet names remain non-editable.

---

## 5. Quick Actions

**A Quick Action = `title`, `amount`, `type`.** A reusable template.

- Displayed as a 2-column grid of tappable cards: title (semibold) + signed amount (green/red).
- Empty state: "No Quick Actions yet".
- **Tap → opens the New Transaction form pre-filled** with title, amount, type. The user still picks a wallet, may change anything, and saves. The Quick Action is not consumed, not modified, and does not itself create anything.
- Create: title (required), amount (> 0), type (default Expense).
- Delete: picker with delete-one and delete-all, both confirmed.

No edit action for quick actions — recreating means delete + add.

---

## 6. Nabung — savings goals

**A goal = `title`, optional `description`, `target_amount`, `saved_amount`, `status` ∈ {in_progress, completed, cancelled}.**

### Create
- Fields: Goal Title (required), Description (optional, multiline), Target Amount (Rp, > 0).
- New goal always: `saved_amount = 0`, `status = in_progress`.

### Display
Each goal card shows:
- Title.
- Description when non-empty (up to two lines on Home, one line in Settings).
- Status, capitalized with `_` replaced by a space; colour: `in_progress` orange, `completed` green, `cancelled` red.
- Progress bar: `saved / target * 100`, clamped to 0–100, width animates (~500 ms); guard `target = 0` (treat as 1 or 0%, never divide by zero).
- Percentage label, rounded to integer, clamped at 100.
- `Saved` (green) and `Target` (neutral) amounts.
- A permanent note under the list: goals are **not** connected to wallets, so the user must move money manually.

### Edit
- The editor exposes: title, description, target amount, saved amount (≥ 0), status (In Progress / Completed / Cancelled).
- Validation: title non-empty, target > 0, saved ≥ 0.
- Save sets `updated_at`.
- Editor also contains **Remove** (confirmation required).

### Hard rule
- **Goals never read or write wallet balances, and never create transactions.** Do not "improve" this by auto-deducting; it would break the mental model. If the rebuild adds a link, it must be opt-in and clearly surfaced.

---

## 7. Tukar Uang — move money between wallets

Purpose: ATM withdrawal, top-up from bank, moving money between e-wallets. **Not income, not expense.**

Fields: Amount (Rp), Source wallet, Destination wallet.

Validation, in order:
1. Amount > **1.000** (strictly greater; 1.000 is rejected).
2. Both wallets selected → else "Please select both wallets".
3. Source ≠ Destination → else "Wallet asal and tujuan cannot be the same".
4. Source wallet exists and `source.balance >= amount`, checked against the **database value at submit time**, not the cached list.
5. Destination wallet exists.
6. Confirmation prompt before writing.

On save: subtract from source, add to destination, both `updated_at = now`, **in one database transaction**.

Consequences to preserve:
- Transfers are logged in a **separate `transfer_log` table** shown in Activity with its own `Transfer` filter. The log must never feed income/expense totals, reports, or snapshots; Review and Home continue to read only `trx`.
- They only change wallet balances, so Total Balance stays the same.

---

## 8. Monthly Review (one month at a time)

Defaults to **the device's current month** and can step to any month that has a transaction or a
saved snapshot. It is independent of the Activity tab's filters.

All numbers are **derived live from that month's transactions**, so the selected month is always
the truth, including the current month while it is still running.

Shows:
- The selected month (`{YYYY-MM}`) and its transaction count.
- **Total Income** / **Total Expense** = sum of that month's transactions by type.
- **Net** = Total Income − Total Expense.
- **Balance at month end** = today's total balance minus every transaction after that month.
  Transfers and goals never change the total, so this reconstruction is exact unless a wallet was
  deleted (which removes balance without a transaction).
- **Average expense** and the top three expense titles for the month.
- **Fee %** — a saved global setting, default **2.5**, editable from Settings or the Review fee card. It is included in JSON backup/restore (backup v5+); goal descriptions are included from v6.
- **Fee Expense** = `round(Total Income × Fee % / 100)`; purely informational, not deducted anywhere.
- **Status** — `Running month` (current month, never auto-saved), `Automatic` (maintained by the app),
  `Manual` (frozen by the user), or `Not saved`.
- **Save this month manually / Edit report manually** opens the report editor (§9). Saving marks the
  month `manual` and stops automatic updates for it.
- **Switch back to automatic** recomputes a manual month from its transactions (or deletes the
  current month's snapshot, since the current month stays live).

Rebuild notes:
- Recompute from the database on any data change; these are derived values, never stored — except
  the snapshot itself.
- The fee percentage is **persisted globally** (SharedPreferences) and is no longer a per-screen
  draft. Snapshots keep their own historical fee copy; changing the global value never rewrites
  saved months.
- Accept decimal fee input (e.g. `2,5`). The original stripped the decimal character, making 2.5
  untypeable — fix it.

---

## 9. Monthly History — automatic snapshots, manually overridable

A snapshot freezes one finished month's totals. Snapshots are **automatic by default** and may be
edited by hand.

### Automatic rollover
- The first time the app opens in a new calendar month it writes snapshots for every finished month
  since the last sync (all finished months on a fresh install or an upgrade from 1.1).
- Values come from transactions: income, expense, the reconstructed month-end balance and the
  saved global fee (or the fee already stored for that month).
- Existing `manual` snapshots are **never** overwritten.
- An `auto` snapshot of a month whose transactions no longer exist is removed.
- A deleted auto snapshot is not recreated on the next month change unless the app has not yet seen
  that month; history stays deleted.
- Snapshots store `source` (`auto` | `manual`); v1 databases get `'manual'` via the v2 migration.

### Edit dialog
- Opened from Review for the **selected month only** (the month is fixed, not typed).
- Pre-filled with the live values: Total Balance, Total Income, Total Expense, Fee %; Fee Expense is
  computed from income and fee.
- All fields are **editable before saving** — a snapshot is deliberately whatever the user confirms,
  not necessarily what the transactions produce.
- Saving sets `source = manual` and shows a confirmation explaining that automatic updates will skip
  the month.
- **One row per month: month is the primary key.** Saving the same month again overwrites that
  snapshot (upsert), it does not append.
- Store `updated_at`.

### History list
- Sorted by month descending.
- Each row shows: month, `Auto`/`Manual`, income (green), expense (red), balance, fee amount with its
  percentage, and last updated time.
- Empty state explains that finished months are saved automatically.
- **Copy to clipboard** per row, exact block:

```
*Nabungah — {Month YYYY}*
Sumber: {Manual|Otomatis}
Pemasukan: Rp {totalIncome}
Pengeluaran: Rp {totalExpense}
Saldo: Rp {totalBalance}
Biaya ({feePercent}%): Rp {feeExpense}
Diperbarui: {updatedAt}
```
(Checkmark feedback ~1.5 s, as with transactions.)

### Delete
- Delete one selected snapshot or delete all, each confirmed.

Snapshots are independent: deleting transactions does not alter old snapshots, and deleting snapshots touches no transactions or wallets.

---

## 10. Data model for the rebuild (SQLite)

Suggested schema. All monetary columns are rupiah `INTEGER`. Timestamps stored as UTC epoch millis, displayed in device-local time.

```sql
wallet (
  id               INTEGER PRIMARY KEY,
  name             TEXT    NOT NULL,
  balance          INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
  starting_balance INTEGER NOT NULL DEFAULT 0 CHECK (starting_balance >= 0),
  created_at       INTEGER NOT NULL,
  updated_at       INTEGER NOT NULL
);

trx (
  id            INTEGER PRIMARY KEY,
  title         TEXT    NOT NULL,
  amount        INTEGER NOT NULL CHECK (amount > 0),
  type          TEXT    NOT NULL CHECK (type IN ('income','expense')),
  wallet_id     INTEGER REFERENCES wallet(id) ON DELETE SET NULL,
  note          TEXT    NOT NULL DEFAULT '',
  occurred_at   INTEGER NOT NULL,          -- chosen date or now; month derives from it
  month         TEXT    NOT NULL           -- 'YYYY-MM', derived from occurred_at
);
CREATE INDEX idx_trx_month ON trx(month);
CREATE INDEX idx_trx_occurred ON trx(occurred_at DESC);

quick_action (
  id            INTEGER PRIMARY KEY,
  title         TEXT    NOT NULL,
  amount        INTEGER NOT NULL CHECK (amount > 0),
  type          TEXT    NOT NULL CHECK (type IN ('income','expense')),
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL
);

goal (
  id            INTEGER PRIMARY KEY,
  title         TEXT    NOT NULL,
  description   TEXT    NOT NULL DEFAULT '',   -- schema v5
  target_amount INTEGER NOT NULL CHECK (target_amount > 0),
  saved_amount  INTEGER NOT NULL DEFAULT 0 CHECK (saved_amount >= 0),
  status        TEXT    NOT NULL DEFAULT 'in_progress'
                CHECK (status IN ('in_progress','completed','cancelled')),
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER
);

month_snapshot (
  month         TEXT PRIMARY KEY,          -- 'YYYY-MM', upsert target
  total_balance INTEGER NOT NULL CHECK (total_balance >= 0),
  total_income  INTEGER NOT NULL CHECK (total_income >= 0),
  total_expense INTEGER NOT NULL CHECK (total_expense >= 0),
  fee_percent   REAL    NOT NULL DEFAULT 2.5 CHECK (fee_percent BETWEEN 0 AND 100),
  fee_expense   INTEGER NOT NULL CHECK (fee_expense >= 0),
  updated_at    INTEGER NOT NULL,
  source        TEXT    NOT NULL DEFAULT 'manual'
                CHECK (source IN ('auto','manual'))   -- schema v2
);
```

Notes:
- Keep `month` on `trx` **or** derive it with a query; if derived, index `occurred_at` and compute the range bounds in the app so month filtering stays fast on large datasets.
- Integrity rules that must be enforced in code, inside one DB transaction: wallet balance changes always accompany (a) a transaction insert and, if you adopt §4's recommended delete behavior, a transaction delete, or (b) a transfer between two wallets.
- Add `PRAGMA foreign_keys = ON` and `PRAGMA journal_mode = WAL`.
- No `users` table, no auth tables, no sync/queue tables.

---

## 11. Screens & interactions for an Android build

- **Single activity** with a bottom bar of four tabs: **Home** (dashboard), **Activity** (transaction log), **Review** (monthly figures and snapshots), **Settings** (backup and data tools). Each tab is its own lazy scroll column. Bottom sheets (or full-width dialogs) replace the web modals: Add Wallet, New Transaction, New Quick Action, Create Goal, Edit Goal, Tukar Uang, Sync Month, Delete Wallets, Delete Transactions, Delete Quick Actions, Delete Monthly Data.
- Every destructive action confirms via dialog; "Delete All" is a separate, clearly-labelled red action.
- Keep the copy-to-clipboard affordances (§4, §9) using the Android clipboard.
- Empty states for all five lists, exactly as specified.
- No navigation drawer, no onboarding, and no navigation library: the tab selection is plain screen state. Destructive actions stay under Settings so the dashboard and Activity list remain read-mostly.
- Since it is offline-only, there must be **no splash/login gate**: first launch shows the empty dashboard.

---

## 12. Non-functional requirements (the point of the rebuild)

- **Fully offline.** No network permission required at all. No Firebase, no remote sync, no crash reporting, no analytics, no ads.
- **Small.** Strip to the minimum: no image-heavy assets, no web view shell. Target a release APK under ~5 MB if achievable; treat under ~10 MB as acceptable.
- **Fast.** Cold start to interactive dashboard well under 1 second on a low-end Android phone (e.g. 2 GB RAM). No blocking work on the main thread for DB queries.
- **Light.** Idle memory footprint small; lists must be lazy (recycled/`LazyColumn`), not built all at once. The "limit 10/20/50/100" selector exists precisely because the list is not meant to render everything.
- **Compact code.** One screen, five entities, no frameworks beyond what the platform needs. Do not introduce DI graphs, navigation libraries, or ORMs that dominate the binary for six tables.
- **Data safety.** All balance-mutating operations are transactional; a failed write must leave consistent state. Provide a simple local backup story (e.g. export/import a single DB or JSON file) only if it does not bloat the app; the original had none.
- **Scalability.** Keep only a bounded recent transaction list in memory (`AppData`), page/filter Activity in SQL, query Review one month at a time, and stream JSON exports. Do not reintroduce a full-table `load()` for UI state.
- **Battery/CPU:** no polling, no timers. Recompute derived totals on data change only.

---

## 13. Known behaviors to decide on (do not copy blindly)

These are real inconsistencies in the original. Each needs an explicit decision in the rebuild:

1. **Transaction delete does not refund the wallet.** Either reverse it (recommended) or warn clearly.
2. **Two non-atomic writes** on create (transaction + wallet) and on transfer. Must become atomic.
3. **Fee % cannot hold a decimal** in the original input. Support `2,5` / `2.5`.
4. **Wallet delete orphans history** ("Unknown Wallet"). Keep orphan rendering, and choose `SET NULL`/soft delete instead of cascade.
5. **Monthly Overview ignores the transaction month filter.** It is always *this* month. Keep or expose a month selector — but if you add one, the "current month" default must stay.
6. **Filter defaults to "All Months"** even though the older README claimed current month. Spec says All Months.
7. **Transfer history is informational only.** Activity reads the separate `transfer_log`; never let it feed income/expense totals, reports, or snapshots.
8. **Goal ↔ wallet are decoupled by design.** Don't auto-connect.

---

## 14. Acceptance checklist

- [ ] App installs and opens directly to an empty dashboard; no login, no network access.
- [ ] Wallet: add with starting balance; sum equals Total Balance; delete warns and leaves history readable as `Unknown Wallet`.
- [ ] Transaction: expense blocked when it would make a wallet negative; wallet balance and list update together or not at all.
- [ ] Transaction filters: month options derived from data; type; limit truncates but "X of Y" uses the unfiltered-by-limit count.
- [ ] Copy block for a transaction matches §4 exactly (plain `*Nabungah — Transaksi*` header, no emoji) and `id-ID` formatting.
- [ ] Quick Action tap pre-fills the form and does not modify the action.
- [ ] Goal: create at 0 progress, edit saved/target/status, progress capped at 100%, remove confirmed, wallets untouched.
- [ ] Tukar Uang: rejects ≤ 1.000, rejects same wallet, checks DB balance, moves money atomically, creates no transaction, Total Balance unchanged.
- [ ] Monthly Review: income/expense/net derived from the selected month, fee = round(income × %), fee never deducted, top spending and month-end balance shown.
- [ ] Snapshots: finished months become `auto` on a month change, manual edits freeze a month and are never overwritten automatically, switching back to automatic recomputes it; copy block matches §9.
- [ ] Delete-All operations work in one transaction and are confirmed.
- [ ] Offline from first launch; cold start < 1 s on low-end hardware; APK size within target.

---

## 15. Out of scope

Multi-user, accounts/auth, cloud sync, budgets per category, recurring transactions, charts/analytics screens, currency conversion, CSV/Sheets export, ads, sharing, and anything else not described above. The value of this app is that it is small and does one job; system/light/dark theming is supported because it adds no data or network surface.
