package com.nabungah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nabungah.data.AppData
import com.nabungah.data.TransferLog
import com.nabungah.data.Trx
import java.util.Calendar
import java.util.Locale

/** One timeline row: a real transaction or a transfer-log entry (never mixed into reports). */
private data class ActivityItem(
    val occurredAt: Long,
    val month: String,
    val type: String,
    val trx: Trx? = null,
    val transfer: TransferLog? = null,
) {
    val key: String
        get() = when {
            trx != null -> "trx-${trx.id}"
            transfer != null -> "transfer-${transfer.id}"
            else -> "unknown"
        }
}

private const val VIEW_LIST = "list"
private const val VIEW_CALENDAR = "calendar"

/** Activity: search, filters, a day-grouped list, and a month calendar with activity dots. */
@Composable
fun TransactionsTab(data: AppData) {
    var query by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(20) }
    var view by rememberSaveable { mutableStateOf(VIEW_LIST) }
    var calendarMonth by rememberSaveable { mutableStateOf(data.currentMonth) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }

    val months = remember(data.transactions, data.transfers) {
        (data.transactions.map { it.month } + data.transfers.map { it.month })
            .distinct()
            .sortedDescending()
    }
    LaunchedEffect(months) {
        if (month.isNotEmpty() && month !in months) month = ""
    }
    LaunchedEffect(query, month, type) { limit = 20 }
    LaunchedEffect(calendarMonth) { selectedDay = null }

    val allItems = remember(data.transactions, data.transfers) {
        data.transactions.map {
            ActivityItem(it.occurredAt, it.month, it.type, trx = it)
        } + data.transfers.map {
            ActivityItem(it.occurredAt, it.month, "transfer", transfer = it)
        }
    }

    val matches = remember(allItems, data.walletNames, query, month, type) {
        val needle = query.trim()
        allItems
            .filter { item ->
                (month.isEmpty() || item.month == month) &&
                    (type.isEmpty() || item.type == type) &&
                    (needle.isEmpty() || item.matches(needle, data.walletNames))
            }
            .sortedByDescending { it.occurredAt }
    }
    val shown = remember(matches, limit) { matches.take(limit) }
    val income = remember(matches) {
        matches.sumOf { it.trx?.takeIf { trx -> trx.type == "income" }?.amount ?: 0L }
    }
    val expense = remember(matches) {
        matches.sumOf { it.trx?.takeIf { trx -> trx.type == "expense" }?.amount ?: 0L }
    }

    val calendarItems = remember(allItems, data.walletNames, calendarMonth, query, type) {
        val needle = query.trim()
        allItems.filter { item ->
            item.month == calendarMonth &&
                (type.isEmpty() || item.type == type) &&
                (needle.isEmpty() || item.matches(needle, data.walletNames))
        }
    }
    val selectedItems = remember(calendarItems, selectedDay) {
        val day = selectedDay ?: return@remember emptyList()
        calendarItems
            .filter { Fmt.dayKey(it.occurredAt) == day }
            .sortedByDescending { it.occurredAt }
    }

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
                    text = "Semua transaksi dan transfer, terbaru dulu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = view == VIEW_LIST,
                    onClick = { view = VIEW_LIST },
                    label = { Text("Daftar") },
                )
                FilterChip(
                    selected = view == VIEW_CALENDAR,
                    onClick = { view = VIEW_CALENDAR },
                    label = { Text("Kalender") },
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

        if (view == VIEW_CALENDAR) {
            item {
                PickerField(
                    label = "Tipe",
                    selectedLabel = type.label(),
                    options = listOf(
                        "" to "Semua tipe",
                        "income" to "Pemasukan",
                        "expense" to "Pengeluaran",
                        "transfer" to "Transfer",
                    ),
                    onSelect = { type = it },
                )
            }
            item {
                ActivityCalendar(
                    month = calendarMonth,
                    items = calendarItems,
                    selectedDay = selectedDay,
                    onMonthChange = { calendarMonth = it },
                    onDaySelect = { selectedDay = if (selectedDay == it) null else it },
                )
            }

            if (selectedDay == null) {
                item {
                    Text(
                        text = "Ketuk tanggal untuk melihat aktivitas hari itu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Fmt.dayLabelFromKey(selectedDay!!),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${selectedItems.size} aktivitas",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                }
                if (selectedItems.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Tidak ada aktivitas",
                            body = "Tidak ada pemasukan, pengeluaran, atau transfer pada tanggal ini.",
                        )
                    }
                } else {
                    items(selectedItems, key = { it.key }) { item ->
                        ActivityRow(item, data)
                    }
                }
            }
        } else {
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
                        selectedLabel = type.label(),
                        options = listOf(
                            "" to "Semua tipe",
                            "income" to "Pemasukan",
                            "expense" to "Pengeluaran",
                            "transfer" to "Transfer",
                        ),
                        onSelect = { type = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${matches.size} aktivitas",
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
                    if (data.transactions.isEmpty() && data.transfers.isEmpty()) {
                        EmptyState(
                            title = "Belum ada aktivitas",
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
                groups.forEach { (day, dayItems) ->
                    item(key = "day-$day") {
                        Text(
                            text = Fmt.dayLabel(dayItems.first().occurredAt),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Muted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(dayItems, key = { it.key }) { item ->
                        ActivityRow(item, data)
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
}

@Composable
private fun ActivityRow(item: ActivityItem, data: AppData) {
    val trx = item.trx
    if (trx != null) {
        TransactionRow(trx, data.walletNames[trx.walletId] ?: "Dompet tidak dikenal")
    } else {
        item.transfer?.let { transfer -> TransferRow(transfer, data.walletNames) }
    }
}

private fun String.label(): String = when (this) {
    "income" -> "Pemasukan"
    "expense" -> "Pengeluaran"
    "transfer" -> "Transfer"
    else -> "Semua tipe"
}

// ------------------------------------------------------------------ calendar

@Composable
private fun ActivityCalendar(
    month: String,
    items: List<ActivityItem>,
    selectedDay: String?,
    onMonthChange: (String) -> Unit,
    onDaySelect: (String) -> Unit,
) {
    val parts = remember(month) { month.split("-") }
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val monthNumber = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val firstDay = remember(year, monthNumber) {
        Calendar.getInstance().apply {
            clear()
            set(year, monthNumber - 1, 1)
        }
    }
    // Monday-first grid; Calendar uses Sunday = 1.
    val leadingBlanks = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val dayCount = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
    val byDay = remember(items) { items.groupBy { Fmt.dayKey(it.occurredAt) } }
    val income = items.sumOf { it.trx?.takeIf { trx -> trx.type == "income" }?.amount ?: 0L }
    val expense = items.sumOf { it.trx?.takeIf { trx -> trx.type == "expense" }?.amount ?: 0L }

    val cells = remember(leadingBlanks, dayCount, year, monthNumber) {
        buildList<Int?> {
            repeat(leadingBlanks) { add(null) }
            for (day in 1..dayCount) add(day)
            while (size % 7 != 0) add(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appCard()
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(shiftMonth(month, -1)) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Bulan sebelumnya",
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = Fmt.monthLabel(month),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${items.size} aktivitas · + ${Fmt.rp(income)} · - ${Fmt.rp(expense)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { onMonthChange(shiftMonth(month, 1)) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Bulan berikutnya",
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val key = String.format(Locale.US, "%04d-%02d-%02d", year, monthNumber, day)
                        val dayTypes = byDay[key].orEmpty().map { it.type }.toSet()
                        CalendarDay(
                            day = day,
                            selected = key == selectedDay,
                            types = dayTypes,
                            modifier = Modifier.weight(1f),
                            onClick = { onDaySelect(key) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    day: Int,
    selected: Boolean,
    types: Set<String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(46.dp)
            .padding(2.dp)
            .clip(shape)
            .background(if (selected) SoftTeal else Color.Transparent)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (types.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                color = if (types.isEmpty()) Muted else MaterialTheme.colorScheme.onSurface,
            )
            if (types.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if ("income" in types) TypeDot(IncomeGreen)
                    if ("expense" in types) TypeDot(ExpenseRed)
                    if ("transfer" in types) TypeDot(MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun TypeDot(color: Color) {
    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color))
}

private fun shiftMonth(month: String, offset: Int): String {
    val parts = month.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return month
    val monthNumber = parts.getOrNull(1)?.toIntOrNull() ?: return month
    val cal = Calendar.getInstance().apply {
        clear()
        set(year, monthNumber - 1, 1)
        add(Calendar.MONTH, offset)
    }
    return Fmt.monthKey(cal.timeInMillis)
}

private fun ActivityItem.matches(needle: String, walletNames: Map<Long, String>): Boolean {
    val trx = trx
    if (trx != null) {
        return trx.title.contains(needle, ignoreCase = true) ||
            trx.note.contains(needle, ignoreCase = true) ||
            (walletNames[trx.walletId] ?: "").contains(needle, ignoreCase = true)
    }
    val transfer = transfer ?: return false
    val from = walletNames[transfer.fromWalletId] ?: "Dompet dihapus"
    val to = walletNames[transfer.toWalletId] ?: "Dompet dihapus"
    return "transfer".contains(needle, ignoreCase = true) ||
        from.contains(needle, ignoreCase = true) ||
        to.contains(needle, ignoreCase = true)
}
