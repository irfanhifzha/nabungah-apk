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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nabungah.data.AppData
import com.nabungah.data.Goal
import com.nabungah.data.QuickAction
import com.nabungah.data.Snapshot
import com.nabungah.data.Trx
import com.nabungah.data.Wallet
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- clipboard

fun trxCopyText(t: Trx, walletName: String): String = buildString {
    append("*Nabungah — Transaksi*\n")
    append("${t.title}\n")
    append("${Fmt.rp(t.amount)} · ${if (t.type == "income") "Pemasukan" else "Pengeluaran"}\n")
    append("Dompet: $walletName\n")
    append("Tanggal: ${Fmt.timestamp(t.occurredAt)}\n")
    append("Catatan: ${t.note.ifBlank { "-" }}")
}

fun snapshotCopyText(s: Snapshot): String = buildString {
    append("*Nabungah — ${Fmt.monthLabel(s.month)}*\n")
    append("Sumber: ${if (s.isManual) "Manual" else "Otomatis"}\n")
    append("Pemasukan: ${Fmt.rp(s.totalIncome)}\n")
    append("Pengeluaran: ${Fmt.rp(s.totalExpense)}\n")
    append("Saldo: ${Fmt.rp(s.totalBalance)}\n")
    append("Biaya (${Fmt.feePercentLabel(s.feePercent)}%): ${Fmt.rp(s.feeExpense)}\n")
    append("Diperbarui: ${Fmt.timestamp(s.updatedAt)}")
}

/** Compact, chat-ready copy of one month: summary first, then one line per transaction. */
fun monthCopyText(
    month: String,
    report: MonthReport,
    transactions: List<Trx>,
    walletNames: Map<Long, String>,
): String = buildString {
    append("*Nabungah — ${Fmt.monthLabel(month)}*\n")
    append("Pemasukan: ${Fmt.rp(report.income)}\n")
    append("Pengeluaran: ${Fmt.rp(report.expense)}\n")
    append("Selisih: ${signed(report.net)}\n")
    append("Transaksi: ${report.count}\n")
    append("Saldo akhir bulan: ${Fmt.rp(report.balance)}\n")
    val rows = transactions
        .filter { it.month == month }
        .sortedByDescending { it.occurredAt }
    append("\nRincian:\n")
    if (rows.isEmpty()) {
        append("Belum ada transaksi.")
    } else {
        rows.forEachIndexed { index, t ->
            if (index > 0) append('\n')
            val sign = if (t.type == "income") "+ " else "- "
            append("${Fmt.dayMonth(t.occurredAt)} ${t.title} — $sign${Fmt.rp(t.amount)}")
            append(" · ${walletNames[t.walletId] ?: "Dompet dihapus"}")
        }
    }
}

private fun signed(value: Long): String =
    if (value < 0) "- " + Fmt.rp(-value) else "+ " + Fmt.rp(value)

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
    OutlinedActionButton(
        text = if (copied) "✓ Tersalin" else "Salin",
        onClick = {
            copyToClipboard(context, "Nabungah", text)
            copied = true
        },
        modifier = modifier,
        color = if (copied) IncomeGreen else Muted,
        compact = true,
    )
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
            label = { Text("Pengeluaran (-)") },
        )
        FilterChip(
            selected = type == "income",
            onClick = { onChange("income") },
            label = { Text("Pemasukan (+)") },
        )
    }
}

@Composable
private fun SaveButton(label: String, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, millis: Long, onChange: (Long) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    OutlinedActionButton(
        text = "$label: ${Fmt.dateLabel(millis)}",
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth(),
        color = Muted,
    )
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Fmt.utcDateMillis(millis))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        onChange(Fmt.mergePickedDate(picked))
                    }
                    showPicker = false
                }) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Batal") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

// -------------------------------------------------------------- add wallet

@Composable
fun AddWalletSheet(vm: MainViewModel, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        vm.addWallet(
            name = name,
            balance = Fmt.digitsToLong(balance),
            onError = { error = it },
            onDone = onClose,
        )
    }

    SheetColumn {
        SheetTitle("Tambah Dompet")
        SheetNote("Isi saldo dompet saat ini. Dompet baru tidak harus mulai dari 0.")
        LabeledTextField(value = name, onValueChange = { name = it }, label = "Nama Dompet")
        MoneyField(
            label = "Saldo Awal",
            digits = balance,
            onDigitsChange = { balance = it },
            onDone = { submit() },
        )
        FormError(error)
        SaveButton("Simpan Dompet") { submit() }
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
    var title by rememberSaveable { mutableStateOf(prefill?.title ?: "") }
    var amount by rememberSaveable { mutableStateOf(prefill?.amount?.toString() ?: "") }
    var type by rememberSaveable { mutableStateOf(prefill?.type ?: "expense") }
    var walletId by rememberSaveable { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var occurredAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val amountFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // A quick action should land directly in the amount field, ready to save.
    LaunchedEffect(prefill) {
        if (prefill != null) {
            amountFocus.requestFocus()
            keyboard?.show()
        }
    }

    fun submit() {
        vm.addTransaction(
            title = title,
            amount = Fmt.digitsToLong(amount),
            type = type,
            walletId = walletId,
            note = note,
            occurredAt = occurredAt,
            onError = { error = it },
            onDone = onClose,
        )
    }

    SheetColumn {
        SheetTitle("Transaksi Baru")
        LabeledTextField(value = title, onValueChange = { title = it }, label = "Judul")
        MoneyField(
            label = "Nominal",
            digits = amount,
            onDigitsChange = { amount = it },
            modifier = Modifier.focusRequester(amountFocus),
            onDone = { submit() },
        )
        TypeSelector(type = type, onChange = { type = it })
        PickerField(
            label = "Dompet",
            selectedLabel = wallets.firstOrNull { it.id == walletId }?.name ?: "",
            options = wallets.map { it.id to it.name },
            onSelect = { walletId = it },
        )
        DateField(label = "Tanggal", millis = occurredAt, onChange = { occurredAt = it })
        LabeledTextField(
            value = note,
            onValueChange = { note = it },
            label = "Catatan (opsional)",
            singleLine = false,
            minLines = 2,
        )
        FormError(error)
        SaveButton("Simpan Transaksi") { submit() }
    }
}

@Composable
fun TransactionDetailSheet(
    vm: MainViewModel,
    trx: Trx,
    walletName: String,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Detail Transaksi")
        Text(
            text = trx.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = (if (trx.type == "income") "+ " else "- ") + Fmt.rp(trx.amount),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (trx.type == "income") IncomeGreen else ExpenseRed,
        )
        SheetNote(
            "${if (trx.type == "income") "Pemasukan" else "Pengeluaran"} · ${trx.month}\n" +
                "Dompet: $walletName\nTanggal: ${Fmt.timestamp(trx.occurredAt)}\n" +
                "Catatan: ${trx.note.ifBlank { "-" }}",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CopyButton(text = trxCopyText(trx, walletName), modifier = Modifier.weight(1f))
            OutlinedActionButton(
                text = "Edit",
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedActionButton(
            text = "Hapus transaksi",
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.error,
        )
        FormError(error)
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus transaksi?",
            message = "\"${trx.title}\" (${Fmt.rp(trx.amount)}) akan dihapus dan efeknya pada " +
                "dompet dibalik. Kamu bisa mengurungkannya lewat snackbar.",
            onConfirm = {
                vm.deleteTransaction(trx.id)
                onClose()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun EditTransactionSheet(
    vm: MainViewModel,
    trx: Trx,
    wallets: List<Wallet>,
    onClose: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(trx.title) }
    var amount by rememberSaveable { mutableStateOf(trx.amount.toString()) }
    var type by rememberSaveable { mutableStateOf(trx.type) }
    var walletId by rememberSaveable { mutableStateOf(trx.walletId) }
    var note by rememberSaveable { mutableStateOf(trx.note) }
    var occurredAt by rememberSaveable { mutableStateOf(trx.occurredAt) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Edit Transaksi")
        SheetNote("Perubahan saldo dompet dihitung ulang dalam satu transaksi database.")
        LabeledTextField(value = title, onValueChange = { title = it }, label = "Judul")
        MoneyField(label = "Nominal", digits = amount, onDigitsChange = { amount = it })
        TypeSelector(type = type, onChange = { type = it })
        PickerField(
            label = "Dompet",
            selectedLabel = wallets.firstOrNull { it.id == walletId }?.name ?: "",
            options = wallets.map { it.id to it.name },
            onSelect = { walletId = it },
        )
        DateField(label = "Tanggal", millis = occurredAt, onChange = { occurredAt = it })
        LabeledTextField(
            value = note,
            onValueChange = { note = it },
            label = "Catatan (opsional)",
            singleLine = false,
            minLines = 2,
        )
        FormError(error)
        SaveButton("Simpan Perubahan") {
            vm.updateTransaction(
                id = trx.id,
                title = title,
                amount = Fmt.digitsToLong(amount),
                type = type,
                walletId = walletId,
                note = note,
                occurredAt = occurredAt,
                onError = { error = it },
                onDone = onClose,
            )
        }
    }
}

// ------------------------------------------------------- new quick action

@Composable
fun AddQuickActionSheet(vm: MainViewModel, onClose: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("expense") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Aksi Cepat Baru")
        SheetNote("Template untuk form transaksi. Tidak pernah memindahkan uang sendiri.")
        LabeledTextField(value = title, onValueChange = { title = it }, label = "Judul")
        MoneyField(label = "Nominal", digits = amount, onDigitsChange = { amount = it })
        TypeSelector(type = type, onChange = { type = it })
        FormError(error)
        SaveButton("Simpan Aksi Cepat") {
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
    var title by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        vm.addGoal(
            title = title,
            target = Fmt.digitsToLong(target),
            onError = { error = it },
            onDone = onClose,
        )
    }

    SheetColumn {
        SheetTitle("Buat Target")
        SheetNote("Target tidak terhubung ke dompet. Pindahkan uangnya secara manual.")
        LabeledTextField(value = title, onValueChange = { title = it }, label = "Nama Target")
        MoneyField(
            label = "Jumlah Target",
            digits = target,
            onDigitsChange = { target = it },
            onDone = { submit() },
        )
        FormError(error)
        SaveButton("Buat Target") { submit() }
    }
}

@Composable
fun EditGoalSheet(vm: MainViewModel, goal: Goal, onClose: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(goal.title) }
    var target by rememberSaveable { mutableStateOf(goal.targetAmount.toString()) }
    var saved by rememberSaveable { mutableStateOf(goal.savedAmount.toString()) }
    var status by rememberSaveable { mutableStateOf(goal.status) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    SheetColumn {
        SheetTitle("Edit Target")
        LabeledTextField(value = title, onValueChange = { title = it }, label = "Nama Target")
        MoneyField(label = "Jumlah Target", digits = target, onDigitsChange = { target = it })
        MoneyField(label = "Sudah Terkumpul", digits = saved, onDigitsChange = { saved = it })
        Text("Status", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "in_progress" to "Berjalan",
                "completed" to "Selesai",
                "cancelled" to "Dibatalkan",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = status == value,
                    onClick = { status = value },
                    label = { Text(label) },
                )
            }
        }
        FormError(error)
        SaveButton("Simpan Perubahan") {
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
        OutlinedActionButton(
            text = "Hapus Target",
            onClick = { confirmRemove = true },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Hapus target?",
            message = "\"${goal.title}\" akan dihapus. Saldo dompet tidak terpengaruh.",
            confirmLabel = "Hapus",
            onConfirm = { vm.deleteGoal(goal.id, onDone = onClose) },
            onDismiss = { confirmRemove = false },
        )
    }
}

// --------------------------------------------------------------- transfer

@Composable
fun TransferSheet(vm: MainViewModel, wallets: List<Wallet>, onClose: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var fromId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toId by rememberSaveable { mutableStateOf<Long?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }

    fun localError(): String? {
        val value = Fmt.digitsToLong(amount)
        if (value <= 1000) return "Minimal transfer Rp 1.001"
        if (fromId == null || toId == null) return "Pilih kedua dompet"
        if (fromId == toId) return "Dompet asal dan tujuan tidak boleh sama"
        return null
    }

    SheetColumn {
        SheetTitle("Tukar Uang")
        SheetNote(
            "Memindahkan uang antar dompet. Tercatat di Aktivitas sebagai transfer, " +
                "tetapi tidak dihitung sebagai pemasukan atau pengeluaran.",
        )
        MoneyField(label = "Nominal", digits = amount, onDigitsChange = { amount = it })
        PickerField(
            label = "Dari Dompet",
            selectedLabel = wallets.firstOrNull { it.id == fromId }?.name ?: "",
            options = wallets.map { it.id to it.name },
            onSelect = { fromId = it },
        )
        PickerField(
            label = "Ke Dompet",
            selectedLabel = wallets.firstOrNull { it.id == toId }?.name ?: "",
            options = wallets.map { it.id to it.name },
            onSelect = { toId = it },
        )
        FormError(error)
        SaveButton("Pindahkan Uang") {
            val local = localError()
            if (local != null) error = local else confirm = true
        }
    }

    if (confirm) {
        val fromName = wallets.firstOrNull { it.id == fromId }?.name ?: ""
        val toName = wallets.firstOrNull { it.id == toId }?.name ?: ""
        ConfirmDialog(
            title = "Konfirmasi transfer",
            message = "Pindahkan ${Fmt.rp(Fmt.digitsToLong(amount))} dari $fromName ke $toName?",
            confirmLabel = "Pindahkan",
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

// ------------------------------------------------------------------- fee

@Composable
fun EditFeeSheet(vm: MainViewModel, current: Double, onClose: () -> Unit) {
    var percent by rememberSaveable { mutableStateOf(Fmt.feePercentLabel(current)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val value = Fmt.parseFee(percent)

    SheetColumn {
        SheetTitle("Biaya")
        SheetNote(
            "Biaya disimpan sebagai persentase dari pemasukan. Nilainya hanya informasi: " +
                "tidak pernah dipotong dari saldo dompet.",
        )
        OutlinedTextField(
            value = percent,
            onValueChange = { percent = Fmt.sanitizeFeeInput(it) },
            label = { Text("Biaya (%)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Contoh: pemasukan Rp 1.000.000 bulan ini → biaya ${Fmt.rp(Fmt.feeExpense(1_000_000, value))}.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        FormError(error)
        SaveButton("Simpan Biaya") {
            vm.setFeePercent(value, onError = { error = it }, onDone = onClose)
        }
    }
}

// ------------------------------------------------------------- sync month

@Composable
fun EditReportSheet(
    vm: MainViewModel,
    month: String,
    initialIncome: Long,
    initialExpense: Long,
    initialBalance: Long,
    feeDraft: String,
    onClose: () -> Unit,
) {
    var balance by rememberSaveable { mutableStateOf(initialBalance.toString()) }
    var income by rememberSaveable { mutableStateOf(initialIncome.toString()) }
    var expense by rememberSaveable { mutableStateOf(initialExpense.toString()) }
    var feePercent by rememberSaveable { mutableStateOf(feeDraft) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }

    val feeExpense = Fmt.feeExpense(Fmt.digitsToLong(income), Fmt.parseFee(feePercent))

    SheetColumn {
        SheetTitle("Edit laporan ${Fmt.monthLabel(month)}")
        SheetNote(
            "Menyimpan di sini mengunci bulan dengan angkamu sendiri. Update otomatis akan " +
                "melewatinya sampai kamu kembalikan ke otomatis di Laporan.",
        )
        Text(
            text = "Bulan: ${Fmt.monthLabel(month)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        MoneyField(label = "Total Saldo", digits = balance, onDigitsChange = { balance = it })
        MoneyField(label = "Total Pemasukan", digits = income, onDigitsChange = { income = it })
        MoneyField(label = "Total Pengeluaran", digits = expense, onDigitsChange = { expense = it })
        OutlinedTextField(
            value = feePercent,
            onValueChange = { feePercent = Fmt.sanitizeFeeInput(it) },
            label = { Text("Biaya %") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Biaya dari pemasukan: ${Fmt.rp(feeExpense)}",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        FormError(error)
        SaveButton("Simpan sebagai manual") { confirm = true }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Simpan laporan manual?",
            message = "${Fmt.monthLabel(month)} akan ditandai manual. Update otomatis akhir bulan " +
                "tidak akan mengubahnya lagi.",
            confirmLabel = "Simpan",
            destructive = false,
            onConfirm = {
                vm.upsertSnapshot(
                    month = month,
                    totalBalance = Fmt.digitsToLong(balance),
                    totalIncome = Fmt.digitsToLong(income),
                    totalExpense = Fmt.digitsToLong(expense),
                    feePercent = Fmt.parseFee(feePercent),
                    feeExpense = feeExpense,
                    source = Snapshot.SOURCE_MANUAL,
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
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Hapus Dompet")
        SheetNote("Menghapus dompet tidak memindahkan saldonya. Transaksinya tetap ada dan tampil sebagai \"Dompet tidak dikenal\".")
        if (wallets.isEmpty()) EmptyText("Belum ada dompet")
        wallets.forEach { wallet ->
            SelectableRow(
                title = wallet.name,
                subtitle = Fmt.rp(wallet.balance),
                onClick = { pending = wallet },
            )
        }
        FormError(error)
        if (wallets.isNotEmpty()) {
            OutlinedActionButton(
                text = "Hapus Semua Dompet",
                onClick = { pendingAll = true },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    pending?.let { wallet ->
        ConfirmDialog(
            title = "Hapus dompet?",
            message = "\"${wallet.name}\" akan dihapus dan ${Fmt.rp(wallet.balance)} keluar dari Total Saldo. Uangnya tidak dipindahkan.",
            onConfirm = {
                vm.deleteWallet(wallet.id, onError = { error = it })
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
    if (pendingAll) {
        ConfirmDialog(
            title = "Hapus semua dompet?",
            message = "Semua dompet dihapus. Saldonya keluar dari Total Saldo dan tidak dipindahkan ke mana pun.",
            confirmLabel = "Hapus Semua",
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
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Hapus Transaksi")
        SheetNote("Menghapus transaksi membalik efeknya pada dompet, jadi saldo tetap konsisten.")
        if (transactions.isEmpty()) EmptyText("Belum ada transaksi")
        transactions.forEach { t ->
            SelectableRow(
                title = t.title,
                subtitle = "${Fmt.timestamp(t.occurredAt)} · ${walletNames[t.walletId] ?: "Dompet tidak dikenal"}",
                trailing = (if (t.type == "income") "+ " else "- ") + Fmt.rp(t.amount),
                onClick = { pending = t },
            )
        }
        FormError(error)
        if (transactions.isNotEmpty()) {
            OutlinedActionButton(
                text = "Hapus Semua Transaksi",
                onClick = { pendingAll = true },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    pending?.let { t ->
        ConfirmDialog(
            title = "Hapus transaksi?",
            message = "\"${t.title}\" (${Fmt.rp(t.amount)}) akan dihapus dan efeknya pada dompet dibalik.",
            onConfirm = {
                vm.deleteTransaction(t.id)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
    if (pendingAll) {
        ConfirmDialog(
            title = "Hapus semua transaksi?",
            message = "Semua ${transactions.size} transaksi dihapus sekaligus. Saldo dompet disesuaikan untuk membatalkan efeknya.",
            confirmLabel = "Hapus Semua",
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
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Hapus Aksi Cepat")
        if (actions.isEmpty()) EmptyText("Belum ada aksi cepat")
        actions.forEach { action ->
            SelectableRow(
                title = action.title,
                subtitle = if (action.type == "income") "Pemasukan" else "Pengeluaran",
                trailing = (if (action.type == "income") "+ " else "- ") + Fmt.rp(action.amount),
                onClick = { pending = action },
            )
        }
        FormError(error)
        if (actions.isNotEmpty()) {
            OutlinedActionButton(
                text = "Hapus Semua Aksi Cepat",
                onClick = { pendingAll = true },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    pending?.let { action ->
        ConfirmDialog(
            title = "Hapus aksi cepat?",
            message = "\"${action.title}\" akan dihapus. Transaksi dan saldo tidak tersentuh.",
            onConfirm = {
                vm.deleteQuickAction(action.id, onError = { error = it })
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
    if (pendingAll) {
        ConfirmDialog(
            title = "Hapus semua aksi cepat?",
            message = "Semua aksi cepat dihapus. Transaksi dan saldo tidak tersentuh.",
            confirmLabel = "Hapus Semua",
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
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    SheetColumn {
        SheetTitle("Hapus Data Bulanan")
        SheetNote("Snapshot berdiri sendiri: menghapusnya tidak menyentuh transaksi atau dompet.")
        if (snapshots.isEmpty()) EmptyText("Belum ada snapshot bulanan")
        snapshots.forEach { snapshot ->
            SelectableRow(
                title = snapshot.month,
                subtitle = "Diperbarui ${Fmt.timestamp(snapshot.updatedAt)}",
                trailing = Fmt.rp(snapshot.totalBalance),
                onClick = { pending = snapshot },
            )
        }
        FormError(error)
        if (snapshots.isNotEmpty()) {
            OutlinedActionButton(
                text = "Hapus Semua Snapshot",
                onClick = { pendingAll = true },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    pending?.let { snapshot ->
        ConfirmDialog(
            title = "Hapus snapshot?",
            message = "Snapshot ${snapshot.month} akan dihapus. Transaksi dan dompet tidak tersentuh.",
            onConfirm = {
                vm.deleteSnapshot(snapshot.month, onError = { error = it })
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
    if (pendingAll) {
        ConfirmDialog(
            title = "Hapus semua snapshot?",
            message = "Semua snapshot bulanan akan dihapus. Transaksi dan dompet tidak tersentuh.",
            confirmLabel = "Hapus Semua",
            onConfirm = {
                vm.deleteAllSnapshots(onError = { error = it })
                pendingAll = false
            },
            onDismiss = { pendingAll = false },
        )
    }
}

// ---------------------------------------------------------- delete all data

@Composable
fun DeleteAllDataSheet(vm: MainViewModel, data: AppData, onClose: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val confirmed = typed.trim().uppercase() == "HAPUS"

    SheetColumn {
        SheetTitle("Hapus semua data")
        Text(
            text = "Ini menghapus seluruh isi aplikasi, bukan hanya satu bagian. " +
                "Backup yang sudah kamu ekspor tetap aman, tetapi tanpa backup data ini " +
                "tidak bisa dikembalikan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        SheetNote(
            "${data.wallets.size} dompet · ${data.transactions.size} transaksi · " +
                "${data.transfers.size} transfer · ${data.goals.size} target · " +
                "${data.quickActions.size} aksi cepat · ${data.snapshots.size} snapshot",
        )
        LabeledTextField(
            value = typed,
            onValueChange = { typed = it.uppercase() },
            label = "Ketik HAPUS untuk melanjutkan",
        )
        FormError(error)
        Button(
            onClick = { vm.deleteAllData(onError = { error = it }, onDone = onClose) },
            enabled = confirmed,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
        ) {
            Text("Hapus Semua Data")
        }
    }
}
