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
import com.nabungah.data.Snapshot

/**
 * Monthly report: every figure comes from that month's transactions, so the selected month is
 * always live. Completed months also get an automatic snapshot when the calendar month rolls
 * over; editing a month by hand freezes it as `manual` until the user switches it back.
 */
@Composable
fun ReviewTab(vm: MainViewModel, data: AppData, onSheet: (Sheet) -> Unit) {
    val months = remember(data.transactions, data.snapshots, data.currentMonth) {
        (data.transactions.map { it.month } + data.snapshots.map { it.month } + data.currentMonth)
            .distinct()
            .sortedDescending()
    }
    var month by rememberSaveable { mutableStateOf(data.currentMonth) }
    var fee by rememberSaveable { mutableStateOf("2,5") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(months) {
        if (month !in months) month = months.firstOrNull() ?: data.currentMonth
    }
    LaunchedEffect(data.currentMonth) {
        if (month.isEmpty()) month = data.currentMonth
    }

    val snapshot = data.snapshots.firstOrNull { it.month == month }
    // A saved month remembers its fee; everything else starts from the 2.5% default.
    LaunchedEffect(month, snapshot?.feePercent) {
        fee = snapshot?.let { Fmt.feePercentLabel(it.feePercent) } ?: "2,5"
    }
    LaunchedEffect(month) { message = null }

    val feePercent = Fmt.parseFee(fee)
    val report = remember(data.transactions, data.totalBalance, month, feePercent) {
        buildMonthReport(month, data.transactions, data.totalBalance, feePercent)
    }

    val isCurrentMonth = month == data.currentMonth
    val isManual = snapshot?.isManual == true
    val isAuto = snapshot?.source == Snapshot.SOURCE_AUTO
    val differs = snapshot != null && (
        snapshot.totalIncome != report.income ||
            snapshot.totalExpense != report.expense ||
            snapshot.totalBalance != report.balance ||
            kotlin.math.abs(snapshot.feePercent - report.feePercent) > 0.0001
        )

    val index = months.indexOf(month)
    val older = months.getOrNull(index + 1)
    val newer = if (index > 0) months[index - 1] else null

    fun resetToAuto() {
        vm.resetSnapshotToAuto(
            month = month,
            onError = { message = it },
            onDone = {
                message = if (isCurrentMonth) {
                    "This month is live again; it will be saved automatically next month."
                } else {
                    "Automatic report refreshed for ${Fmt.monthLabel(month)}."
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
                    text = "Review",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Monthly report from your transactions, plus the fee you should set aside.",
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
                        isCurrentMonth -> "Running month"
                        isManual -> "Manual"
                        isAuto -> "Automatic"
                        else -> "Not saved"
                    },
                    color = when {
                        isCurrentMonth -> MaterialTheme.colorScheme.primary
                        isManual -> StatusOrange
                        else -> IncomeGreen
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = snapshot?.let { "Saved ${Fmt.timestamp(it.updatedAt)}" }
                        ?: if (isCurrentMonth) "Live, not saved yet" else "Not saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }

        item {
            SectionCard(title = "Month report") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "Income",
                        value = "+ " + Fmt.rp(report.income),
                        color = IncomeGreen,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Expense",
                        value = "- " + Fmt.rp(report.expense),
                        color = ExpenseRed,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                AmountRow("Net", report.net, if (report.net >= 0) IncomeGreen else ExpenseRed)
                AmountRow("Balance at month end", report.balance, MaterialTheme.colorScheme.onSurface)
                AmountRow(
                    "Transactions",
                    report.count.toLong(),
                    MaterialTheme.colorScheme.onSurface,
                    money = false,
                )
                AmountRow(
                    "Average expense",
                    report.averageExpense,
                    MaterialTheme.colorScheme.onSurface,
                )
                if (report.topExpenses.isNotEmpty()) {
                    HorizontalDivider(
                        color = CardBorder,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        text = "Biggest spending",
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
            SectionCard(title = "Fee for the month") {
                Text(
                    text = "Informational only. The fee is what you should set aside from income this " +
                        "month; it is never deducted from a wallet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = fee,
                        onValueChange = { fee = Fmt.sanitizeFeeInput(it) },
                        label = { Text("Fee %") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(130.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fee on income",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                        Text(
                            text = Fmt.rp(report.feeExpense),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${Fmt.feePercentLabel(report.feePercent)}% of ${Fmt.rp(report.income)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Save or edit") {
                if (isCurrentMonth) {
                    Text(
                        text = "This month keeps updating live. When the month ends, it is saved " +
                            "automatically and appears in Saved months.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                } else if (isAuto) {
                    Text(
                        text = "Saved automatically from transactions. It only changes when you edit it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                } else if (isManual) {
                    Text(
                        text = "This month is frozen with your manual numbers. Automatic updates skip it.",
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
                    Text(if (snapshot == null) "Save this month manually" else "Edit report manually")
                }

                if (isManual) {
                    TextButton(onClick = { resetToAuto() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Switch back to automatic")
                    }
                }
                if (isAuto && differs) {
                    TextButton(onClick = { resetToAuto() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Refresh automatic report")
                    }
                }
                if (isAuto && differs) {
                    Text(
                        text = "The saved snapshot differs from the transactions shown above.",
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
                text = "Saved months",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (data.snapshots.isEmpty()) {
            item {
                EmptyState(
                    title = "No saved months yet",
                    body = "Finished months are saved automatically the first time the app opens in " +
                        "a new month. You can also save one manually above.",
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
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { older?.let(onOlder) }, enabled = older != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier month")
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
                text = "$count transaction${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        IconButton(onClick = { newer?.let(onNewer) }, enabled = newer != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later month")
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
