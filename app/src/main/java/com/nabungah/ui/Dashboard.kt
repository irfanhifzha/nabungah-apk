package com.nabungah.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nabungah.data.AppData
import com.nabungah.data.Goal
import com.nabungah.data.QuickAction
import com.nabungah.data.Snapshot
import com.nabungah.data.Trx
import com.nabungah.data.Wallet
import kotlin.math.roundToInt

sealed interface Sheet {
    data object AddWallet : Sheet
    data class NewTransaction(val prefill: QuickAction? = null) : Sheet
    data object AddQuickAction : Sheet
    data object AddGoal : Sheet
    data class EditGoal(val goal: Goal) : Sheet
    data object Transfer : Sheet
    data object Sync : Sheet
    data object DeleteWallets : Sheet
    data object DeleteTransactions : Sheet
    data object DeleteQuickActions : Sheet
    data object DeleteSnapshots : Sheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(vm: MainViewModel, modifier: Modifier = Modifier) {
    val data = vm.data

    var monthFilter by remember { mutableStateOf<String?>(null) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var limit by remember { mutableIntStateOf(10) }
    var feeDraft by remember { mutableStateOf("2,5") }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
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

    val months = remember(data.transactions) {
        data.transactions.map { it.month }.distinct().sortedDescending()
    }
    LaunchedEffect(months) {
        if (monthFilter != null && monthFilter !in months) monthFilter = null
    }

    val filtered = remember(data.transactions, monthFilter, typeFilter) {
        data.transactions.filter { trx ->
            (monthFilter == null || trx.month == monthFilter) &&
                (typeFilter == null || trx.type == typeFilter)
        }
    }
    val displayed = remember(filtered, limit) { filtered.take(limit) }

    if (!vm.loaded) {
        // Keep the first frame as cheap as possible: the window background is already
        // the app canvas, so no spinner or Material composition is needed while the
        // first database read completes off the main thread.
        Box(modifier.fillMaxSize().background(CanvasBg))
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 2.dp)) {
                Text(
                    text = "Nabungah",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Your money, where it sits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
        }

        item {
            WalletsCard(
                wallets = data.wallets,
                onAdd = { sheet = Sheet.AddWallet },
                onTransfer = { sheet = Sheet.Transfer },
                onDelete = { sheet = Sheet.DeleteWallets },
            )
        }

        item {
            GoalsCard(
                goals = data.goals,
                onAdd = { sheet = Sheet.AddGoal },
                onEdit = { sheet = Sheet.EditGoal(it) },
            )
        }

        item {
            QuickActionsCard(
                actions = data.quickActions,
                onAdd = { sheet = Sheet.AddQuickAction },
                onDelete = { sheet = Sheet.DeleteQuickActions },
                onPick = { sheet = Sheet.NewTransaction(it) },
            )
        }

        item {
            TransactionsHeaderCard(
                displayed = displayed.size,
                filtered = filtered.size,
                months = months,
                monthFilter = monthFilter,
                typeFilter = typeFilter,
                limit = limit,
                onMonth = { monthFilter = it },
                onType = { typeFilter = it },
                onLimit = { limit = it },
                onAdd = { sheet = Sheet.NewTransaction() },
                onDelete = { sheet = Sheet.DeleteTransactions },
            )
        }

        if (displayed.isEmpty()) {
            item { EmptyCard("No transactions yet") }
        } else {
            items(displayed, key = { it.id }) { trx ->
                TransactionRow(
                    trx = trx,
                    walletName = data.walletNames[trx.walletId] ?: "Unknown Wallet",
                )
            }
        }

        item {
            MonthlyOverviewCard(
                data = data,
                feeDraft = feeDraft,
                onFeeDraft = { feeDraft = Fmt.sanitizeFeeInput(it) },
                onSync = { sheet = Sheet.Sync },
            )
        }

        item {
            HistoryHeaderCard(onDelete = { sheet = Sheet.DeleteSnapshots })
        }

        if (data.snapshots.isEmpty()) {
            item { EmptyCard("No monthly snapshots yet") }
        } else {
            items(data.snapshots, key = { "snap-${it.month}" }) { snapshot ->
                SnapshotRow(snapshot)
            }
        }

        item {
            BackupCard(
                message = backupMessage,
                isError = backupFailed,
                onExport = { exportLauncher.launch(Fmt.backupFileName(System.currentTimeMillis())) },
                onImport = { confirmRestore = true },
            )
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

    val current = sheet
    if (current != null) {
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
        ) {
            when (current) {
                is Sheet.AddWallet -> AddWalletSheet(vm) { sheet = null }
                is Sheet.NewTransaction -> NewTransactionSheet(
                    vm = vm,
                    wallets = data.wallets,
                    prefill = current.prefill,
                    onClose = { sheet = null },
                )
                is Sheet.AddQuickAction -> AddQuickActionSheet(vm) { sheet = null }
                is Sheet.AddGoal -> AddGoalSheet(vm) { sheet = null }
                is Sheet.EditGoal -> EditGoalSheet(vm, current.goal) { sheet = null }
                is Sheet.Transfer -> TransferSheet(vm, data.wallets) { sheet = null }
                is Sheet.Sync -> SyncSheet(vm, feeDraft) { sheet = null }
                is Sheet.DeleteWallets -> DeleteWalletsSheet(vm, data.wallets) { sheet = null }
                is Sheet.DeleteTransactions -> DeleteTransactionsSheet(
                    vm = vm,
                    transactions = data.transactions,
                    walletNames = data.walletNames,
                    onClose = { sheet = null },
                )
                is Sheet.DeleteQuickActions -> DeleteQuickActionsSheet(vm, data.quickActions) { sheet = null }
                is Sheet.DeleteSnapshots -> DeleteSnapshotsSheet(vm, data.snapshots) { sheet = null }
            }
        }
    }
}

// ------------------------------------------------------------------ wallets

@Composable
private fun WalletsCard(
    wallets: List<Wallet>,
    onAdd: () -> Unit,
    onTransfer: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(
        title = "Wallets",
        actions = {
            CardAction("+ Add", onAdd)
            CardAction("Delete", onDelete, color = MaterialTheme.colorScheme.error)
        },
    ) {
        if (wallets.isEmpty()) {
            EmptyText("No wallet yet")
        } else {
            wallets.forEachIndexed { index, wallet ->
                if (index > 0) HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = Fmt.rp(wallet.balance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) {
                Text("Tukar Uang")
            }
        }
    }
}

// -------------------------------------------------------------------- goals

@Composable
private fun GoalsCard(goals: List<Goal>, onAdd: () -> Unit, onEdit: (Goal) -> Unit) {
    SectionCard(title = "Nabung", actions = { CardAction("+ Add", onAdd) }) {
        if (goals.isEmpty()) {
            EmptyText("No goals yet")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                goals.forEach { goal -> GoalCard(goal, onEdit) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Goals are not connected to wallets. Move the money manually.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

@Composable
private fun GoalCard(goal: Goal, onEdit: (Goal) -> Unit) {
    val statusColor = when (goal.status) {
        "completed" -> IncomeGreen
        "cancelled" -> ExpenseRed
        else -> StatusOrange
    }
    val rawProgress = if (goal.targetAmount <= 0) 0f
    else (goal.savedAmount.toFloat() / goal.targetAmount).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 500),
        label = "goalProgress",
    )
    val percent = (rawProgress * 100).roundToInt().coerceAtMost(100)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onEdit(goal) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = goal.status.replace('_', ' ').replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = statusColor,
            trackColor = CardBorder,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Saved ${Fmt.rp(goal.savedAmount)}",
                style = MaterialTheme.typography.labelMedium,
                color = IncomeGreen,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Target ${Fmt.rp(goal.targetAmount)}",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
        }
    }
}

// ------------------------------------------------------------ quick actions

@Composable
private fun QuickActionsCard(
    actions: List<QuickAction>,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    onPick: (QuickAction) -> Unit,
) {
    SectionCard(
        title = "Quick Actions",
        actions = {
            CardAction("+ Add", onAdd)
            CardAction("Delete", onDelete, color = MaterialTheme.colorScheme.error)
        },
    ) {
        if (actions.isEmpty()) {
            EmptyText("No Quick Actions yet")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.chunked(2).forEach { rowActions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowActions.forEach { action ->
                            QuickActionCard(
                                action = action,
                                modifier = Modifier.weight(1f),
                                onClick = { onPick(action) },
                            )
                        }
                        if (rowActions.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(action: QuickAction, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = action.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = (if (action.type == "income") "+ " else "- ") + Fmt.rp(action.amount),
            style = MaterialTheme.typography.bodySmall,
            color = if (action.type == "income") IncomeGreen else ExpenseRed,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ------------------------------------------------------------- transactions

@Composable
private fun TransactionsHeaderCard(
    displayed: Int,
    filtered: Int,
    months: List<String>,
    monthFilter: String?,
    typeFilter: String?,
    limit: Int,
    onMonth: (String?) -> Unit,
    onType: (String?) -> Unit,
    onLimit: (Int) -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(
        title = "Recent Transactions",
        actions = {
            CardAction("+ Add", onAdd)
            CardAction("Delete", onDelete, color = MaterialTheme.colorScheme.error)
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerField(
                label = "Month",
                selectedLabel = monthFilter ?: "All Months",
                options = listOf<Pair<String?, String>>(null to "All Months") +
                    months.map { it to it },
                onSelect = onMonth,
                modifier = Modifier.weight(1f),
            )
            PickerField(
                label = "Type",
                selectedLabel = when (typeFilter) {
                    "income" -> "Income"
                    "expense" -> "Expense"
                    else -> "All Types"
                },
                options = listOf(
                    null to "All Types",
                    "income" to "Income",
                    "expense" to "Expense",
                ),
                onSelect = onType,
                modifier = Modifier.weight(1f),
            )
            PickerField(
                label = "Limit",
                selectedLabel = limit.toString(),
                options = listOf(10, 20, 50, 100).map { it to it.toString() },
                onSelect = onLimit,
                modifier = Modifier.weight(0.8f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Showing $displayed of $filtered transactions",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

@Composable
private fun TransactionRow(trx: Trx, walletName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = trx.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${Fmt.timestamp(trx.occurredAt)} · $walletName",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                if (trx.note.isNotBlank()) {
                    Text(
                        text = trx.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = (if (trx.type == "income") "+ " else "- ") + Fmt.rp(trx.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (trx.type == "income") IncomeGreen else ExpenseRed,
                modifier = Modifier.padding(top = 2.dp, start = 6.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            CopyButton(text = trxCopyText(trx, walletName))
        }
    }
}

// --------------------------------------------------------- monthly overview

@Composable
private fun MonthlyOverviewCard(
    data: AppData,
    feeDraft: String,
    onFeeDraft: (String) -> Unit,
    onSync: () -> Unit,
) {
    SectionCard(title = "Monthly Overview") {
        Text(
            text = data.currentMonth,
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
        )
        Spacer(Modifier.height(6.dp))
        Text("Total Balance", style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(
            text = Fmt.rp(data.totalBalance),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = CardBorder)
        Spacer(Modifier.height(10.dp))
        AmountRow("Total Income", data.monthIncome, IncomeGreen, prefix = "+ ")
        AmountRow("Total Expense", data.monthExpense, ExpenseRed, prefix = "- ")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Fee %", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Informational only, never deducted",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
            OutlinedTextField(
                value = feeDraft,
                onValueChange = onFeeDraft,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(100.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        AmountRow(
            label = "Fee Expense",
            amount = Fmt.feeExpense(data.monthIncome, Fmt.parseFee(feeDraft)),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
            Text("Sync Data")
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Long, color: Color, prefix: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = prefix + Fmt.rp(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

// ------------------------------------------------------------------ history

@Composable
private fun HistoryHeaderCard(onDelete: () -> Unit) {
    SectionCard(
        title = "Monthly History",
        actions = { CardAction("Delete", onDelete, color = MaterialTheme.colorScheme.error) },
    ) {
        Text(
            text = "Snapshots saved from the current month and earlier.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

@Composable
private fun SnapshotRow(snapshot: Snapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = snapshot.month,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            CopyButton(text = snapshotCopyText(snapshot))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            AmountRow("Income", snapshot.totalIncome, IncomeGreen, prefix = "+ ")
            AmountRow("Expense", snapshot.totalExpense, ExpenseRed, prefix = "- ")
            AmountRow("Balance", snapshot.totalBalance, MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Fee ${Fmt.feePercentLabel(snapshot.feePercent)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Fmt.rp(snapshot.feeExpense),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Updated ${Fmt.timestamp(snapshot.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

@Composable
private fun BackupCard(
    message: String?,
    isError: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    SectionCard(title = "Backup") {
        Text(
            text = "Export one JSON file with all data. Importing replaces everything currently in the app.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                Text("Export Backup")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Text("Import Backup")
            }
        }
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else IncomeGreen,
            )
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}
