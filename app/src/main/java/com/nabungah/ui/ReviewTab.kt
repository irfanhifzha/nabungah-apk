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

/** Review: one month at a time, with the fee figure and saved snapshots. */
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
    var confirmSave by remember { mutableStateOf(false) }

    LaunchedEffect(months) {
        if (month !in months) month = months.firstOrNull() ?: data.currentMonth
    }
    LaunchedEffect(data.currentMonth) {
        if (month.isEmpty()) month = data.currentMonth
    }

    val monthTrx = remember(data.transactions, month) {
        data.transactions.filter { it.month == month }
    }
    val income = remember(monthTrx) { monthTrx.filter { it.type == "income" }.sumOf { it.amount } }
    val expense = remember(monthTrx) { monthTrx.filter { it.type == "expense" }.sumOf { it.amount } }
    val net = income - expense
    val feePercent = Fmt.parseFee(fee)
    val feeExpense = Fmt.feeExpense(income, feePercent)
    val snapshot = data.snapshots.firstOrNull { it.month == month }

    val index = months.indexOf(month)
    val older = months.getOrNull(index + 1)
    val newer = if (index > 0) months[index - 1] else null

    fun saveReview() {
        val label = Fmt.monthLabel(month)
        vm.upsertSnapshot(
            month = month,
            totalBalance = data.totalBalance,
            totalIncome = income,
            totalExpense = expense,
            feePercent = feePercent,
            feeExpense = feeExpense,
            onError = { message = it },
            onDone = { message = "Saved review for $label." },
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
                    text = "Look back, check the fee, save the month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { older?.let { month = it; message = null } }, enabled = older != null) {
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
                        text = "${monthTrx.size} transaction${if (monthTrx.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                IconButton(onClick = { newer?.let { month = it; message = null } }, enabled = newer != null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later month")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "Income",
                    value = "+ " + Fmt.rp(income),
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Expense",
                    value = "- " + Fmt.rp(expense),
                    color = ExpenseRed,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Net this month", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Income minus expense",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                Text(
                    text = (if (net >= 0) "+ " else "- ") + Fmt.rp(kotlin.math.abs(net)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (net >= 0) IncomeGreen else ExpenseRed,
                )
            }
        }

        item {
            SectionCard(title = "Fee calculator") {
                Text(
                    text = "Informational only. The fee is recorded in a snapshot, never deducted from a wallet.",
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
                            text = Fmt.rp(feeExpense),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { if (snapshot != null) confirmSave = true else saveReview() },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    Text(if (snapshot != null) "Update snapshot" else "Save review")
                }
                TextButton(
                    onClick = { onSheet(Sheet.Sync(fee)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enter numbers manually")
                }
                if (snapshot != null) {
                    Text(
                        text = "A snapshot for this month already exists. Saving overwrites it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
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
                    title = "No saved months",
                    body = "Save a review to keep a fixed record of that month.",
                )
            }
        } else {
            items(data.snapshots, key = { "snap-${it.month}" }) { saved ->
                SnapshotRow(saved)
            }
        }
    }

    if (confirmSave) {
        ConfirmDialog(
            title = "Update snapshot?",
            message = "The ${Fmt.monthLabel(month)} snapshot will be overwritten with the numbers shown now.",
            confirmLabel = "Update",
            destructive = false,
            onConfirm = { saveReview() },
            onDismiss = { confirmSave = false },
        )
    }
}
