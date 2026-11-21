package com.nabungah.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nabungah.data.Goal
import com.nabungah.data.QuickAction
import com.nabungah.data.Snapshot
import com.nabungah.data.Trx
import com.nabungah.data.Wallet
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- clipboard

fun trxCopyText(t: Trx, walletName: String): String = buildString {
    append("*🧾 Transaksi Title: ${t.title}*\n")
    append("💰 Amount: ${Fmt.rp(t.amount)}\n")
    append("📌 Type: ${t.type}\n")
    append("🏦 Wallet: $walletName\n")
    append("🗓️ Date: ${Fmt.timestamp(t.occurredAt)}\n")
    append("📝 Note: ${t.note.ifBlank { "-" }}")
}

fun snapshotCopyText(s: Snapshot): String = buildString {
    append("*📅 Data Statistik Bulan: ${s.month}*\n")
    append("💰 Total Income: ${Fmt.rp(s.totalIncome)}\n")
    append("💸 Total Expense: ${Fmt.rp(s.totalExpense)}\n")
    append("💼 Total Balance: ${Fmt.rp(s.totalBalance)}\n")
    append("💳 Fee (${Fmt.feePercentLabel(s.feePercent)}%): ${Fmt.rp(s.feeExpense)}\n")
    append("🕒 Updated: ${Fmt.timestamp(s.updatedAt)}")
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
fun CopyButton(text: String, modifier: Modifier = Modifier) {
    var copied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    TextButton(
        onClick = {
            copyToClipboard(context, "Nabungah", text)
            copied = true
        },
        modifier = modifier,
    ) {
        Text(
            text = if (copied) "✓ Copied" else "Copy",
            style = MaterialTheme.typography.labelMedium,
            color = if (copied) IncomeGreen else Muted,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// -------------------------------------------------------------- sheet parts

@Composable
private fun SheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SheetNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Muted)
}

@Composable
private fun TypeSelector(type: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = type == "expense",
            onClick = { onChange("expense") },
            label = { Text("Expense (-)") },
        )
        FilterChip(
            selected = type == "income",
            onClick = { onChange("income") },
            label = { Text("Income (+)") },
        )
    }
}

@Composable
private fun SaveButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(label)
    }
}

// -------------------------------------------------------------- add wallet

@Composable
fun AddWalletSheet(vm: MainViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Add Wallet")
        SheetNote("Enter the balance this wallet holds right now. New wallets do not start at 0.")
        LabeledTextField(value = name, onValueChange = { name = it }, label = "Wallet Name")
        MoneyField(label = "Starting Balance", digits = balance, onDigitsChange = { balance = it })
        FormError(error)
        SaveButton("Save Wallet") {
            vm.addWallet(
                name = name,
                balance = Fmt.digitsToLong(balance),
                onError = { error = it },
                onDone = onClose,
            )
        }
    }
}

// --------------------------------------------------------- new transaction

@Composable
fun NewTransactionSheet(
    vm: MainViewModel,
    wallets: List<Wallet>,
    prefill: QuickAction?,
    onClose: () -> Unit,
) {
    var title by remember { mutableStateOf(prefill?.title ?: "") }
    var amount by remember { mutableStateOf(prefill?.amount?.toString() ?: "") }
    var type by remember { mutableStateOf(prefill?.type ?: "expense") }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("New Transaction")
        LabeledTextField(value = title, onValueChange = { title = it }, label = "Title")
        MoneyField(label = "Amount", digits = amount, onDigitsChange = { amount = it })
        TypeSelector(type = type, onChange = { type = it })
        PickerField(
            label = "Wallet",
            selectedLabel = wallets.firstOrNull { it.id == walletId }?.name ?: "",
            options = wallets.map { it.id to it.name },
            onSelect = { walletId = it },
        )
        LabeledTextField(
            value = note,
            onValueChange = { note = it },
            label = "Note (optional)",
            singleLine = false,
            minLines = 2,
        )
        FormError(error)
        SaveButton("Save Transaction") {
            vm.addTransaction(
                title = title,
                amount = Fmt.digitsToLong(amount),
                type = type,
                walletId = walletId,
                note = note,
                onError = { error = it },
                onDone = onClose,
            )
        }
    }
}

// ------------------------------------------------------- new quick action

@Composable
fun AddQuickActionSheet(vm: MainViewModel, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("expense") }
    var error by remember { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("New Quick Action")
        SheetNote("A template for the transaction form. It never moves money by itself.")
        LabeledTextField(value = title, onValueChange = { title = it }, label = "Title")
        MoneyField(label = "Amount", digits = amount, onDigitsChange = { amount = it })
        TypeSelector(type = type, onChange = { type = it })
        FormError(error)
        SaveButton("Save Quick Action") {
            vm.addQuickAction(
                title = title,
                amount = Fmt.digitsToLong(amount),
                type = type,
                onError = { error = it },
                onDone = onClose,
            )
        }
    }
}

// ------------------------------------------------------------------ goals

@Composable
fun AddGoalSheet(vm: MainViewModel, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Create Goal")
        SheetNote("Goals are not connected to wallets. Move the money manually.")
        LabeledTextField(value = title, onValueChange = { title = it }, label = "Goal Title")
        MoneyField(label = "Target Amount", digits = target, onDigitsChange = { target = it })
        FormError(error)
        SaveButton("Create Goal") {
            vm.addGoal(
                title = title,
                target = Fmt.digitsToLong(target),
                onError = { error = it },
                onDone = onClose,
            )
        }
    }
}

@Composable
fun EditGoalSheet(vm: MainViewModel, goal: Goal, onClose: () -> Unit) {
    var title by remember { mutableStateOf(goal.title) }
    var target by remember { mutableStateOf(goal.targetAmount.toString()) }
    var saved by remember { mutableStateOf(goal.savedAmount.toString()) }
    var status by remember { mutableStateOf(goal.status) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    SheetColumn {
        SheetTitle("Edit Goal")
        LabeledTextField(value = title, onValueChange = { title = it }, label = "Goal Title")
        MoneyField(label = "Target Amount", digits = target, onDigitsChange = { target = it })
        MoneyField(label = "Saved Amount", digits = saved, onDigitsChange = { saved = it })
        Text("Status", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "in_progress" to "In Progress",
                "completed" to "Completed",
                "cancelled" to "Cancelled",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = status == value,
                    onClick = { status = value },
                    label = { Text(label) },
                )
            }
        }
        FormError(error)
        SaveButton("Save Changes") {
            vm.updateGoal(
                id = goal.id,
                title = title,
                target = Fmt.digitsToLong(target),
                saved = Fmt.digitsToLong(saved),
                status = status,
                onError = { error = it },
                onDone = onClose,
            )
        }
        TextButton(onClick = { confirmRemove = true }) {
            Text("Remove Goal", color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove goal?",
            message = "\"${goal.title}\" will be deleted. Wallet balances are not affected.",
            confirmLabel = "Remove",
            onConfirm = { vm.deleteGoal(goal.id, onDone = onClose) },
            onDismiss = { confirmRemove = false },
        )
    }
}

// --------------------------------------------------------------- transfer

@Composable
fun TransferSheet(vm: MainViewModel, wallets: List<Wallet>, onClose: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var fromId by remember { mutableStateOf<Long?>(null) }
    var toId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    fun localError(): String? {
        val value = Fmt.digitsToLong(amount)
        if (value <= 1000) return "Amount must be greater than 1.000"
        if (fromId == null || toId == null) return "Please select both wallets"
        if (fromId == toId) return "Wallet asal and tujuan cannot be the same"
        return null
    }

    SheetColumn {
        SheetTitle("Tukar Uang")
        SheetNote("Moves money between wallets. Not income, not expense, and no record is created.")
        MoneyField(label = "Amount", digits = amount, onDigitsChange = { amount = it })
        PickerField(
            label = "From Wallet",
            selectedLabel = wallets.firstOrNull { it.id == fromId }?.name ?: "",
            options = wallets.map { it.id to it.name },
            onSelect = { fromId = it },
        )
        PickerField(
            label = "To Wallet",
            selectedLabel = wallets.firstOrNull { it.id == toId }?.name ?: "",
            options = wallets.map { it.id to it.name },
            onSelect = { toId = it },
        )
        FormError(error)
        SaveButton("Move Money") {
            val local = localError()
            if (local != null) error = local else confirm = true
        }
    }

    if (confirm) {
        val fromName = wallets.firstOrNull { it.id == fromId }?.name ?: ""
        val toName = wallets.firstOrNull { it.id == toId }?.name ?: ""
        ConfirmDialog(
            title = "Confirm transfer",
            message = "Move ${Fmt.rp(Fmt.digitsToLong(amount))} from $fromName to $toName?",
            confirmLabel = "Move",
            destructive = false,
            onConfirm = {
                vm.transfer(
                    amount = Fmt.digitsToLong(amount),
                    fromId = fromId,
                    toId = toId,
                    onError = { error = it },
                    onDone = onClose,
                )
            },
            onDismiss = { confirm = false },
        )
    }
}

// ------------------------------------------------------------- sync month

@Composable
fun SyncSheet(vm: MainViewModel, feeDraft: String, onClose: () -> Unit) {
    val data = vm.data
    var month by remember { mutableStateOf(data.currentMonth) }
    var balance by remember { mutableStateOf(data.totalBalance.toString()) }
    var income by remember { mutableStateOf(data.monthIncome.toString()) }
    var expense by remember { mutableStateOf(data.monthExpense.toString()) }
    var feePercent by remember { mutableStateOf(feeDraft) }
    var feeExpense by remember {
        mutableStateOf(
            Fmt.feeExpense(data.monthIncome, Fmt.parseFee(feeDraft)).toString(),
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    SheetColumn {
        SheetTitle("Sync This Month")
        SheetNote("Saving the same month again overwrites its snapshot.")
        LabeledTextField(value = month, onValueChange = { month = it }, label = "Month (YYYY-MM)")
        MoneyField(label = "Total Balance", digits = balance, onDigitsChange = { balance = it })
        MoneyField(label = "Total Income", digits = income, onDigitsChange = { income = it })
        MoneyField(label = "Total Expense", digits = expense, onDigitsChange = { expense = it })
        OutlinedTextField(
            value = feePercent,
            onValueChange = { feePercent = Fmt.sanitizeFeeInput(it) },
            label = { Text("Fee %") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        MoneyField(
            label = "Fee Expense",
            digits = feeExpense,
            onDigitsChange = { feeExpense = it },
        )
        FormError(error)
        SaveButton("Save Snapshot") {
            if (month.isBlank()) error = "Month is required" else confirm = true
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Save snapshot?",
            message = "Save the $month snapshot? An existing snapshot for this month will be overwritten.",
            confirmLabel = "Save",
            destructive = false,
            onConfirm = {
                vm.upsertSnapshot(
                    month = month,
                    totalBalance = Fmt.digitsToLong(balance),
                    totalIncome = Fmt.digitsToLong(income),
                    totalExpense = Fmt.digitsToLong(expense),
                    feePercent = Fmt.parseFee(feePercent),
                    feeExpense = Fmt.digitsToLong(feeExpense),
                    onError = { error = it },
                    onDone = onClose,
                )
            },
            onDismiss = { confirm = false },
        )
    }
}

// -------------------------------------------------------------- deletions

@Composable
fun DeleteWalletsSheet(vm: MainViewModel, wallets: List<Wallet>, onClose: () -> Unit) {
    var pending by remember { mutableStateOf<Wallet?>(null) }
    var pendingAll by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Delete Wallets")
        SheetNote("Deleting a wallet does not move its balance. Its transactions stay in history and show \"Unknown Wallet\".")
        if (wallets.isEmpty()) EmptyText("No wallet yet")
        wallets.forEach { wallet ->
            SelectableRow(
                title = wallet.name,
                subtitle = Fmt.rp(wallet.balance),
                onClick = { pending = wallet },
            )
        }
        FormError(error)
        if (wallets.isNotEmpty()) {
            TextButton(onClick = { pendingAll = true }) {
                Text("Delete All Wallets", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pending?.let { wallet ->
        ConfirmDialog(
            title = "Delete wallet?",
            message = "\"${wallet.name}\" will be removed and ${Fmt.rp(wallet.balance)} leaves Total Balance. The money is not moved.",
            onConfirm = {
                vm.deleteWallet(wallet.id, onError = { error = it })
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
    if (pendingAll) {
        ConfirmDialog(
            title = "Delete all wallets?",
            message = "All wallets will be removed. Their balances leave Total Balance and are not moved anywhere.",
            confirmLabel = "Delete All",
            onConfirm = {
                vm.deleteAllWallets(onError = { error = it })
                pendingAll = false
            },
            onDismiss = { pendingAll = false },
        )
    }
}

@Composable
fun DeleteTransactionsSheet(
    vm: MainViewModel,
    transactions: List<Trx>,
    walletNames: Map<Long, String>,
    onClose: () -> Unit,
) {
    var pending by remember { mutableStateOf<Trx?>(null) }
    var pendingAll by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Delete Transactions")
        SheetNote("Deleting a transaction reverses its effect on the wallet, so balances stay consistent.")
        if (transactions.isEmpty()) EmptyText("No transactions yet")
        transactions.forEach { t ->
            SelectableRow(
                title = t.title,
                subtitle = "${Fmt.timestamp(t.occurredAt)} · ${walletNames[t.walletId] ?: "Unknown Wallet"}",
                trailing = (if (t.type == "income") "+ " else "- ") + Fmt.rp(t.amount),
                onClick = { pending = t },
            )
        }
        FormError(error)
        if (transactions.isNotEmpty()) {
            TextButton(onClick = { pendingAll = true }) {
                Text("Delete All Transactions", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pending?.let { t ->
        ConfirmDialog(
            title = "Delete transaction?",
            message = "\"${t.title}\" (${Fmt.rp(t.amount)}) will be deleted and its wallet effect reversed.",
            onConfirm = {
                vm.deleteTransaction(t.id)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
    if (pendingAll) {
        ConfirmDialog(
            title = "Delete all transactions?",
            message = "All ${transactions.size} transactions will be deleted in one step. Wallet balances are adjusted to undo their effects.",
            confirmLabel = "Delete All",
            onConfirm = {
                vm.deleteAllTransactions()
                pendingAll = false
            },
            onDismiss = { pendingAll = false },
        )
    }
}

@Composable
fun DeleteQuickActionsSheet(vm: MainViewModel, actions: List<QuickAction>, onClose: () -> Unit) {
    var pending by remember { mutableStateOf<QuickAction?>(null) }
    var pendingAll by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Delete Quick Actions")
        if (actions.isEmpty()) EmptyText("No Quick Actions yet")
        actions.forEach { action ->
            SelectableRow(
                title = action.title,
                subtitle = action.type.replaceFirstChar { it.uppercase() },
                trailing = (if (action.type == "income") "+ " else "- ") + Fmt.rp(action.amount),
                onClick = { pending = action },
            )
        }
        FormError(error)
        if (actions.isNotEmpty()) {
            TextButton(onClick = { pendingAll = true }) {
                Text("Delete All Quick Actions", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pending?.let { action ->
        ConfirmDialog(
            title = "Delete quick action?",
            message = "\"${action.title}\" will be removed. This never touches transactions or balances.",
            onConfirm = {
                vm.deleteQuickAction(action.id, onError = { error = it })
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
    if (pendingAll) {
        ConfirmDialog(
            title = "Delete all quick actions?",
            message = "All saved quick actions will be removed. Transactions and balances are untouched.",
            confirmLabel = "Delete All",
            onConfirm = {
                vm.deleteAllQuickActions(onError = { error = it })
                pendingAll = false
            },
            onDismiss = { pendingAll = false },
        )
    }
}

@Composable
fun DeleteSnapshotsSheet(vm: MainViewModel, snapshots: List<Snapshot>, onClose: () -> Unit) {
    var pending by remember { mutableStateOf<Snapshot?>(null) }
    var pendingAll by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Delete Monthly Data")
        SheetNote("Snapshots are independent: deleting them never touches transactions or wallets.")
        if (snapshots.isEmpty()) EmptyText("No monthly snapshots yet")
        snapshots.forEach { snapshot ->
            SelectableRow(
                title = snapshot.month,
                subtitle = "Updated ${Fmt.timestamp(snapshot.updatedAt)}",
                trailing = Fmt.rp(snapshot.totalBalance),
                onClick = { pending = snapshot },
            )
        }
        FormError(error)
        if (snapshots.isNotEmpty()) {
            TextButton(onClick = { pendingAll = true }) {
                Text("Delete All Snapshots", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pending?.let { snapshot ->
        ConfirmDialog(
            title = "Delete snapshot?",
            message = "The ${snapshot.month} snapshot will be deleted. Transactions and wallets are untouched.",
            onConfirm = {
                vm.deleteSnapshot(snapshot.month, onError = { error = it })
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
    if (pendingAll) {
        ConfirmDialog(
            title = "Delete all snapshots?",
            message = "All monthly snapshots will be deleted. Transactions and wallets are untouched.",
            confirmLabel = "Delete All",
            onConfirm = {
                vm.deleteAllSnapshots(onError = { error = it })
                pendingAll = false
            },
            onDismiss = { pendingAll = false },
        )
    }
}
