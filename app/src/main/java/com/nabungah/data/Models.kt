package com.nabungah.data

data class Wallet(
    val id: Long,
    val name: String,
    val balance: Long,
    /** Balance before any recorded transaction, used to audit [balance]. */
    val startingBalance: Long = 0,
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

/** Activity-only record of a wallet-to-wallet move. Never counted as income or expense. */
data class TransferLog(
    val id: Long,
    val amount: Long,
    val fromWalletId: Long?,
    val toWalletId: Long?,
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
    /** "auto" is maintained by the app; "manual" is frozen by the user. */
    val source: String = SOURCE_MANUAL,
) {
    val isManual: Boolean get() = source == SOURCE_MANUAL

    companion object {
        const val SOURCE_AUTO = "auto"
        const val SOURCE_MANUAL = "manual"
    }
}

data class AppData(
    val wallets: List<Wallet> = emptyList(),
    val transactions: List<Trx> = emptyList(),
    val quickActions: List<QuickAction> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val snapshots: List<Snapshot> = emptyList(),
    val transfers: List<TransferLog> = emptyList(),
    /** Saved fee percentage used for live previews; snapshots keep their own copy. */
    val feePercent: Double = 2.5,
    val currentMonth: String = "",
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val lastBackupAt: Long = 0,
    val lastBackupBytes: Long = 0,
) {
    // Computed once per loaded snapshot instead of on every row/composition read.
    val totalBalance: Long = wallets.sumOf { it.balance }
    val walletNames: Map<Long, String> = wallets.associate { it.id to it.name }
}

data class BackupPreview(
    val version: Int,
    val exportedAt: Long,
    val wallets: Int,
    val transactions: Int,
    val transfers: Int,
    val goals: Int,
    val quickActions: Int,
    val snapshots: Int,
)

data class WalletDrift(
    val name: String,
    val stored: Long,
    val expected: Long,
) {
    val difference: Long get() = stored - expected
}

data class ConsistencyReport(
    val walletDrift: List<WalletDrift> = emptyList(),
    val monthMismatches: Int = 0,
    val snapshotIssues: Int = 0,
) {
    val issueCount: Int get() = walletDrift.size + monthMismatches + snapshotIssues
    val hasIssues: Boolean get() = issueCount > 0
}
