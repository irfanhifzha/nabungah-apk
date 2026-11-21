package com.nabungah.ui

import com.nabungah.data.Trx

/** One aggregated spending line (expense grouped by title). */
data class TitleTotal(val title: String, val total: Long)

/**
 * Everything the Review tab shows for one month, derived from transactions only.
 * Pure Kotlin: keep it free of Android and database imports so it stays unit-testable.
 */
data class MonthReport(
    val month: String,
    val income: Long,
    val expense: Long,
    val count: Int,
    /** Wallet balances at the end of this month, reconstructed from later transactions. */
    val balance: Long,
    val feePercent: Double,
    val feeExpense: Long,
    val topExpenses: List<TitleTotal>,
) {
    val net: Long get() = income - expense
    val averageExpense: Long get() = if (count == 0) 0 else expense / count

    /** Preview only: fee is informational and never actually deducted from wallets. */
    val balanceAfterFee: Long get() = balance - feeExpense
}

/**
 * Builds the report for [month] from all transactions.
 *
 * [totalBalance] is today's balance. A historical balance is reconstructed by undoing every
 * transaction that happened after the month: transfers and goals never touch the total, so the
 * only drift left is deleting a wallet (which removes its balance without a transaction).
 */
fun buildMonthReport(
    month: String,
    transactions: List<Trx>,
    totalBalance: Long,
    feePercent: Double,
): MonthReport {
    var income = 0L
    var expense = 0L
    var count = 0
    var netAfter = 0L
    val expenseByTitle = mutableMapOf<String, Long>()

    for (trx in transactions) {
        if (trx.month == month) {
            count++
            if (trx.type == "income") income += trx.amount else expense += trx.amount
            if (trx.type == "expense") {
                expenseByTitle[trx.title] = (expenseByTitle[trx.title] ?: 0L) + trx.amount
            }
        } else if (trx.month > month) {
            netAfter += if (trx.type == "income") trx.amount else -trx.amount
        }
    }

    return MonthReport(
        month = month,
        income = income,
        expense = expense,
        count = count,
        balance = totalBalance - netAfter,
        feePercent = feePercent,
        feeExpense = Fmt.feeExpense(income, feePercent),
        topExpenses = expenseByTitle
            .map { TitleTotal(it.key, it.value) }
            .sortedWith(compareByDescending<TitleTotal> { it.total }.thenBy { it.title })
            .take(3),
    )
}
