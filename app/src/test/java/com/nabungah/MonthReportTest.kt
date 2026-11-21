package com.nabungah

import com.nabungah.data.Trx
import com.nabungah.ui.buildMonthReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthReportTest {

    private var nextId = 1L

    private fun trx(
        title: String,
        amount: Long,
        type: String,
        month: String,
    ) = Trx(
        id = nextId++,
        title = title,
        amount = amount,
        type = type,
        walletId = 1L,
        note = "",
        occurredAt = 0L,
        month = month,
    )

    @Test
    fun derivesTotalsCountFeeAndTopSpending() {
        val transactions = listOf(
            trx("Gaji", 5_000_000, "income", "2026-10"),
            trx("Bonus", 1_000_000, "income", "2026-10"),
            trx("Makan", 300_000, "expense", "2026-10"),
            trx("Makan", 200_000, "expense", "2026-10"),
            trx("Bensin", 250_000, "expense", "2026-10"),
            trx("Listrik", 150_000, "expense", "2026-10"),
            trx("November", 999_000, "expense", "2026-11"),
        )

        val report = buildMonthReport("2026-10", transactions, totalBalance = 0, feePercent = 2.5)

        assertEquals(6_000_000L, report.income)
        assertEquals(900_000L, report.expense)
        assertEquals(5_100_000L, report.net)
        assertEquals(6, report.count)
        assertEquals(150_000L, report.feeExpense) // 2.5% of income, rounded
        assertEquals(150_000L, report.averageExpense)
        assertEquals(849_000L, report.balanceAfterFee) // 999k reconstructed balance - fee

        assertEquals(3, report.topExpenses.size)
        assertEquals("Makan", report.topExpenses[0].title)
        assertEquals(500_000L, report.topExpenses[0].total)
        assertEquals("Bensin", report.topExpenses[1].title)
        assertEquals("Listrik", report.topExpenses[2].title)
    }

    @Test
    fun reconstructsBalanceAtMonthEndFromLaterTransactions() {
        val transactions = listOf(
            trx("Awal", 1_000_000, "income", "2026-09"),
            trx("Belanja", 200_000, "expense", "2026-09"),
            trx("Gaji", 3_000_000, "income", "2026-10"),
            trx("Sewa", 1_000_000, "expense", "2026-10"),
            trx("Jajan", 500_000, "expense", "2026-11"),
        )
        // Today's balance after all five transactions.
        val today = 1_000_000L - 200_000 + 3_000_000 - 1_000_000 - 500_000

        val september = buildMonthReport("2026-09", transactions, today, 2.5)
        val october = buildMonthReport("2026-10", transactions, today, 2.5)

        // Sept ends at 800k; Oct net +2M -> 2.8M; Nov expense -> today's 2.3M.
        assertEquals(800_000L, september.balance)
        assertEquals(2_800_000L, october.balance)

        val november = buildMonthReport("2026-11", transactions, today, 2.5)
        assertEquals(today, november.balance)
    }

    @Test
    fun emptyMonthIsAllZeroes() {
        val report = buildMonthReport("2026-12", emptyList(), totalBalance = 42_000, feePercent = 2.5)

        assertEquals(0L, report.income)
        assertEquals(0L, report.expense)
        assertEquals(0, report.count)
        assertEquals(42_000L, report.balance)
        assertEquals(0L, report.feeExpense)
        assertEquals(0L, report.averageExpense)
        assertTrue(report.topExpenses.isEmpty())
    }
}
