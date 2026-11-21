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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nabungah.data.ActivityEntry
import com.nabungah.data.ActivityPage
import com.nabungah.data.ActivityQuery
import com.nabungah.data.AppData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val VIEW_LIST = "list"
private const val VIEW_CALENDAR = "calendar"

/** Activity: search, filters, a day-grouped list, and a month calendar with activity dots. */
@Composable
fun TransactionsTab(vm: MainViewModel, data: AppData, onSheet: (Sheet) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var walletFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var view by rememberSaveable { mutableStateOf(VIEW_LIST) }
    var calendarMonth by rememberSaveable { mutableStateOf(data.currentMonth) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmBulk by rememberSaveable { mutableStateOf(false) }
    var page by remember { mutableStateOf(ActivityPage()) }
    var calendarEntries by remember { mutableStateOf<List<ActivityEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val months = data.activityMonths
    LaunchedEffect(months) {
        if (month.isNotEmpty() && month !in months) month = ""
    }
    LaunchedEffect(calendarMonth) { selectedDay = null }
    LaunchedEffect(selecting) { if (!selecting) selectedIds = emptySet() }
    LaunchedEffect(vm.dataRevision) {
        if (selecting) {
            selecting = false
            selectedIds = emptySet()
        }
    }

    LaunchedEffect(query, month, type, walletFilter, vm.dataRevision) {
        loading = true
        pageError = null
        if (query.isNotBlank()) delay(250)
        try {
            page = vm.activityPage(
                ActivityQuery(
                    query = query.trim(),
                    month = month,
                    type = type,
                    walletId = walletFilter,
                    limit = 20,
                    offset = 0,
                ),
            )
        } catch (e: Exception) {
            pageError = e.message ?: "Aktivitas gagal dimuat"
            page = ActivityPage()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(view, calendarMonth, query, type, walletFilter, vm.dataRevision) {
        if (view != VIEW_CALENDAR) {
            calendarEntries = emptyList()
            return@LaunchedEffect
        }
        if (query.isNotBlank()) delay(250)
        calendarEntries = runCatching {
            vm.activityMonth(
                ActivityQuery(
                    query = query.trim(),
                    month = calendarMonth,
                    type = type,
                    walletId = walletFilter,
                ),
            ).items
        }.getOrDefault(emptyList())
    }

    val selectedItems = remember(calendarEntries, selectedDay) {
        val day = selectedDay ?: return@remember emptyList()
        calendarEntries
            .filter { Fmt.dayKey(it.occurredAt) == day }
            .sortedByDescending { it.occurredAt }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListBottomPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aktivitas",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (selecting) "${selectedIds.size} transaksi dipilih"
                        else "Semua transaksi dan transfer, terbaru dulu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
                if (view == VIEW_LIST) {
                    OutlinedActionButton(
                        text = if (selecting) "Batal" else "Pilih",
                        onClick = {
                            selecting = !selecting
                            selectedIds = emptySet()
                        },
                        compact = true,
                        color = if (selecting) Muted else MaterialTheme.colorScheme.primary,
                    )
                }
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
                    onClick = { view = VIEW_CALENDAR; selecting = false },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    FilterChip(
                        selected = walletFilter == null,
                        onClick = { walletFilter = null },
                        label = { Text("Semua dompet") },
                    )
                }
                if (data.wallets.isNotEmpty()) {
                    PickerField(
                        label = "Dompet",
                        selectedLabel = data.wallets.firstOrNull { it.id == walletFilter }?.name
                            ?: "Semua dompet",
                        options = listOf<Pair<Long?, String>>(null to "Semua dompet") +
                            data.wallets.map { it.id to it.name },
                        onSelect = { walletFilter = it },
                    )
                }
            }
            item {
                ActivityCalendar(
                    month = calendarMonth,
                    items = calendarEntries,
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
                        ActivityRow(
                            item = item,
                            data = data,
                            onClick = {
                                item.trx?.let { onSheet(Sheet.TransactionDetail(it)) }
                            },
                        )
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PickerField(
                        label = "Dompet",
                        selectedLabel = data.wallets.firstOrNull { it.id == walletFilter }?.name
                            ?: "Semua dompet",
                        options = listOf<Pair<Long?, String>>(null to "Semua dompet") +
                            data.wallets.map { it.id to it.name },
                        onSelect = { walletFilter = it },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedActionButton(
                        text = "Bulan ini",
                        onClick = { month = data.currentMonth },
                        compact = true,
                        color = if (month == data.currentMonth) MaterialTheme.colorScheme.primary else Muted,
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${page.total} aktivitas",
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "+ ${Fmt.rp(page.income)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = IncomeGreen,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "- ${Fmt.rp(page.expense)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ExpenseRed,
                    )
                }
            }

            if (selecting) {
                item {
                    OutlinedActionButton(
                        text = if (selectedIds.isEmpty()) "Pilih transaksi untuk dihapus"
                        else "Hapus ${selectedIds.size} transaksi",
                        onClick = { confirmBulk = true },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (pageError != null) {
                item {
                    Text(
                        text = pageError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (loading && page.items.isEmpty()) {
                item {
                    Text(
                        text = "Memuat aktivitas…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else if (page.items.isEmpty()) {
                item {
                    if (data.transactionCount == 0 && data.transferCount == 0) {
                        EmptyState(
                            title = "Belum ada aktivitas",
                            body = "Catat pemasukan atau pengeluaran pertamamu.",
                            actionLabel = "Tambah transaksi",
                            onAction = { onSheet(Sheet.NewTransaction()) },
                        )
                    } else {
                        EmptyState(
                            title = "Tidak ada hasil",
                            body = "Coba bulan, dompet, tipe, atau kata pencarian lain.",
                            actionLabel = "Hapus filter",
                            onAction = {
                                query = ""
                                month = ""
                                type = ""
                                walletFilter = null
                            },
                        )
                    }
                }
            } else {
                val groups = page.items.groupBy { Fmt.dayKey(it.occurredAt) }
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
                        ActivityRow(
                            item = item,
                            data = data,
                            selecting = selecting,
                            selected = item.trx?.id in selectedIds,
                            onClick = {
                                val trx = item.trx
                                when {
                                    selecting && trx != null -> {
                                        selectedIds = if (trx.id in selectedIds) {
                                            selectedIds - trx.id
                                        } else {
                                            selectedIds + trx.id
                                        }
                                    }
                                    trx != null -> onSheet(Sheet.TransactionDetail(trx))
                                }
                            },
                        )
                    }
                }

                if (page.items.size < page.total) {
                    item {
                        OutlinedButton(
                            onClick = {
                                if (!loadingMore) {
                                    scope.launch {
                                        loadingMore = true
                                        try {
                                            val next = vm.activityPage(
                                                ActivityQuery(
                                                    query = query.trim(),
                                                    month = month,
                                                    type = type,
                                                    walletId = walletFilter,
                                                    limit = 20,
                                                    offset = page.items.size,
                                                ),
                                            )
                                            page = page.copy(
                                                items = page.items + next.items,
                                                total = next.total,
                                                income = next.income,
                                                expense = next.expense,
                                            )
                                        } catch (e: Exception) {
                                            pageError = e.message ?: "Aktivitas gagal dimuat"
                                        } finally {
                                            loadingMore = false
                                        }
                                    }
                                }
                            },
                            enabled = !loadingMore,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                        ) {
                            Text(
                                if (loadingMore) "Memuat…"
                                else "Tampilkan ${minOf(20, page.total - page.items.size)} lagi",
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmBulk) {
        ConfirmDialog(
            title = "Hapus ${selectedIds.size} transaksi?",
            message = "Semua transaksi terpilih dihapus dalam satu langkah. Saldo dompet " +
                "disesuaikan untuk membatalkan efek masing-masing transaksi.",
            confirmLabel = "Hapus Semua",
            onConfirm = {
                vm.deleteTransactions(selectedIds)
                selectedIds = emptySet()
                selecting = false
                confirmBulk = false
            },
            onDismiss = { confirmBulk = false },
        )
    }
}

@Composable
private fun ActivityRow(
    item: ActivityEntry,
    data: AppData,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val trx = item.trx
    if (trx != null) {
        TransactionRow(
            trx = trx,
            walletName = data.walletNames[trx.walletId] ?: "Dompet tidak dikenal",
            onClick = onClick,
            selectionActive = selecting,
            selected = selected,
        )
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
    items: List<ActivityEntry>,
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
    val a11y = buildString {
        append("Tanggal $day")
        if ("income" in types) append(", pemasukan")
        if ("expense" in types) append(", pengeluaran")
        if ("transfer" in types) append(", transfer")
        if (selected) append(", dipilih")
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .padding(2.dp)
            .clip(shape)
            .background(if (selected) SoftTeal else Color.Transparent)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .semantics { contentDescription = a11y }
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
