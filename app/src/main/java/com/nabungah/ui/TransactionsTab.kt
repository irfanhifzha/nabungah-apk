package com.nabungah.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nabungah.data.AppData

/** Activity: search, filter, and read the transaction log grouped by day. */
@Composable
fun TransactionsTab(data: AppData) {
    var query by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(20) }

    val months = remember(data.transactions) {
        data.transactions.map { it.month }.distinct().sortedDescending()
    }
    LaunchedEffect(months) {
        if (month.isNotEmpty() && month !in months) month = ""
    }
    LaunchedEffect(query, month, type) { limit = 20 }

    val matches = remember(data.transactions, data.walletNames, query, month, type) {
        val needle = query.trim()
        data.transactions.filter { trx ->
            (month.isEmpty() || trx.month == month) &&
                (type.isEmpty() || trx.type == type) &&
                (needle.isEmpty() ||
                    trx.title.contains(needle, ignoreCase = true) ||
                    trx.note.contains(needle, ignoreCase = true) ||
                    (data.walletNames[trx.walletId] ?: "").contains(needle, ignoreCase = true))
        }
    }
    val shown = remember(matches, limit) { matches.take(limit) }
    val income = remember(matches) { matches.filter { it.type == "income" }.sumOf { it.amount } }
    val expense = remember(matches) { matches.filter { it.type == "expense" }.sumOf { it.amount } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListBottomPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Aktivitas",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Semua transaksi, terbaru dulu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari judul, catatan, atau dompet") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField(
                    label = "Bulan",
                    selectedLabel = if (month.isEmpty()) "Semua bulan" else Fmt.monthLabel(month),
                    options = (listOf("" to "Semua bulan") +
                        months.map { it to Fmt.monthLabel(it) }).distinctBy { it.first },
                    onSelect = { month = it },
                    modifier = Modifier.weight(1f),
                )
                PickerField(
                    label = "Tipe",
                    selectedLabel = when (type) {
                        "income" -> "Pemasukan"
                        "expense" -> "Pengeluaran"
                        else -> "Semua tipe"
                    },
                    options = listOf("" to "Semua tipe", "income" to "Pemasukan", "expense" to "Pengeluaran"),
                    onSelect = { type = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = "${matches.size} transaksi",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "+ ${Fmt.rp(income)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IncomeGreen,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "- ${Fmt.rp(expense)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ExpenseRed,
                )
            }
        }

        if (matches.isEmpty()) {
            item {
                if (data.transactions.isEmpty()) {
                    EmptyState(
                        title = "Belum ada transaksi",
                        body = "Ketuk Tambah untuk mencatat yang pertama.",
                    )
                } else {
                    EmptyState(
                        title = "Tidak ada hasil",
                        body = "Coba bulan, tipe, atau kata pencarian lain.",
                        actionLabel = "Hapus filter",
                        onAction = {
                            query = ""
                            month = ""
                            type = ""
                        },
                    )
                }
            }
        } else {
            val groups = shown.groupBy { Fmt.dayKey(it.occurredAt) }
            groups.forEach { (day, dayTrx) ->
                item(key = "day-$day") {
                    Text(
                        text = Fmt.dayLabel(dayTrx.first().occurredAt),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(dayTrx, key = { it.id }) { trx ->
                    TransactionRow(trx, data.walletNames[trx.walletId] ?: "Dompet tidak dikenal")
                }
            }

            if (shown.size < matches.size) {
                item {
                    OutlinedButton(
                        onClick = { limit += 20 },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) {
                        Text("Tampilkan ${minOf(20, matches.size - shown.size)} lagi")
                    }
                }
            }
        }
    }
}
