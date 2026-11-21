package com.nabungah.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nabungah.data.AppData
import com.nabungah.data.Goal
import com.nabungah.data.QuickAction
import kotlin.math.roundToInt

/** Home: balance, wallets, quick entry, goals and the last few movements. */
@Composable
fun HomeTab(data: AppData, onSheet: (Sheet) -> Unit, onSeeAll: () -> Unit) {
    val openTransaction: (com.nabungah.data.Trx) -> Unit = { onSheet(Sheet.TransactionDetail(it)) }
    val recent = data.transactions.take(5)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListBottomPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { GreetingHeader() }
        item { BalanceHero(data) }
        if (data.wallets.isEmpty() || data.transactions.isEmpty()) {
            item {
                OnboardingCard(
                    hasWallet = data.wallets.isNotEmpty(),
                    onAddWallet = { onSheet(Sheet.AddWallet) },
                    onAddTransaction = { onSheet(Sheet.NewTransaction()) },
                )
            }
        }
        item {
            WalletsSection(
                data = data,
                onAdd = { onSheet(Sheet.AddWallet) },
                onTransfer = { onSheet(Sheet.Transfer) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSheet(Sheet.NewTransaction()) },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah")
                }
                OutlinedButton(
                    onClick = { onSheet(Sheet.Transfer) },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Transfer") }
                OutlinedButton(
                    onClick = { onSheet(Sheet.AddGoal) },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Target") }
            }
        }

        if (data.quickActions.isNotEmpty()) {
            item {
                QuickActionsSection(
                    actions = data.quickActions,
                    onUse = { onSheet(Sheet.NewTransaction(it)) },
                    onAdd = { onSheet(Sheet.AddQuickAction) },
                )
            }
        }

        item {
            GoalsSection(
                goals = data.goals,
                onAdd = { onSheet(Sheet.AddGoal) },
                onEdit = { onSheet(Sheet.EditGoal(it)) },
            )
        }

        item {
            SectionHeader(
                title = "Aktivitas terakhir",
                modifier = Modifier.padding(top = 4.dp),
                actions = {
                    if (data.transactions.isNotEmpty()) CardAction("Lihat semua", onSeeAll)
                },
            )
        }

        if (recent.isEmpty()) {
            item {
                EmptyState(
                    title = "Belum ada transaksi",
                    body = "Catat pemasukan atau pengeluaran pertamamu untuk melihatnya di sini.",
                    actionLabel = "Tambah transaksi",
                    onAction = { onSheet(Sheet.NewTransaction()) },
                )
            }
        } else {
            items(recent, key = { it.id }) { trx ->
                TransactionRow(
                    trx = trx,
                    walletName = data.walletNames[trx.walletId] ?: "Dompet tidak dikenal",
                    onClick = { openTransaction(trx) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------- header

@Composable
private fun GreetingHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Nabungah",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Uangmu, di mana pun berada.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

// --------------------------------------------------------------- onboarding

@Composable
private fun OnboardingCard(
    hasWallet: Boolean,
    onAddWallet: () -> Unit,
    onAddTransaction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appCard(color = SoftTeal)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (hasWallet) "Satu langkah lagi" else "Mulai dalam 3 langkah",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (hasWallet) {
                "Catat transaksi pertamamu supaya saldo dan laporan mulai bergerak."
            } else {
                "Buat dompet → catat transaksi → buat target. Semua data tetap di perangkat ini."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        OutlinedActionButton(
            text = if (hasWallet) "Catat transaksi pertama" else "Buat dompet pertama",
            onClick = if (hasWallet) onAddTransaction else onAddWallet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// --------------------------------------------------------------------- hero

@Composable
private fun BalanceHero(data: AppData) {
    // Live preview from the fee saved in Settings; snapshots keep their own historical copy.
    val feePercent = data.feePercent
    val fee = Fmt.feeExpense(data.monthIncome, feePercent)
    val afterFee = data.totalBalance - fee
    val afterFeeText = if (afterFee < 0) "- " + Fmt.rp(-afterFee) else Fmt.rp(afterFee)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(HeroTop, HeroBottom)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Total saldo",
                style = MaterialTheme.typography.labelLarge,
                color = OnHeroMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Fmt.monthLabel(data.currentMonth),
                style = MaterialTheme.typography.labelSmall,
                color = OnHeroMuted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = Fmt.rp(data.totalBalance),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroStat(
                label = "Pemasukan",
                value = "+ " + Fmt.rp(data.monthIncome),
                color = HeroIncome,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(Color(0x33FFFFFF)),
            )
            Spacer(Modifier.width(14.dp))
            HeroStat(
                label = "Pengeluaran",
                value = "- " + Fmt.rp(data.monthExpense),
                color = HeroExpense,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x33FFFFFF)),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Jika fee disisihkan",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnHeroMuted,
                )
                Text(
                    text = afterFeeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Fee ${Fmt.feePercentLabel(feePercent)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnHeroMuted,
                )
                Text(
                    text = "- " + Fmt.rp(fee),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HeroExpense,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = "Perkiraan dari pemasukan bulan ini; fee tidak dipotong dari dompet.",
            style = MaterialTheme.typography.labelSmall,
            color = OnHeroMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnHeroMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------ quick actions

@Composable
private fun QuickActionsSection(
    actions: List<QuickAction>,
    onUse: (QuickAction) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "Aksi cepat",
            actions = { CardAction("+ Baru", onAdd) },
        )
        Text(
            text = "Template nominal tetap untuk mencatat transaksi harian tanpa mengetik ulang.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(actions, key = { it.id }) { action ->
                QuickActionTile(action) { onUse(action) }
            }
        }
    }
}

@Composable
private fun QuickActionTile(action: QuickAction, onClick: () -> Unit) {
    val isIncome = action.type == "income"
    val accent = if (isIncome) IncomeGreen else ExpenseRed
    val tint = if (isIncome) IncomeTint else ExpenseTint

    Column(
        modifier = Modifier
            .width(178.dp)
            .height(116.dp)
            .appCard(radius = 18.dp, color = tint)
            .pressableCard(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isIncome) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = (if (isIncome) "+ " else "- ") + Fmt.rp(action.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

// ------------------------------------------------------------------ wallets

@Composable
private fun WalletsSection(data: AppData, onAdd: () -> Unit, onTransfer: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "Dompet",
            actions = {
                CardAction("+ Tambah", onAdd)
                if (data.wallets.size > 1) CardAction("Transfer", onTransfer)
            },
        )
        if (data.wallets.isEmpty()) {
            EmptyState(
                title = "Belum ada dompet",
                body = "Dompet adalah tempat saldo berada: tunai, bank, atau e-wallet.",
                actionLabel = "Tambah dompet",
                onAction = onAdd,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.wallets.forEach { wallet ->
                    WalletCard(
                        name = wallet.name,
                        balance = wallet.balance,
                        total = data.totalBalance,
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletCard(
    name: String,
    balance: Long,
    total: Long,
) {
    val rawShare = if (total <= 0) 0f else (balance.toFloat() / total).coerceIn(0f, 1f)
    val shareProgress by animateFloatAsState(
        targetValue = rawShare,
        animationSpec = tween(durationMillis = 500),
        label = "walletShare",
    )
    val share = (rawShare * 100).roundToInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appCard()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SoftTeal),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$share% dari total saldo",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = Fmt.rp(balance),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { shareProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = CardBorder,
        )
    }
}

// -------------------------------------------------------------------- goals

@Composable
private fun GoalsSection(goals: List<Goal>, onAdd: () -> Unit, onEdit: (Goal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "Target tabungan",
            actions = { CardAction("+ Tambah", onAdd) },
        )
        Text(
            text = "Catat progres menabung secara manual. Target tidak memotong saldo dompet.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        if (goals.isEmpty()) {
            EmptyState(
                title = "Belum ada target",
                body = "Target dihitung sendiri dan tidak terhubung ke dompet.",
                actionLabel = "Buat target",
                onAction = onAdd,
            )
        } else {
            goals.forEach { goal -> GoalProgress(goal, onEdit) }
            Text(
                text = "Target tidak terhubung ke dompet. Pindahkan uangnya secara manual.",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun GoalProgress(goal: Goal, onEdit: (Goal) -> Unit) {
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
            .appCard()
            .pressableCard { onEdit(goal) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when (goal.status) {
                    "completed" -> "Selesai"
                    "cancelled" -> "Dibatalkan"
                    else -> "Berjalan"
                },
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (goal.description.isNotBlank()) {
            Text(
                text = goal.description,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
                text = "$percent% · ${Fmt.rp(goal.savedAmount)}",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "dari ${Fmt.rp(goal.targetAmount)}",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
        }
    }
}
