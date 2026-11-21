package com.nabungah

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabungah.data.Repo
import com.nabungah.data.Snapshot
import com.nabungah.data.ValidationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepoTest {

    private lateinit var context: Context
    private lateinit var repo: Repo

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("nabungah.db")
        repo = Repo(context)
    }

    @After
    fun tearDown() {
        repo.close()
        context.deleteDatabase("nabungah.db")
    }

    private fun walletId(name: String): Long =
        repo.load().wallets.first { it.name == name }.id

    @Test
    fun expenseUpdatesBalanceAndIsRejectedWhenInsufficient() {
        repo.addWallet("Cash", 50_000)
        val id = walletId("Cash")

        repo.addTransaction("Kopi", 20_000, "expense", id, "pagi")
        assertEquals(30_000L, repo.load().wallets.single().balance)
        assertEquals(1, repo.load().transactions.size)

        try {
            repo.addTransaction("Gadget", 100_000, "expense", id, "")
            fail("Expected insufficient balance")
        } catch (e: ValidationException) {
            assertEquals("Saldo dompet tidak cukup", e.message)
        }
        // Nothing was written by the failed attempt.
        assertEquals(1, repo.load().transactions.size)
        assertEquals(30_000L, repo.load().wallets.single().balance)
    }

    @Test
    fun incomeAndExpenseRoundTripThroughDelete() {
        repo.addWallet("Cash", 100_000)
        val id = walletId("Cash")

        repo.addTransaction("Gaji", 50_000, "income", id, "")
        assertEquals(150_000L, repo.load().wallets.single().balance)
        val income = repo.load().transactions.single()
        repo.deleteTransaction(income.id)
        assertEquals(100_000L, repo.load().wallets.single().balance)

        repo.addTransaction("Makan", 40_000, "expense", id, "")
        assertEquals(60_000L, repo.load().wallets.single().balance)
        val expense = repo.load().transactions.single()
        repo.deleteTransaction(expense.id)
        assertEquals(100_000L, repo.load().wallets.single().balance)
        assertTrue(repo.load().transactions.isEmpty())
    }

    @Test
    fun deleteAllTransactionsReversesEveryEffect() {
        repo.addWallet("Cash", 100_000)
        repo.addWallet("Bank", 0)
        val cash = walletId("Cash")
        val bank = walletId("Bank")
        repo.addTransaction("Gaji", 80_000, "income", cash, "")
        repo.addTransaction("Jajan", 25_000, "expense", cash, "")
        repo.addTransaction("Bunga", 5_000, "income", bank, "")

        repo.deleteAllTransactions()

        val loaded = repo.load()
        assertTrue(loaded.transactions.isEmpty())
        assertEquals(100_000L, loaded.wallets.first { it.name == "Cash" }.balance)
        assertEquals(0L, loaded.wallets.first { it.name == "Bank" }.balance)
    }

    @Test
    fun transactionValidationMessages() {
        repo.addWallet("Cash", 10_000)
        val id = walletId("Cash")
        assertEquals(
            "Judul wajib diisi",
            runCatching { repo.addTransaction("   ", 1_000, "expense", id, "") }
                .exceptionOrNull()?.message,
        )
        assertEquals(
            "Nominal harus lebih dari 0",
            runCatching { repo.addTransaction("X", 0, "expense", id, "") }
                .exceptionOrNull()?.message,
        )
        assertEquals(
            "Pilih dompet",
            runCatching { repo.addTransaction("X", 1_000, "expense", null, "") }
                .exceptionOrNull()?.message,
        )
        assertEquals(10_000L, repo.load().wallets.single().balance)
    }

    @Test
    fun transferMovesMoneyWithoutCreatingRecords() {
        repo.addWallet("Cash", 100_000)
        repo.addWallet("Bank", 0)
        val cash = walletId("Cash")
        val bank = walletId("Bank")

        repo.transfer(50_000, cash, bank)

        val loaded = repo.load()
        assertEquals(50_000L, loaded.wallets.first { it.name == "Cash" }.balance)
        assertEquals(50_000L, loaded.wallets.first { it.name == "Bank" }.balance)
        assertEquals(100_000L, loaded.totalBalance)
        assertTrue(loaded.transactions.isEmpty())

        assertEquals(
            "Nominal harus lebih dari 1.000",
            runCatching { repo.transfer(1_000, cash, bank) }.exceptionOrNull()?.message,
        )
        assertEquals(
            "Dompet asal dan tujuan tidak boleh sama",
            runCatching { repo.transfer(50_000, cash, cash) }.exceptionOrNull()?.message,
        )
        assertEquals(
            "Saldo dompet tidak cukup",
            runCatching { repo.transfer(500_000, cash, bank) }.exceptionOrNull()?.message,
        )
        // Failed transfers left balances untouched.
        assertEquals(50_000L, repo.load().wallets.first { it.name == "Cash" }.balance)
        assertEquals(50_000L, repo.load().wallets.first { it.name == "Bank" }.balance)
    }

    @Test
    fun deletingWalletKeepsHistoryAsOrphan() {
        repo.addWallet("Cash", 100_000)
        val id = walletId("Cash")
        repo.addTransaction("Kopi", 20_000, "expense", id, "")

        repo.deleteWallet(id)

        val loaded = repo.load()
        assertTrue(loaded.wallets.isEmpty())
        assertEquals(1, loaded.transactions.size)
        assertNull(loaded.transactions.single().walletId)
    }

    @Test
    fun goalsNeverTouchWalletBalances() {
        repo.addWallet("Cash", 5_000)
        repo.addGoal("Laptop", 1_000_000)

        var goal = repo.load().goals.single()
        assertEquals(0L, goal.savedAmount)
        assertEquals("in_progress", goal.status)
        assertEquals(5_000L, repo.load().wallets.single().balance)

        repo.updateGoal(goal.id, "Laptop", 1_000_000, 250_000, "completed")
        goal = repo.load().goals.single()
        assertEquals(250_000L, goal.savedAmount)
        assertEquals("completed", goal.status)
        assertEquals(5_000L, repo.load().wallets.single().balance)
        assertTrue(repo.load().transactions.isEmpty())
    }

    @Test
    fun snapshotUpsertsByMonth() {
        repo.upsertSnapshot("2026-11", 1_000, 2_000, 500, 2.5, 50)
        repo.upsertSnapshot("2026-11", 9_000, 8_000, 100, 2.5, 200)
        repo.upsertSnapshot("2026-10", 100, 200, 300, 2.5, 5)

        val snapshots = repo.load().snapshots
        assertEquals(2, snapshots.size)
        assertEquals("2026-11", snapshots[0].month)
        assertEquals(9_000L, snapshots[0].totalBalance)
        assertEquals(8_000L, snapshots[0].totalIncome)
        assertEquals(200L, snapshots[0].feeExpense)
        assertTrue(snapshots[0].isManual) // upserts without a source are manual by default

        repo.deleteSnapshot("2026-11")
        assertEquals(1, repo.load().snapshots.size)
        repo.deleteAllSnapshots()
        assertTrue(repo.load().snapshots.isEmpty())
    }

    @Test
    fun backupRoundTripRestoresEveryTable() {
        repo.addWallet("Cash", 100_000)
        val cash = walletId("Cash")
        repo.addTransaction("Kopi", 20_000, "expense", cash, "pagi")
        repo.addQuickAction("Makan", 15_000, "expense")
        repo.addGoal("Laptop", 5_000_000)
        repo.updateGoal(repo.load().goals.single().id, "Laptop", 5_000_000, 500_000, "in_progress")
        repo.upsertSnapshot("2026-11", 80_000, 0, 20_000, 2.5, 0)

        val json = repo.exportJson()

        // Wipe everything the way a user might before restoring.
        repo.deleteAllTransactions()
        repo.deleteAllWallets()
        repo.deleteAllQuickActions()
        repo.load().goals.forEach { repo.deleteGoal(it.id) }
        repo.deleteAllSnapshots()
        assertTrue(repo.load().wallets.isEmpty())
        assertTrue(repo.load().transactions.isEmpty())

        repo.importJson(json)

        val restored = repo.load()
        assertEquals(1, restored.wallets.size)
        assertEquals("Cash", restored.wallets.single().name)
        assertEquals(80_000L, restored.wallets.single().balance)

        val trx = restored.transactions.single()
        assertEquals("Kopi", trx.title)
        assertEquals(20_000L, trx.amount)
        assertEquals("expense", trx.type)
        assertEquals("pagi", trx.note)
        assertEquals(restored.wallets.single().id, trx.walletId)

        assertEquals("Makan", restored.quickActions.single().title)
        assertEquals(500_000L, restored.goals.single().savedAmount)
        assertEquals(5_000_000L, restored.goals.single().targetAmount)
        assertEquals(80_000L, restored.snapshots.single().totalBalance)
        assertEquals(2.5, restored.snapshots.single().feePercent, 0.0001)
        assertTrue(restored.snapshots.single().isManual)
    }

    @Test
    fun autoSnapshotsRoundTripThroughBackup() {
        repo.upsertSnapshot("2026-09", 10_000, 5_000, 1_000, 2.5, 125, Snapshot.SOURCE_AUTO)

        val restored = run {
            val json = repo.exportJson()
            repo.deleteAllSnapshots()
            repo.importJson(json)
            repo.load().snapshots.single()
        }

        assertEquals(Snapshot.SOURCE_AUTO, restored.source)
        assertEquals(5_000L, restored.totalIncome)
    }

    @Test
    fun autoSyncNeverOverwritesManualSnapshots() {
        repo.upsertSnapshot("2026-01", 1, 2, 3, 2.5, 4, Snapshot.SOURCE_MANUAL)

        repo.syncAutoSnapshots(force = true)

        val saved = repo.load().snapshots.single()
        assertEquals("2026-01", saved.month)
        assertTrue(saved.isManual)
        assertEquals(1L, saved.totalBalance)
        assertEquals(3L, saved.totalExpense)
    }

    @Test
    fun invalidBackupsAreRejectedWithoutWipingData() {
        repo.addWallet("Cash", 5_000)

        listOf(
            "not json",
            """{"app":"other","version":1}""",
            """{"app":"nabungah","version":99}""",
            """{"app":"nabungah","version":1,"wallets":[{"id":1}]}""",
        ).forEach { text ->
            try {
                repo.importJson(text)
                fail("Expected invalid backup rejection")
            } catch (e: ValidationException) {
                // expected
            }
            assertEquals(1, repo.load().wallets.size)
            assertEquals(5_000L, repo.load().wallets.single().balance)
        }
    }

    @Test
    fun constraintInvalidBackupRollsBack() {
        repo.addWallet("Cash", 5_000)
        val bad = """{"app":"nabungah","version":1,"wallets":[
            {"id":1,"name":"Bad","balance":-5,"created_at":0,"updated_at":0}
        ]}"""

        try {
            repo.importJson(bad)
            fail("Expected constraint rejection")
        } catch (e: ValidationException) {
            // expected
        }

        val loaded = repo.load()
        assertEquals(1, loaded.wallets.size)
        assertEquals("Cash", loaded.wallets.single().name)
        assertEquals(5_000L, loaded.wallets.single().balance)
    }

    @Test
    fun quickActionIsOnlyATemplate() {
        repo.addWallet("Cash", 10_000)
        repo.addQuickAction("Kopi", 18_000, "expense")

        val loaded = repo.load()
        assertEquals(1, loaded.quickActions.size)
        assertTrue(loaded.transactions.isEmpty())
        assertEquals(10_000L, loaded.wallets.single().balance)
    }
}
