package com.nabungah.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.nabungah.ui.Fmt
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/** Thrown for user-facing validation failures. */
class ValidationException(message: String) : Exception(message)

class Repo(context: Context) {

    private val appContext = context.applicationContext
    private val helper = NabungDb(appContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    fun close() = helper.close()

    // ---------------------------------------------------------------- reads

    fun load(): AppData {
        val wallets = db.query(
            "wallet", null, null, null, null, null, "id ASC"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Wallet(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            name = c.getString(c.getColumnIndexOrThrow("name")),
                            balance = c.getLong(c.getColumnIndexOrThrow("balance")),
                            startingBalance = c.getLong(c.getColumnIndexOrThrow("starting_balance")),
                        )
                    )
                }
            }
        }

        val transactions = db.query(
            "trx", null, null, null, null, null, "occurred_at DESC, id DESC"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Trx(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            title = c.getString(c.getColumnIndexOrThrow("title")),
                            amount = c.getLong(c.getColumnIndexOrThrow("amount")),
                            type = c.getString(c.getColumnIndexOrThrow("type")),
                            walletId = c.getLongOrNull("wallet_id"),
                            note = c.getString(c.getColumnIndexOrThrow("note")),
                            occurredAt = c.getLong(c.getColumnIndexOrThrow("occurred_at")),
                            month = c.getString(c.getColumnIndexOrThrow("month")),
                        )
                    )
                }
            }
        }

        val transfers = db.query(
            "transfer_log", null, null, null, null, null, "occurred_at DESC, id DESC"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        TransferLog(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            amount = c.getLong(c.getColumnIndexOrThrow("amount")),
                            fromWalletId = c.getLongOrNull("from_wallet_id"),
                            toWalletId = c.getLongOrNull("to_wallet_id"),
                            occurredAt = c.getLong(c.getColumnIndexOrThrow("occurred_at")),
                            month = c.getString(c.getColumnIndexOrThrow("month")),
                        )
                    )
                }
            }
        }

        val quickActions = db.query(
            "quick_action", null, null, null, null, null, "id ASC"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        QuickAction(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            title = c.getString(c.getColumnIndexOrThrow("title")),
                            amount = c.getLong(c.getColumnIndexOrThrow("amount")),
                            type = c.getString(c.getColumnIndexOrThrow("type")),
                        )
                    )
                }
            }
        }

        val goals = db.query(
            "goal", null, null, null, null, null, "id ASC"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Goal(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            title = c.getString(c.getColumnIndexOrThrow("title")),
                            targetAmount = c.getLong(c.getColumnIndexOrThrow("target_amount")),
                            savedAmount = c.getLong(c.getColumnIndexOrThrow("saved_amount")),
                            status = c.getString(c.getColumnIndexOrThrow("status")),
                        )
                    )
                }
            }
        }

        val snapshots = db.query(
            "month_snapshot", null, null, null, null, null, "month DESC"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Snapshot(
                            month = c.getString(c.getColumnIndexOrThrow("month")),
                            totalBalance = c.getLong(c.getColumnIndexOrThrow("total_balance")),
                            totalIncome = c.getLong(c.getColumnIndexOrThrow("total_income")),
                            totalExpense = c.getLong(c.getColumnIndexOrThrow("total_expense")),
                            feePercent = c.getDouble(c.getColumnIndexOrThrow("fee_percent")),
                            feeExpense = c.getLong(c.getColumnIndexOrThrow("fee_expense")),
                            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
                            source = c.getString(c.getColumnIndexOrThrow("source")),
                        )
                    )
                }
            }
        }

        val month = Fmt.monthKey(System.currentTimeMillis())
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppData(
            wallets = wallets,
            transactions = transactions,
            quickActions = quickActions,
            goals = goals,
            snapshots = snapshots,
            transfers = transfers,
            feePercent = feePercent(),
            currentMonth = month,
            // Reuse the rows already loaded instead of running two extra aggregate queries.
            monthIncome = transactions.sumOf {
                if (it.month == month && it.type == "income") it.amount else 0L
            },
            monthExpense = transactions.sumOf {
                if (it.month == month && it.type == "expense") it.amount else 0L
            },
            lastBackupAt = prefs.getLong(KEY_LAST_EXPORT_AT, 0),
            lastBackupBytes = prefs.getLong(KEY_LAST_EXPORT_BYTES, 0),
        )
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
        val idx = getColumnIndexOrThrow(column)
        return if (isNull(idx)) null else getLong(idx)
    }

    private fun walletBalance(walletId: Long): Long? =
        db.rawQuery("SELECT balance FROM wallet WHERE id = ?", arrayOf(walletId.toString()))
            .use { if (it.moveToFirst()) it.getLong(0) else null }

    // -------------------------------------------------------------- wallets

    fun addWallet(name: String, startingBalance: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw ValidationException("Nama dompet wajib diisi")
        if (startingBalance < 0) throw ValidationException("Saldo tidak boleh negatif")
        val now = System.currentTimeMillis()
        db.insertOrThrow(
            "wallet", null,
            ContentValues().apply {
                put("name", trimmed)
                put("balance", startingBalance)
                put("starting_balance", startingBalance)
                put("created_at", now)
                put("updated_at", now)
            },
        )
    }

    fun deleteWallet(id: Long) {
        // Transactions keep their history: wallet_id becomes NULL (ON DELETE SET NULL).
        db.delete("wallet", "id = ?", arrayOf(id.toString()))
    }

    /** Test hook: writes a balance without the normal transaction path to simulate drift. */
    fun debugSetWalletBalance(id: Long, balance: Long) {
        db.update(
            "wallet",
            ContentValues().apply { put("balance", balance) },
            "id = ?", arrayOf(id.toString()),
        )
    }

    fun deleteAllWallets() {
        db.beginTransaction()
        try {
            db.delete("wallet", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --------------------------------------------------------- transactions

    fun addTransaction(
        title: String,
        amount: Long,
        type: String,
        walletId: Long?,
        note: String,
        occurredAt: Long = System.currentTimeMillis(),
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) throw ValidationException("Judul wajib diisi")
        if (amount <= 0) throw ValidationException("Nominal harus lebih dari 0")
        if (walletId == null) throw ValidationException("Pilih dompet")
        validateType(type)
        val whenMillis = validateOccurredAt(occurredAt)

        db.beginTransaction()
        try {
            val balance = walletBalance(walletId)
                ?: throw ValidationException("Pilih dompet")
            if (type == "expense" && balance - amount < 0) {
                throw ValidationException("Saldo dompet tidak cukup")
            }
            db.insertOrThrow(
                "trx", null,
                ContentValues().apply {
                    put("title", trimmed)
                    put("amount", amount)
                    put("type", type)
                    put("wallet_id", walletId)
                    put("note", note.trim())
                    put("occurred_at", whenMillis)
                    put("month", Fmt.monthKey(whenMillis))
                },
            )
            updateBalance(
                walletId,
                if (type == "income") balance + amount else balance - amount,
                System.currentTimeMillis(),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Edits one transaction atomically: reverses the old wallet effect, applies the new
     * values, and keeps the transaction row's month derived from its timestamp.
     */
    fun updateTransaction(
        id: Long,
        title: String,
        amount: Long,
        type: String,
        walletId: Long?,
        note: String,
        occurredAt: Long,
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) throw ValidationException("Judul wajib diisi")
        if (amount <= 0) throw ValidationException("Nominal harus lebih dari 0")
        if (walletId == null) throw ValidationException("Pilih dompet")
        validateType(type)
        val whenMillis = validateOccurredAt(occurredAt)

        db.beginTransaction()
        try {
            val old = db.query("trx", null, "id = ?", arrayOf(id.toString()), null, null, null)
                .use { if (it.moveToFirst()) it.toTrx() else null }
                ?: throw ValidationException("Transaksi tidak ditemukan")
            val balance = walletBalance(walletId)
                ?: throw ValidationException("Pilih dompet")
            val balanceBefore = if (old.walletId == walletId) {
                // Undo the old row first, then validate the new one against that balance.
                when (old.type) {
                    "income" -> balance - old.amount
                    else -> balance + old.amount
                }
            } else {
                balance
            }
            if (balanceBefore < 0) throw ValidationException("Saldo dompet tidak cukup")
            if (type == "expense" && balanceBefore - amount < 0) {
                throw ValidationException("Saldo dompet tidak cukup")
            }
            if (old.walletId != null && old.walletId != walletId) {
                reverseEffect(old)
                val targetBalance = walletBalance(walletId)
                    ?: throw ValidationException("Pilih dompet")
                updateBalance(
                    walletId,
                    if (type == "income") targetBalance + amount else targetBalance - amount,
                    System.currentTimeMillis(),
                )
            } else {
                updateBalance(
                    walletId,
                    if (type == "income") balanceBefore + amount else balanceBefore - amount,
                    System.currentTimeMillis(),
                )
            }
            db.update(
                "trx",
                ContentValues().apply {
                    put("title", trimmed)
                    put("amount", amount)
                    put("type", type)
                    put("wallet_id", walletId)
                    put("note", note.trim())
                    put("occurred_at", whenMillis)
                    put("month", Fmt.monthKey(whenMillis))
                },
                "id = ?", arrayOf(id.toString()),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Deletes one transaction, reversing its effect on the wallet (clamped at 0). */
    fun deleteTransaction(id: Long): Trx? {
        db.beginTransaction()
        try {
            val row = db.query("trx", null, "id = ?", arrayOf(id.toString()), null, null, null)
                .use { if (it.moveToFirst()) it.toTrx() else null }
            if (row != null) {
                reverseEffect(row)
                db.delete("trx", "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
            return row
        } finally {
            db.endTransaction()
        }
    }

    /** Deletes several transactions in one database transaction. */
    fun deleteTransactions(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        db.beginTransaction()
        try {
            ids.forEach { id ->
                val row = db.query("trx", null, "id = ?", arrayOf(id.toString()), null, null, null)
                    .use { if (it.moveToFirst()) it.toTrx() else null }
                if (row != null) {
                    reverseEffect(row)
                    db.delete("trx", "id = ?", arrayOf(id.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Re-inserts a just-deleted transaction and reapplies its wallet effect (undo). */
    fun restoreTransaction(trx: Trx) {
        db.beginTransaction()
        try {
            val walletId = trx.walletId
            if (walletId != null) {
                val balance = walletBalance(walletId)
                    ?: throw ValidationException("Dompet transaksi sudah dihapus")
                if (trx.type == "expense" && balance - trx.amount < 0) {
                    throw ValidationException("Saldo dompet tidak cukup untuk membatalkan")
                }
                updateBalance(
                    walletId,
                    if (trx.type == "income") balance + trx.amount else balance - trx.amount,
                    System.currentTimeMillis(),
                )
            }
            db.insertOrThrow(
                "trx", null,
                ContentValues().apply {
                    put("title", trx.title)
                    put("amount", trx.amount)
                    put("type", trx.type)
                    if (walletId == null) putNull("wallet_id") else put("wallet_id", walletId)
                    put("note", trx.note)
                    put("occurred_at", trx.occurredAt)
                    put("month", Fmt.monthKey(trx.occurredAt))
                },
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun validateType(type: String) {
        if (type != "income" && type != "expense") {
            throw ValidationException("Tipe transaksi tidak dikenal")
        }
    }

    private fun validateOccurredAt(value: Long): Long {
        val now = System.currentTimeMillis()
        if (value <= 0) throw ValidationException("Tanggal transaksi tidak valid")
        // Allow a small clock skew but no future transactions: month reports depend on it.
        if (value > now + 60_000) throw ValidationException("Tanggal tidak boleh di masa depan")
        return value
    }

    /** Deletes every transaction in one DB transaction, reversing all wallet effects. */
    fun deleteAllTransactions() {
        db.beginTransaction()
        try {
            val rows = db.query("trx", null, null, null, null, null, "occurred_at DESC")
                .use { c -> buildList { while (c.moveToNext()) add(c.toTrx()) } }
            rows.forEach { reverseEffect(it) }
            db.delete("trx", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun reverseEffect(row: Trx) {
        val walletId = row.walletId ?: return
        val balance = walletBalance(walletId) ?: return
        val now = System.currentTimeMillis()
        val newBalance = if (row.type == "income") {
            max(0L, balance - row.amount)
        } else {
            balance + row.amount
        }
        updateBalance(walletId, newBalance, now)
    }

    private fun updateBalance(walletId: Long, newBalance: Long, now: Long) {
        db.update(
            "wallet",
            ContentValues().apply {
                put("balance", newBalance)
                put("updated_at", now)
            },
            "id = ?", arrayOf(walletId.toString()),
        )
    }

    private fun Cursor.toTrx() = Trx(
        id = getLong(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        amount = getLong(getColumnIndexOrThrow("amount")),
        type = getString(getColumnIndexOrThrow("type")),
        walletId = getLongOrNull("wallet_id"),
        note = getString(getColumnIndexOrThrow("note")),
        occurredAt = getLong(getColumnIndexOrThrow("occurred_at")),
        month = getString(getColumnIndexOrThrow("month")),
    )

    // --------------------------------------------------------- quick actions

    fun addQuickAction(title: String, amount: Long, type: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) throw ValidationException("Judul wajib diisi")
        if (amount <= 0) throw ValidationException("Nominal harus lebih dari 0")
        val now = System.currentTimeMillis()
        db.insertOrThrow(
            "quick_action", null,
            ContentValues().apply {
                put("title", trimmed)
                put("amount", amount)
                put("type", type)
                put("created_at", now)
                put("updated_at", now)
            },
        )
    }

    fun deleteQuickAction(id: Long) {
        db.delete("quick_action", "id = ?", arrayOf(id.toString()))
    }

    fun deleteAllQuickActions() {
        db.beginTransaction()
        try {
            db.delete("quick_action", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------------------------------------------------------------- goals

    fun addGoal(title: String, target: Long) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) throw ValidationException("Judul wajib diisi")
        if (target <= 0) throw ValidationException("Jumlah target harus lebih dari 0")
        val now = System.currentTimeMillis()
        db.insertOrThrow(
            "goal", null,
            ContentValues().apply {
                put("title", trimmed)
                put("target_amount", target)
                put("saved_amount", 0L)
                put("status", "in_progress")
                put("created_at", now)
                put("updated_at", now)
            },
        )
        // Wallet balances are deliberately untouched: goals are not linked to wallets.
    }

    fun updateGoal(id: Long, title: String, target: Long, saved: Long, status: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) throw ValidationException("Judul wajib diisi")
        if (target <= 0) throw ValidationException("Jumlah target harus lebih dari 0")
        if (saved < 0) throw ValidationException("Jumlah terkumpul tidak boleh negatif")
        // A completed goal follows the numbers; an explicit cancellation stays cancelled.
        val normalizedStatus = when {
            status == "cancelled" -> "cancelled"
            saved >= target -> "completed"
            else -> "in_progress"
        }
        db.update(
            "goal",
            ContentValues().apply {
                put("title", trimmed)
                put("target_amount", target)
                put("saved_amount", saved)
                put("status", normalizedStatus)
                put("updated_at", System.currentTimeMillis())
            },
            "id = ?", arrayOf(id.toString()),
        )
    }

    fun deleteGoal(id: Long) {
        db.delete("goal", "id = ?", arrayOf(id.toString()))
    }

    // ------------------------------------------------------------------- fee

    /** Fee percentage saved by the user; used for live previews and new snapshots. */
    fun feePercent(): Double = appContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getFloat(KEY_FEE_PERCENT, DEFAULT_FEE_PERCENT.toFloat())
        .toDouble()

    fun saveFeePercent(percent: Double) {
        if (percent < 0 || percent > 100) {
            throw ValidationException("Biaya harus antara 0 dan 100 persen")
        }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FEE_PERCENT, percent.toFloat())
            .apply()
    }

    // ------------------------------------------------------------ transfers

    /** Moves money between wallets atomically and appends an activity-log entry. */
    fun transfer(amount: Long, fromId: Long?, toId: Long?) {
        if (amount <= 1000) throw ValidationException("Minimal transfer Rp 1.001")
        if (fromId == null || toId == null) throw ValidationException("Pilih kedua dompet")
        if (fromId == toId) throw ValidationException("Dompet asal dan tujuan tidak boleh sama")

        db.beginTransaction()
        try {
            val from = walletBalance(fromId) ?: throw ValidationException("Pilih kedua dompet")
            val to = walletBalance(toId) ?: throw ValidationException("Pilih kedua dompet")
            if (from < amount) throw ValidationException("Saldo dompet tidak cukup")
            val now = System.currentTimeMillis()
            updateBalance(fromId, from - amount, now)
            updateBalance(toId, to + amount, now)
            db.insertOrThrow(
                "transfer_log", null,
                ContentValues().apply {
                    put("amount", amount)
                    put("from_wallet_id", fromId)
                    put("to_wallet_id", toId)
                    put("occurred_at", now)
                    put("month", Fmt.monthKey(now))
                },
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ------------------------------------------------------- data auditing

    /** Compares every stored wallet balance with `starting + income - expense`. */
    fun auditConsistency(): ConsistencyReport {
        val drift = db.rawQuery(
            """
            SELECT w.name, w.balance, w.starting_balance + COALESCE(SUM(
                CASE WHEN t.type = 'income' THEN t.amount
                     WHEN t.type = 'expense' THEN -t.amount ELSE 0 END
            ), 0)
            FROM wallet w LEFT JOIN trx t ON t.wallet_id = w.id
            GROUP BY w.id
            HAVING w.balance <> w.starting_balance + COALESCE(SUM(
                CASE WHEN t.type = 'income' THEN t.amount
                     WHEN t.type = 'expense' THEN -t.amount ELSE 0 END
            ), 0)
            ORDER BY w.id
            """.trimIndent(),
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(WalletDrift(c.getString(0), c.getLong(1), c.getLong(2)))
                }
            }
        }
        val monthMismatches = db.rawQuery(
            """
            SELECT COUNT(*) FROM trx
            WHERE month <> strftime('%Y-%m', occurred_at / 1000, 'unixepoch', 'localtime')
            """.trimIndent(),
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val snapshotIssues = db.rawQuery(
            """
            SELECT COUNT(*) FROM month_snapshot
            WHERE total_balance < 0 OR total_income < 0 OR total_expense < 0
               OR fee_percent < 0 OR fee_percent > 100 OR fee_expense < 0
            """.trimIndent(),
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return ConsistencyReport(drift, monthMismatches, snapshotIssues)
    }

    /** Repairs balances, months and invalid snapshot numbers in one database transaction. */
    fun fixConsistency() {
        db.beginTransaction()
        try {
            db.execSQL(
                """
                UPDATE wallet SET balance = MAX(0, starting_balance + COALESCE((
                    SELECT SUM(CASE WHEN type = 'income' THEN amount ELSE -amount END)
                    FROM trx WHERE trx.wallet_id = wallet.id
                ), 0)), updated_at = ?
                """.trimIndent(),
                arrayOf<Any>(System.currentTimeMillis()),
            )
            db.execSQL(
                """
                UPDATE trx SET month = strftime('%Y-%m', occurred_at / 1000, 'unixepoch', 'localtime')
                WHERE month <> strftime('%Y-%m', occurred_at / 1000, 'unixepoch', 'localtime')
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE month_snapshot SET
                    total_balance = MAX(0, total_balance),
                    total_income = MAX(0, total_income),
                    total_expense = MAX(0, total_expense),
                    fee_percent = MIN(100, MAX(0, fee_percent)),
                    fee_expense = MAX(0, fee_expense)
                """.trimIndent()
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --------------------------------------------------------- destructive

    /** Wipes wallets, transactions, quick actions, goals, snapshots and transfer logs. */
    fun deleteAllData() {
        db.beginTransaction()
        try {
            db.delete("transfer_log", null, null)
            db.delete("trx", null, null)
            db.delete("wallet", null, null)
            db.delete("quick_action", null, null)
            db.delete("goal", null, null)
            db.delete("month_snapshot", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --------------------------------------------------------------- backup

    /** Serializes every table and the saved fee to one JSON document (schema version 5). */
    fun exportJson(): String {
        val root = JSONObject()
        root.put("app", BACKUP_APP)
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("feePercent", feePercent())

        val wallets = JSONArray()
        db.query("wallet", null, null, null, null, null, "id ASC").use { c ->
            while (c.moveToNext()) {
                wallets.put(
                    JSONObject()
                        .put("id", c.longOf("id"))
                        .put("name", c.stringOf("name"))
                        .put("balance", c.longOf("balance"))
                        .put("starting_balance", c.longOf("starting_balance"))
                        .put("created_at", c.longOf("created_at"))
                        .put("updated_at", c.longOf("updated_at")),
                )
            }
        }
        root.put("wallets", wallets)

        val transactions = JSONArray()
        db.query("trx", null, null, null, null, null, "id ASC").use { c ->
            while (c.moveToNext()) {
                val item = JSONObject()
                    .put("id", c.longOf("id"))
                    .put("title", c.stringOf("title"))
                    .put("amount", c.longOf("amount"))
                    .put("type", c.stringOf("type"))
                    .put("note", c.stringOf("note"))
                    .put("occurred_at", c.longOf("occurred_at"))
                    .put("month", c.stringOf("month"))
                val walletIdx = c.getColumnIndexOrThrow("wallet_id")
                item.put(
                    "wallet_id",
                    if (c.isNull(walletIdx)) JSONObject.NULL else c.getLong(walletIdx),
                )
                transactions.put(item)
            }
        }
        root.put("transactions", transactions)

        val quickActions = JSONArray()
        db.query("quick_action", null, null, null, null, null, "id ASC").use { c ->
            while (c.moveToNext()) {
                quickActions.put(
                    JSONObject()
                        .put("id", c.longOf("id"))
                        .put("title", c.stringOf("title"))
                        .put("amount", c.longOf("amount"))
                        .put("type", c.stringOf("type"))
                        .put("created_at", c.longOf("created_at"))
                        .put("updated_at", c.longOf("updated_at")),
                )
            }
        }
        root.put("quickActions", quickActions)

        val goals = JSONArray()
        db.query("goal", null, null, null, null, null, "id ASC").use { c ->
            while (c.moveToNext()) {
                goals.put(
                    JSONObject()
                        .put("id", c.longOf("id"))
                        .put("title", c.stringOf("title"))
                        .put("target_amount", c.longOf("target_amount"))
                        .put("saved_amount", c.longOf("saved_amount"))
                        .put("status", c.stringOf("status"))
                        .put("created_at", c.longOf("created_at"))
                        .put("updated_at", c.longOf("updated_at")),
                )
            }
        }
        root.put("goals", goals)

        val snapshots = JSONArray()
        db.query("month_snapshot", null, null, null, null, null, "month ASC").use { c ->
            while (c.moveToNext()) {
                snapshots.put(
                    JSONObject()
                        .put("month", c.stringOf("month"))
                        .put("total_balance", c.longOf("total_balance"))
                        .put("total_income", c.longOf("total_income"))
                        .put("total_expense", c.longOf("total_expense"))
                        .put("fee_percent", c.getDouble(c.getColumnIndexOrThrow("fee_percent")))
                        .put("fee_expense", c.longOf("fee_expense"))
                        .put("updated_at", c.longOf("updated_at"))
                        .put("source", c.stringOf("source")),
                )
            }
        }
        root.put("snapshots", snapshots)

        val transfers = JSONArray()
        db.query("transfer_log", null, null, null, null, null, "id ASC").use { c ->
            while (c.moveToNext()) {
                val item = JSONObject()
                    .put("id", c.longOf("id"))
                    .put("amount", c.longOf("amount"))
                    .put("occurred_at", c.longOf("occurred_at"))
                    .put("month", c.stringOf("month"))
                listOf("from_wallet_id", "to_wallet_id").forEach { column ->
                    val idx = c.getColumnIndexOrThrow(column)
                    item.put(column, if (c.isNull(idx)) JSONObject.NULL else c.getLong(idx))
                }
                transfers.put(item)
            }
        }
        root.put("transfers", transfers)

        return root.toString(2)
    }

    /** Reads only the summary of a backup file, before the replace confirmation. */
    fun inspectBackup(text: String): BackupPreview {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ValidationException("File backup tidak valid")
        }
        if (root.optString("app") != BACKUP_APP) {
            throw ValidationException("Bukan file backup Nabungah")
        }
        val version = root.optInt("version", 0)
        if (version !in 1..BACKUP_VERSION) {
            throw ValidationException("Versi backup tidak didukung")
        }
        fun size(key: String): Int = root.optJSONArray(key)?.length() ?: 0
        val fee = root.optDouble("feePercent", DEFAULT_FEE_PERCENT)
        if (fee.isNaN() || fee < 0 || fee > 100) {
            throw ValidationException("Persentase biaya tidak valid")
        }
        return BackupPreview(
            version = version,
            exportedAt = root.optLong("exportedAt", 0),
            feePercent = fee,
            wallets = size("wallets"),
            transactions = size("transactions"),
            transfers = size("transfers"),
            goals = size("goals"),
            quickActions = size("quickActions"),
            snapshots = size("snapshots"),
        )
    }

    /** Remembers a successful export for the backup health card in Settings. */
    fun markBackupExported(bytes: Long) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_EXPORT_AT, System.currentTimeMillis())
            .putLong(KEY_LAST_EXPORT_BYTES, bytes)
            .apply()
    }

    /**
     * Replaces all local data with the contents of a backup document. Parsing happens
     * before the transaction, so a malformed file can never leave the database wiped.
     */
    fun importJson(text: String) {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ValidationException("File backup tidak valid")
        }
        if (root.optString("app") != BACKUP_APP) {
            throw ValidationException("Bukan file backup Nabungah")
        }
        val version = root.optInt("version", 0)
        // v1 files predate snapshot sources; their snapshots are treated as manual.
        if (version !in 1..BACKUP_VERSION) {
            throw ValidationException("Versi backup tidak didukung")
        }
        val importedFee = if (version >= 5) {
            root.optDouble("feePercent", DEFAULT_FEE_PERCENT)
        } else {
            DEFAULT_FEE_PERCENT
        }
        if (importedFee.isNaN() || importedFee < 0 || importedFee > 100) {
            throw ValidationException("Persentase biaya tidak valid")
        }

        val walletRows = mutableListOf<ContentValues>()
        val trxRows = mutableListOf<ContentValues>()
        val quickActionRows = mutableListOf<ContentValues>()
        val goalRows = mutableListOf<ContentValues>()
        val snapshotRows = mutableListOf<ContentValues>()
        val transferRows = mutableListOf<ContentValues>()
        val walletIds = mutableSetOf<Long>()
        val trxIds = mutableSetOf<Long>()
        val transferIds = mutableSetOf<Long>()
        val quickIds = mutableSetOf<Long>()
        val goalIds = mutableSetOf<Long>()
        val snapshotMonths = mutableSetOf<String>()
        val netByWallet = HashMap<Long, Long>()

        try {
            val wallets = root.optJSONArray("wallets") ?: JSONArray()
            for (i in 0 until wallets.length()) {
                val o = wallets.getJSONObject(i)
                val id = o.getLong("id")
                if (!walletIds.add(id)) throw ValidationException("ID dompet duplikat")
                val name = o.getString("name").trim()
                val balance = o.getLong("balance")
                val starting = if (version >= 4) o.optLong("starting_balance", 0) else 0L
                if (name.isEmpty() || balance < 0 || starting < 0) {
                    throw ValidationException("Data dompet tidak valid")
                }
                walletRows.add(
                    ContentValues().apply {
                        put("id", id)
                        put("name", name)
                        put("balance", balance)
                        put("starting_balance", starting)
                        put("created_at", o.getLong("created_at"))
                        put("updated_at", o.getLong("updated_at"))
                    },
                )
            }

            val transactions = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until transactions.length()) {
                val o = transactions.getJSONObject(i)
                val id = o.getLong("id")
                if (!trxIds.add(id)) throw ValidationException("ID transaksi duplikat")
                val amount = o.getLong("amount")
                val type = o.getString("type")
                val occurredAt = o.getLong("occurred_at")
                val walletId = if (o.isNull("wallet_id")) null else o.getLong("wallet_id")
                if (o.getString("title").trim().isEmpty() || amount <= 0) {
                    throw ValidationException("Data transaksi tidak valid")
                }
                if (type != "income" && type != "expense") {
                    throw ValidationException("Tipe transaksi tidak dikenal")
                }
                if (walletId != null && walletId !in walletIds) {
                    throw ValidationException("Transaksi merujuk dompet yang tidak ada")
                }
                trxRows.add(
                    ContentValues().apply {
                        put("id", id)
                        put("title", o.getString("title").trim())
                        put("amount", amount)
                        put("type", type)
                        put("note", o.optString("note", ""))
                        put("occurred_at", occurredAt)
                        put("month", Fmt.monthKey(occurredAt))
                        if (walletId == null) putNull("wallet_id") else put("wallet_id", walletId)
                    },
                )
                if (walletId != null) {
                    val delta = if (type == "income") amount else -amount
                    netByWallet[walletId] = (netByWallet[walletId] ?: 0L) + delta
                }
            }

            // Old backups had no starting balance: derive it from the current balance and
            // the transaction history, exactly like the v4 migration does.
            if (version < 4) {
                walletRows.forEach { row ->
                    val id = row.getAsLong("id") ?: 0L
                    val balance = row.getAsLong("balance") ?: 0L
                    row.put("starting_balance", max(0L, balance - (netByWallet[id] ?: 0L)))
                }
            }

            val quickActions = root.optJSONArray("quickActions") ?: JSONArray()
            for (i in 0 until quickActions.length()) {
                val o = quickActions.getJSONObject(i)
                val id = o.getLong("id")
                if (!quickIds.add(id)) throw ValidationException("ID aksi cepat duplikat")
                val amount = o.getLong("amount")
                val type = o.getString("type")
                if (o.getString("title").trim().isEmpty() || amount <= 0 ||
                    (type != "income" && type != "expense")
                ) {
                    throw ValidationException("Aksi cepat tidak valid")
                }
                quickActionRows.add(
                    ContentValues().apply {
                        put("id", id)
                        put("title", o.getString("title").trim())
                        put("amount", amount)
                        put("type", type)
                        put("created_at", o.getLong("created_at"))
                        put("updated_at", o.getLong("updated_at"))
                    },
                )
            }

            val goals = root.optJSONArray("goals") ?: JSONArray()
            for (i in 0 until goals.length()) {
                val o = goals.getJSONObject(i)
                val id = o.getLong("id")
                if (!goalIds.add(id)) throw ValidationException("ID target duplikat")
                val target = o.getLong("target_amount")
                val saved = o.optLong("saved_amount", 0)
                val status = o.optString("status", "in_progress")
                if (o.getString("title").trim().isEmpty() || target <= 0 || saved < 0 ||
                    status !in setOf("in_progress", "completed", "cancelled")
                ) {
                    throw ValidationException("Target tidak valid")
                }
                goalRows.add(
                    ContentValues().apply {
                        put("id", id)
                        put("title", o.getString("title").trim())
                        put("target_amount", target)
                        put("saved_amount", saved)
                        put("status", if (saved >= target && status != "cancelled") "completed" else status)
                        put("created_at", o.getLong("created_at"))
                        put("updated_at", o.optLong("updated_at", 0))
                    },
                )
            }

            val transfers = root.optJSONArray("transfers") ?: JSONArray()
            for (i in 0 until transfers.length()) {
                val o = transfers.getJSONObject(i)
                val id = o.getLong("id")
                if (!transferIds.add(id)) throw ValidationException("ID transfer duplikat")
                val amount = o.getLong("amount")
                val occurredAt = o.getLong("occurred_at")
                if (amount <= 0) throw ValidationException("Transfer tidak valid")
                val fromId = if (o.isNull("from_wallet_id")) null else o.getLong("from_wallet_id")
                val toId = if (o.isNull("to_wallet_id")) null else o.getLong("to_wallet_id")
                if ((fromId != null && fromId !in walletIds) ||
                    (toId != null && toId !in walletIds)
                ) {
                    throw ValidationException("Transfer merujuk dompet yang tidak ada")
                }
                transferRows.add(
                    ContentValues().apply {
                        put("id", id)
                        put("amount", amount)
                        put("occurred_at", occurredAt)
                        put("month", Fmt.monthKey(occurredAt))
                        listOf("from_wallet_id", "to_wallet_id").forEach { column ->
                            if (o.isNull(column)) putNull(column) else put(column, o.getLong(column))
                        }
                    },
                )
            }

            val snapshots = root.optJSONArray("snapshots") ?: JSONArray()
            for (i in 0 until snapshots.length()) {
                val o = snapshots.getJSONObject(i)
                val month = o.getString("month")
                if (!snapshotMonths.add(month)) throw ValidationException("Bulan snapshot duplikat")
                val balance = o.getLong("total_balance")
                val income = o.getLong("total_income")
                val expense = o.getLong("total_expense")
                val feePercent = o.optDouble("fee_percent", 2.5)
                val feeExpense = o.getLong("fee_expense")
                if (balance < 0 || income < 0 || expense < 0 || feeExpense < 0 ||
                    feePercent < 0 || feePercent > 100
                ) {
                    throw ValidationException("Data snapshot tidak valid")
                }
                snapshotRows.add(
                    ContentValues().apply {
                        put("month", month)
                        put("total_balance", balance)
                        put("total_income", income)
                        put("total_expense", expense)
                        put("fee_percent", feePercent)
                        put("fee_expense", feeExpense)
                        put("updated_at", o.getLong("updated_at"))
                        val source = if (version >= 2) o.optString("source", Snapshot.SOURCE_MANUAL)
                        else Snapshot.SOURCE_MANUAL
                        if (source != Snapshot.SOURCE_AUTO && source != Snapshot.SOURCE_MANUAL) {
                            throw ValidationException("File backup tidak valid")
                        }
                        put("source", source)
                    },
                )
            }
        } catch (e: ValidationException) {
            throw e
        } catch (e: Exception) {
            throw ValidationException("File backup tidak valid")
        }

        db.beginTransaction()
        try {
            db.delete("transfer_log", null, null)
            db.delete("trx", null, null)
            db.delete("wallet", null, null)
            db.delete("quick_action", null, null)
            db.delete("goal", null, null)
            db.delete("month_snapshot", null, null)
            walletRows.forEach { db.insertOrThrow("wallet", null, it) }
            trxRows.forEach { db.insertOrThrow("trx", null, it) }
            transferRows.forEach { db.insertOrThrow("transfer_log", null, it) }
            quickActionRows.forEach { db.insertOrThrow("quick_action", null, it) }
            goalRows.forEach { db.insertOrThrow("goal", null, it) }
            snapshotRows.forEach { db.insertOrThrow("month_snapshot", null, it) }
            db.setTransactionSuccessful()
        } catch (e: SQLiteConstraintException) {
            throw ValidationException("Data backup tidak valid")
        } finally {
            db.endTransaction()
        }
        saveFeePercent(importedFee)
    }

    private fun Cursor.longOf(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.stringOf(column: String): String = getString(getColumnIndexOrThrow(column))

    companion object {
        private const val BACKUP_APP = "nabungah"
        private const val BACKUP_VERSION = 5
        private const val PREFS_NAME = "nabungah_sync"
        private const val KEY_LAST_AUTO_MONTH = "last_auto_month"
        private const val KEY_FEE_PERCENT = "fee_percent"
        private const val KEY_LAST_EXPORT_AT = "last_export_at"
        private const val KEY_LAST_EXPORT_BYTES = "last_export_bytes"
        private const val DEFAULT_FEE_PERCENT = 2.5
    }

    // ------------------------------------------------------------ snapshots

    /** One row per month: month is the primary key, so re-syncing overwrites. */
    fun upsertSnapshot(
        month: String,
        totalBalance: Long,
        totalIncome: Long,
        totalExpense: Long,
        feePercent: Double,
        feeExpense: Long,
        source: String = Snapshot.SOURCE_MANUAL,
    ) {
        val key = month.trim()
        if (key.isEmpty()) throw ValidationException("Bulan wajib diisi")
        if (totalBalance < 0 || totalIncome < 0 || totalExpense < 0 || feeExpense < 0) {
            throw ValidationException("Angka snapshot tidak boleh negatif")
        }
        if (feePercent < 0 || feePercent > 100) {
            throw ValidationException("Biaya snapshot harus antara 0 dan 100 persen")
        }
        if (source != Snapshot.SOURCE_AUTO && source != Snapshot.SOURCE_MANUAL) {
            throw ValidationException("Sumber snapshot tidak dikenal")
        }
        db.insertWithOnConflict(
            "month_snapshot", null,
            ContentValues().apply {
                put("month", key)
                put("total_balance", totalBalance)
                put("total_income", totalIncome)
                put("total_expense", totalExpense)
                put("fee_percent", feePercent)
                put("fee_expense", feeExpense)
                put("updated_at", System.currentTimeMillis())
                put("source", source)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /**
     * Freezes every finished month from its transactions. Runs at most once per calendar month
     * (and on first launch after an upgrade), so it is cheap to call before every reload.
     *
     * - Months before the current one get (or refresh) an `auto` snapshot.
     * - `manual` snapshots are never touched.
     * - `auto` snapshots of months that no longer have transactions are removed.
     *
     * @return true when the app wrote something.
     */
    fun syncAutoSnapshots(force: Boolean = false): Boolean {
        val currentMonth = Fmt.monthKey(System.currentTimeMillis())
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastMonth = prefs.getString(KEY_LAST_AUTO_MONTH, null)
        if (!force && lastMonth == currentMonth) return false
        // First run (fresh install or upgrade to 1.2) backfills every finished month. Later
        // rollovers only touch months the app has not seen yet, so deleting an old snapshot stays
        // deleted instead of being recreated on the next month change.
        val backfill = lastMonth == null

        // Totals per completed month.
        val totals = db.rawQuery(
            """
            SELECT month,
                   COALESCE(SUM(CASE WHEN type = 'income'  THEN amount ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0)
            FROM trx WHERE month < ? GROUP BY month ORDER BY month ASC
            """.trimIndent(),
            arrayOf(currentMonth),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Triple(c.getString(0), c.getLong(1), c.getLong(2)))
            }
        }

        val totalBalance = db.rawQuery("SELECT COALESCE(SUM(balance), 0) FROM wallet", null)
            .use { if (it.moveToFirst()) it.getLong(0) else 0L }

        // Balance at the end of month M = today's balance minus every transaction after M.
        val netAfter = HashMap<String, Long>()
        var laterNet = 0L
        for ((month, income, expense) in totals.asReversed()) {
            netAfter[month] = laterNet
            laterNet += income - expense
        }

        data class Saved(val month: String, val feePercent: Double, val source: String)
        val existing = db.query(
            "month_snapshot", arrayOf("month", "fee_percent", "source"),
            null, null, null, null, null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Saved(c.getString(0), c.getDouble(1), c.getString(2)))
                }
            }
        }.associateBy { it.month }

        val globalFee = feePercent()
        var changed = false
        db.beginTransaction()
        try {
            for ((month, income, expense) in totals) {
                if (!backfill && !force && month <= lastMonth) continue
                val saved = existing[month]
                if (saved != null && saved.source == Snapshot.SOURCE_MANUAL) continue
                val feePercent = saved?.feePercent ?: globalFee
                upsertSnapshot(
                    month = month,
                    totalBalance = totalBalance - (netAfter[month] ?: 0L),
                    totalIncome = income,
                    totalExpense = expense,
                    feePercent = feePercent,
                    feeExpense = Fmt.feeExpense(income, feePercent),
                    source = Snapshot.SOURCE_AUTO,
                )
                changed = true
            }

            // Auto history for months that no longer have transactions.
            for (saved in existing.values) {
                if (saved.source == Snapshot.SOURCE_AUTO &&
                    saved.month < currentMonth &&
                    totals.none { it.first == saved.month }
                ) {
                    deleteSnapshot(saved.month)
                    changed = true
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        prefs.edit().putString(KEY_LAST_AUTO_MONTH, currentMonth).apply()
        return changed
    }

    fun todayMonth(): String = Fmt.monthKey(System.currentTimeMillis())

    /** Recomputes one month from its transactions, keeping the fee already chosen for it. */
    fun refreshAutoSnapshot(month: String) {
        val key = month.trim()
        if (key.isEmpty()) throw ValidationException("Bulan wajib diisi")

        val (income, expense) = db.rawQuery(
            """
            SELECT COALESCE(SUM(CASE WHEN type = 'income'  THEN amount ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0)
            FROM trx WHERE month = ?
            """.trimIndent(),
            arrayOf(key),
        ).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else 0L to 0L }

        val totalBalance = db.rawQuery("SELECT COALESCE(SUM(balance), 0) FROM wallet", null)
            .use { if (it.moveToFirst()) it.getLong(0) else 0L }
        val netAfter = db.rawQuery(
            "SELECT COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE -amount END), 0) " +
                "FROM trx WHERE month > ?",
            arrayOf(key),
        ).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        val storedFee = db.rawQuery(
            "SELECT fee_percent FROM month_snapshot WHERE month = ?", arrayOf(key),
        ).use { if (it.moveToFirst()) it.getDouble(0) else feePercent() }

        upsertSnapshot(
            month = key,
            totalBalance = totalBalance - netAfter,
            totalIncome = income,
            totalExpense = expense,
            feePercent = storedFee,
            feeExpense = Fmt.feeExpense(income, storedFee),
            source = Snapshot.SOURCE_AUTO,
        )
    }

    fun deleteSnapshot(month: String) {
        db.delete("month_snapshot", "month = ?", arrayOf(month))
    }

    fun deleteAllSnapshots() {
        db.beginTransaction()
        try {
            db.delete("month_snapshot", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
