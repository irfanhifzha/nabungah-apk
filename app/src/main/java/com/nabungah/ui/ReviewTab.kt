package com.nabungah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nabungah.data.AppData
import com.nabungah.data.ReviewMonthData
import com.nabungah.data.Snapshot
import kotlin.math.roundToInt

/**
 * Monthly report: every figure comes from that month's transactions, so the selected month is
 * always live. Completed months also get an automatic snapshot when the calendar month rolls
 * over; editing a month by hand freezes it as `manual` until the user switches it back.
 */
@Composable
fun ReviewTab(vm: MainViewModel, data: AppData, onSheet: (Sheet) -> Unit) {
    val months = remember(data.transactionMonths, data.snapshots, data.currentMonth) {
        (data.transactionMonths + data.snapshots.map { it.month } + data.currentMonth)
            .distinct()
            .sortedDescending()
    }
    var month by rememberSaveable { mutableStateOf(data.currentMonth) }
    var message by remember { mutableStateOf<String?>(null) }
    var monthData by remember { mutableStateOf(ReviewMonthData()) }
    var previousData by remember { mutableStateOf(ReviewMonthData()) }

    LaunchedEffect(months) {
        if (month !in months) month = months.firstOrNull() ?: data.currentMonth
    }
    LaunchedEffect(data.currentMonth) {
        if (month.isEmpty()) month = data.currentMonth
    }

    val snapshot = data.snapshots.firstOrNull { it.month == month }
    LaunchedEffect(month) { message = null }

    val index = months.indexOf(month)
    val older = months.getOrNull(index + 1)
    val newer = if (index > 0) months[index - 1] else null

    LaunchedEffect(month, older, vm.dataRevision) {
        monthData = vm.reviewMonth(month)
        previousData = older?.let { vm.reviewMonth(it) } ?: ReviewMonthData()
    }

    // The live fee comes from the saved setting, never from a per-screen draft.
    val feePercent = data.feePercent
    val activeData = monthData.takeIf { it.month == month } ?: ReviewMonthData(month = month)
    val report = remember(activeData, data.totalBalance, month, feePercent) {
        buildMonthReport(
            month,
            activeData.transactions,
            data.totalBalance,
            feePercent,
            activeData.netAfter,
        )
    }

    val isCurrentMonth = month == data.currentMonth
    val isManual = snapshot?.isManual == true
    val isAuto = snapshot?.source == Snapshot.SOURCE_AUTO
    // Saved fee percentages are historical and deliberately not compared with the live setting.
    val differs = snapshot != null && (
        snapshot.totalIncome != report.income ||
            snapshot.totalExpense != report.expense ||
            snapshot.totalBalance != report.balance
        )

    val previousReport = older?.let { previous ->
        buildMonthReport(
            previous,
            previousData.takeIf { it.month == previous }?.transactions.orEmpty(),
            data.totalBalance,
            feePercent,
            previousData.takeIf { it.month == previous }?.netAfter ?: 0L,
        )
    }
    val incomeTrend = previousReport?.let { changeLabel(report.income, it.income) }
    val expenseTrend = previousReport?.let { changeLabel(report.expense, it.expense) }

    fun resetToAuto() {
        vm.resetSnapshotToAuto(
            month = month,
            onError = { message = it },
            onDone = {
                message = if (isCurrentMonth) {
                    "Bulan ini kembali langsung; nanti tersimpan otomatis saat bulan berganti."
                } else {
                    "Laporan otomatis diperbarui untuk ${Fmt.monthLabel(month)}."
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListBottomPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Laporan",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Laporan bulanan dari transaksimu, plus biaya yang perlu disisihkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }

        item { MonthSwitcher(month, report.count, older, newer, onOlder = { month = it }, onNewer = { month = it }) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    label = when {
                        isCurrentMonth -> "Bulan berjalan"
                        isManual -> "Manual"
                        isAuto -> "Otomatis"
                        else -> "Belum disimpan"
                    },
                    color = when {
                        isCurrentMonth -> MaterialTheme.colorScheme.primary
                        isManual -> StatusOrange
                        else -> IncomeGreen
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = snapshot?.let { "Disimpan ${Fmt.timestamp(it.updatedAt)}" }
                        ?: if (isCurrentMonth) "Langsung, belum disimpan" else "Belum disimpan",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }

        item {
            SectionCard(
                title = "Statistik ${Fmt.monthLabel(month)}",
                actions = {
                    CopyButton(
                        text = monthCopyText(month, report, activeData.transactions, data.walletNames),
                    )
                },
            ) {
                Text(
                    text = "Ringkasan cepat bulan ini, dihitung langsung dari transaksimu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = "Pemasukan",
                        value = "+ " + Fmt.rp(report.income),
                        color = IncomeGreen,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Pengeluaran",
                        value = "- " + Fmt.rp(report.expense),
                        color = ExpenseRed,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = "Selisih",
                        value = signedRp(report.net),
                        color = if (report.net >= 0) IncomeGreen else ExpenseRed,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Rata-rata pengeluaran",
                        value = Fmt.rp(report.averageExpense),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = "Transaksi",
                        value = report.count.toString(),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Biaya ${Fmt.feePercentLabel(report.feePercent)}%",
                        value = Fmt.rp(report.feeExpense),
                        color = StatusOrange,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (incomeTrend != null || expenseTrend != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = listOfNotNull(
                            incomeTrend?.let { "Pemasukan $it" },
                            expenseTrend?.let { "Pengeluaran $it" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                HorizontalDivider(
                    color = CardBorder,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                AmountRow("Saldo akhir bulan", report.balance, MaterialTheme.colorScheme.onSurface)
                if (report.topExpenses.isNotEmpty()) {
                    HorizontalDivider(
                        color = CardBorder,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        text = "Pengeluaran terbesar",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    report.topExpenses.forEach { item ->
                        AmountRow(item.title, item.total, ExpenseRed, prefix = "- ")
                    }
                }
            }
        }

        item {
            SectionCard(title = "Saldo saat ini") {
                AmountRow(
                    "Total saldo",
                    data.totalBalance,
                    MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Saldo semua dompet saat ini, bukan angka akhir bulan.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                if (data.wallets.isEmpty()) {
                    EmptyText("Belum ada dompet.")
                } else {
                    HorizontalDivider(
                        color = CardBorder,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    data.wallets.forEach { wallet ->
                        AmountRow(
                            wallet.name,
                            wallet.balance,
                            MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Biaya bulan ini",
                actions = { CardAction("Ubah", { onSheet(Sheet.EditFee) }) },
            ) {
                Text(
                    text = "Hanya informasi. Biaya adalah bagian pemasukan bulan ini yang perlu " +
                        "disisihkan; tidak pernah dipotong dari dompet. Persentasenya disimpan " +
                        "dan bisa diubah kapan saja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Biaya ${Fmt.feePercentLabel(report.feePercent)}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "dari pemasukan ${Fmt.rp(report.income)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                    Text(
                        text = Fmt.rp(report.feeExpense),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(Modifier.height(4.dp))
                AmountRow(
                    "Saldo jika fee disisihkan",
                    report.balanceAfterFee,
                    MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Perkiraan; saldo dompet tidak benar-benar dikurangi.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }

        item {
            SectionCard(title = "Simpan atau edit") {
                if (isCurrentMonth) {
                    Text(
                        text = "Bulan ini terus diperbarui secara langsung. Saat bulan berakhir, " +
                            "laporannya disimpan otomatis ke bagian Bulan tersimpan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                } else if (isAuto) {
                    Text(
                        text = "Disimpan otomatis dari transaksi. Hanya berubah kalau kamu edit manual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                } else if (isManual) {
                    Text(
                        text = "Bulan ini dikunci dengan angkamu sendiri. Update otomatis akan melewatinya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    onClick = {
                        onSheet(
                            Sheet.EditReport(
                                month = month,
                                income = report.income,
                                expense = report.expense,
                                balance = report.balance,
                                fee = Fmt.feePercentLabel(report.feePercent),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    Text(if (snapshot == null) "Simpan bulan ini secara manual" else "Edit laporan manual")
                }

                if (isManual) {
                    OutlinedActionButton(
                        text = "Kembalikan ke otomatis",
                        onClick = { resetToAuto() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isAuto && differs) {
                    OutlinedActionButton(
                        text = "Perbarui laporan otomatis",
                        onClick = { resetToAuto() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isAuto && differs) {
                    Text(
                        text = "Snapshot tersimpan berbeda dari transaksi di atas.",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusOrange,
                    )
                }
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item {
            Text(
                text = "Bulan tersimpan",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (data.snapshots.isEmpty()) {
            item {
                EmptyState(
                    title = "Belum ada bulan tersimpan",
                    body = "Bulan yang sudah selesai disimpan otomatis saat aplikasi pertama kali " +
                        "dibuka di bulan baru. Kamu juga bisa menyimpannya manual di atas.",
                )
            }
        } else {
            items(data.snapshots, key = { "snap-${it.month}" }) { saved ->
                SnapshotRow(saved)
            }
        }
    }
}

@Composable
private fun MonthSwitcher(
    month: String,
    count: Int,
    older: String?,
    newer: String?,
    onOlder: (String) -> Unit,
    onNewer: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appCard()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { older?.let(onOlder) }, enabled = older != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = Fmt.monthLabel(month),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "$count transaksi",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        IconButton(onClick = { newer?.let(onNewer) }, enabled = newer != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Bulan berikutnya")
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun signedRp(value: Long): String =
    if (value < 0) "- " + Fmt.rp(-value) else "+ " + Fmt.rp(value)

/** Rounded month-over-month change, or null when the previous month has no base. */
private fun changeLabel(current: Long, previous: Long): String? {
    if (previous <= 0) return null
    if (current == previous) return "sama vs bulan lalu"
    val percent = ((current - previous) * 100.0 / previous).roundToInt()
    return (if (percent > 0) "+$percent%" else "$percent%") + " vs bulan lalu"
}
