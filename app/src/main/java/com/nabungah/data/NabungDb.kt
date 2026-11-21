package com.nabungah.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local schema. Every migration is additive and replayable from any older version:
 * v2 added snapshot sources, v3 added the transfer log, v4 added wallet starting
 * balances plus stricter CHECK constraints and repaired transaction months.
 */
class NabungDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "nabungah.db", null, 4) {

    init {
        // WAL journal mode (the platform's supported way to set it).
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE wallet (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                name             TEXT    NOT NULL,
                balance          INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
                starting_balance INTEGER NOT NULL DEFAULT 0 CHECK (starting_balance >= 0),
                created_at       INTEGER NOT NULL,
                updated_at       INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE trx (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                title       TEXT    NOT NULL,
                amount      INTEGER NOT NULL CHECK (amount > 0),
                type        TEXT    NOT NULL CHECK (type IN ('income','expense')),
                wallet_id   INTEGER REFERENCES wallet(id) ON DELETE SET NULL,
                note        TEXT    NOT NULL DEFAULT '',
                occurred_at INTEGER NOT NULL,
                month       TEXT    NOT NULL CHECK (month GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]')
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_trx_month ON trx(month)")
        db.execSQL("CREATE INDEX idx_trx_occurred ON trx(occurred_at DESC)")
        db.execSQL("CREATE INDEX idx_trx_wallet ON trx(wallet_id)")

        db.execSQL(
            """
            CREATE TABLE quick_action (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                title      TEXT    NOT NULL,
                amount     INTEGER NOT NULL CHECK (amount > 0),
                type       TEXT    NOT NULL CHECK (type IN ('income','expense')),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE goal (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                title         TEXT    NOT NULL,
                target_amount INTEGER NOT NULL CHECK (target_amount > 0),
                saved_amount  INTEGER NOT NULL DEFAULT 0 CHECK (saved_amount >= 0),
                status        TEXT    NOT NULL DEFAULT 'in_progress'
                              CHECK (status IN ('in_progress','completed','cancelled')),
                created_at    INTEGER NOT NULL,
                updated_at    INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE transfer_log (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                amount         INTEGER NOT NULL CHECK (amount > 0),
                from_wallet_id INTEGER REFERENCES wallet(id) ON DELETE SET NULL,
                to_wallet_id   INTEGER REFERENCES wallet(id) ON DELETE SET NULL,
                occurred_at    INTEGER NOT NULL,
                month          TEXT    NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_transfer_occurred ON transfer_log(occurred_at DESC)")
        db.execSQL("CREATE INDEX idx_transfer_month ON transfer_log(month)")

        db.execSQL(
            """
            CREATE TABLE month_snapshot (
                month         TEXT PRIMARY KEY,
                total_balance INTEGER NOT NULL CHECK (total_balance >= 0),
                total_income  INTEGER NOT NULL CHECK (total_income >= 0),
                total_expense INTEGER NOT NULL CHECK (total_expense >= 0),
                fee_percent   REAL    NOT NULL DEFAULT 2.5
                              CHECK (fee_percent >= 0 AND fee_percent <= 100),
                fee_expense   INTEGER NOT NULL CHECK (fee_expense >= 0),
                updated_at    INTEGER NOT NULL,
                source        TEXT    NOT NULL DEFAULT 'manual'
                              CHECK (source IN ('auto','manual'))
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v2: snapshots remember whether the app maintains them or the user froze them.
        // Every snapshot written by v1 was manual, which is the column default.
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE month_snapshot ADD COLUMN source TEXT NOT NULL DEFAULT 'manual' " +
                    "CHECK (source IN ('auto','manual'))"
            )
        }
        // v3: transfers get an activity-log entry without becoming income/expense.
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE transfer_log (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    amount         INTEGER NOT NULL CHECK (amount > 0),
                    from_wallet_id INTEGER REFERENCES wallet(id) ON DELETE SET NULL,
                    to_wallet_id   INTEGER REFERENCES wallet(id) ON DELETE SET NULL,
                    occurred_at    INTEGER NOT NULL,
                    month          TEXT    NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_transfer_occurred ON transfer_log(occurred_at DESC)")
        }
        // v4: reconstruct each wallet's starting balance from its current balance and
        // transaction history, repair any month/occurred_at disagreement, and rebuild the
        // snapshot table so invalid negative values cannot be stored again.
        if (oldVersion < 4) {
            db.execSQL(
                "ALTER TABLE wallet ADD COLUMN starting_balance INTEGER NOT NULL DEFAULT 0 " +
                    "CHECK (starting_balance >= 0)"
            )
            db.execSQL(
                """
                UPDATE wallet SET starting_balance = MAX(
                    0,
                    balance - COALESCE((
                        SELECT SUM(CASE WHEN type = 'income' THEN amount ELSE -amount END)
                        FROM trx WHERE trx.wallet_id = wallet.id
                    ), 0)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE trx SET month = strftime('%Y-%m', occurred_at / 1000, 'unixepoch', 'localtime')
                WHERE month <> strftime('%Y-%m', occurred_at / 1000, 'unixepoch', 'localtime')
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_trx_wallet ON trx(wallet_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfer_month ON transfer_log(month)")
            db.execSQL(
                """
                CREATE TABLE month_snapshot_v4 (
                    month         TEXT PRIMARY KEY,
                    total_balance INTEGER NOT NULL CHECK (total_balance >= 0),
                    total_income  INTEGER NOT NULL CHECK (total_income >= 0),
                    total_expense INTEGER NOT NULL CHECK (total_expense >= 0),
                    fee_percent   REAL    NOT NULL DEFAULT 2.5
                                  CHECK (fee_percent >= 0 AND fee_percent <= 100),
                    fee_expense   INTEGER NOT NULL CHECK (fee_expense >= 0),
                    updated_at    INTEGER NOT NULL,
                    source        TEXT    NOT NULL DEFAULT 'manual'
                                  CHECK (source IN ('auto','manual'))
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO month_snapshot_v4
                    (month, total_balance, total_income, total_expense,
                     fee_percent, fee_expense, updated_at, source)
                SELECT month,
                       MAX(0, total_balance),
                       MAX(0, total_income),
                       MAX(0, total_expense),
                       MIN(100, MAX(0, fee_percent)),
                       MAX(0, fee_expense),
                       updated_at,
                       CASE WHEN source IN ('auto','manual') THEN source ELSE 'manual' END
                FROM month_snapshot
                """.trimIndent()
            )
            db.execSQL("DROP TABLE month_snapshot")
            db.execSQL("ALTER TABLE month_snapshot_v4 RENAME TO month_snapshot")
        }
    }
}
