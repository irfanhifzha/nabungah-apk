package com.nabungah.data

data class Wallet(
    val id: Long,
    val name: String,
    val balance: Long,
)

data class Trx(
    val id: Long,
    val title: String,
    val amount: Long,
    val type: String,
    val walletId: Long?,
    val note: String,
    val occurredAt: Long,
    val month: String,
)

data class QuickAction(
    val id: Long,
    val title: String,
    val amount: Long,
    val type: String,
)

data class Goal(
    val id: Long,
    val title: String,
    val targetAmount: Long,
    val savedAmount: Long,
    val status: String,
)

data class Snapshot(
    val month: String,
    val totalBalance: Long,
    val totalIncome: Long,
    val totalExpense: Long,
    val feePercent: Double,
    val feeExpense: Long,
    val updatedAt: Long,
)

data class AppData(
    val wallets: List<Wallet> = emptyList(),
    val transactions: List<Trx> = emptyList(),
    val quickActions: List<QuickAction> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val snapshots: List<Snapshot> = emptyList(),
    val currentMonth: String = "",
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
) {
    // Computed once per loaded snapshot instead of on every row/composition read.
    val totalBalance: Long = wallets.sumOf { it.balance }
    val walletNames: Map<Long, String> = wallets.associate { it.id to it.name }
}
