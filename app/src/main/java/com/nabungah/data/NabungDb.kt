package com.nabungah.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NabungDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "nabungah.db", null, 3) {

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
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                name       TEXT    NOT NULL,
                balance    INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
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
                month       TEXT    NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_trx_month ON trx(month)")
        db.execSQL("CREATE INDEX idx_trx_occurred ON trx(occurred_at DESC)")

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

        db.execSQL(
            """
            CREATE TABLE month_snapshot (
                month         TEXT PRIMARY KEY,
                total_balance INTEGER NOT NULL,
                total_income  INTEGER NOT NULL,
                total_expense INTEGER NOT NULL,
                fee_percent   REAL    NOT NULL DEFAULT 2.5,
                fee_expense   INTEGER NOT NULL,
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
    }
}
