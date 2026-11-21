package com.nabungah.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nabungah.BuildConfig
import com.nabungah.data.AppData
import com.nabungah.data.Goal
import com.nabungah.data.QuickAction
import com.nabungah.data.Wallet

/** Settings: backup, wallet and quick-action setup, and all destructive tools. */
@Composable
fun SettingsTab(vm: MainViewModel, data: AppData, onSheet: (Sheet) -> Unit) {
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupFailed by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            vm.exportBackup(
                uri = uri,
                onError = { backupMessage = it; backupFailed = true },
                onDone = { backupMessage = "Backup exported."; backupFailed = false },
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.importBackup(
                uri = uri,
                onError = { backupMessage = it; backupFailed = true },
                onDone = { backupMessage = "Backup restored."; backupFailed = false },
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListBottomPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Everything lives on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }

        item {
            SectionCard(title = "Backup & restore") {
                Text(
                    text = "Export one JSON file with all data. Importing replaces everything currently in the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch(Fmt.backupFileName(System.currentTimeMillis())) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Export") }
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("Import") }
                }
                if (backupMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = backupMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backupFailed) MaterialTheme.colorScheme.error else IncomeGreen,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Wallets",
                actions = { CardAction("+ Add", { onSheet(Sheet.AddWallet) }) },
            ) {
                if (data.wallets.isEmpty()) {
                    EmptyText("No wallets yet")
                } else {
                    data.wallets.forEach { wallet -> WalletRow(wallet) }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onSheet(Sheet.Transfer) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = data.wallets.size > 1,
                    ) { Text("Transfer between wallets") }
                }
            }
        }

        item {
            SectionCard(
                title = "Quick actions",
                actions = { CardAction("+ New", { onSheet(Sheet.AddQuickAction) }) },
            ) {
                if (data.quickActions.isEmpty()) {
                    EmptyText("No quick actions yet")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        data.quickActions.forEach { action -> QuickActionRow(action) }
                    }
                    TextButton(onClick = { onSheet(Sheet.DeleteQuickActions) }) {
                        Text("Delete quick actions", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Savings goals",
                actions = { CardAction("+ Add", { onSheet(Sheet.AddGoal) }) },
            ) {
                if (data.goals.isEmpty()) {
                    EmptyText("No goals yet")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        data.goals.forEach { goal -> GoalRow(goal) { onSheet(Sheet.EditGoal(goal)) } }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Data & reset") {
                Text(
                    text = "These remove records permanently. Export a backup first if you are unsure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                DangerAction("Delete transactions", data.transactions.size) {
                    onSheet(Sheet.DeleteTransactions)
                }
                DangerAction("Delete wallets", data.wallets.size) { onSheet(Sheet.DeleteWallets) }
                DangerAction("Delete monthly snapshots", data.snapshots.size) {
                    onSheet(Sheet.DeleteSnapshots)
                }
            }
        }

        item {
            SectionCard(title = "About") {
                Text(
                    text = "Nabungah ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Single user, fully offline. No accounts, no network access, no analytics. " +
                        "Records sit in a local SQLite database and only leave the device through a backup you export.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "Restore backup?",
            message = "This replaces all wallets, transactions, quick actions, goals and snapshots with the backup file. It cannot be undone.",
            confirmLabel = "Choose File",
            destructive = false,
            onConfirm = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
            onDismiss = { confirmRestore = false },
        )
    }
}

@Composable
private fun WalletRow(wallet: Wallet) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(SoftTeal),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = wallet.name.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = wallet.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Fmt.rp(wallet.balance),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QuickActionRow(action: QuickAction) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = action.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = (if (action.type == "income") "+ " else "- ") + Fmt.rp(action.amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (action.type == "income") IncomeGreen else ExpenseRed,
        )
    }
}

@Composable
private fun GoalRow(goal: Goal, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick) {
            Text(goal.title, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "${Fmt.rp(goal.savedAmount)} / ${Fmt.rp(goal.targetAmount)}",
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
        )
    }
}

@Composable
private fun DangerAction(label: String, count: Int, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text("$count", color = Muted)
    }
}

/** Explicit white used by the sheet host; kept here so there is one card colour. */
private val CardWhite = Color.White
