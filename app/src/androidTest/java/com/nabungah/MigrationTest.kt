package com.nabungah

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabungah.data.Repo
import com.nabungah.ui.Fmt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("nabungah.db")
    }

    @After
    fun tearDown() {
        context.deleteDatabase("nabungah.db")
    }

    @Test
    fun v1UpgradesWithStartingBalanceAndRepairedRows() {
        val path = context.getDatabasePath("nabungah.db").path
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        db.execSQL(
            """
            CREATE TABLE wallet (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                balance INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE trx (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                amount INTEGER NOT NULL CHECK (amount > 0),
                type TEXT NOT NULL CHECK (type IN ('income','expense')),
                wallet_id INTEGER REFERENCES wallet(id) ON DELETE SET NULL,
                note TEXT NOT NULL DEFAULT '',
                occurred_at INTEGER NOT NULL,
                month TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE quick_action (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                amount INTEGER NOT NULL CHECK (amount > 0),
                type TEXT NOT NULL CHECK (type IN ('income','expense')),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE goal (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                target_amount INTEGER NOT NULL CHECK (target_amount > 0),
                saved_amount INTEGER NOT NULL DEFAULT 0 CHECK (saved_amount >= 0),
                status TEXT NOT NULL DEFAULT 'in_progress'
                       CHECK (status IN ('in_progress','completed','cancelled')),
                created_at INTEGER NOT NULL,
                updated_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE month_snapshot (
                month TEXT PRIMARY KEY,
                total_balance INTEGER NOT NULL,
                total_income INTEGER NOT NULL,
                total_expense INTEGER NOT NULL,
                fee_percent REAL NOT NULL DEFAULT 2.5,
                fee_expense INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO wallet (id,name,balance,created_at,updated_at) VALUES (1,'Cash',90000,?,?)",
            arrayOf(now, now),
        )
        db.execSQL(
            "INSERT INTO trx (id,title,amount,type,wallet_id,note,occurred_at,month) " +
                "VALUES (1,'Lama',10000,'expense',1,'',?,'1999-01')",
            arrayOf(now),
        )
        db.execSQL(
            "INSERT INTO month_snapshot VALUES ('2026-01', -10, -20, -30, 180, -5, ?)",
            arrayOf(now),
        )
        db.version = 1
        db.close()

        val repo = Repo(context)
        try {
            val loaded = repo.load()
            assertEquals(100_000L, loaded.wallets.single().startingBalance)
            assertEquals(90_000L, loaded.wallets.single().balance)
            assertEquals(Fmt.monthKey(now), loaded.transactions.single().month)
            assertEquals(0L, loaded.snapshots.single().totalBalance)
            assertEquals(0L, loaded.snapshots.single().totalIncome)
            assertEquals(100.0, loaded.snapshots.single().feePercent, 0.0001)
            assertEquals(0L, loaded.snapshots.single().feeExpense)
            assertEquals(false, repo.auditConsistency().hasIssues)
        } finally {
            repo.close()
        }
    }
}
