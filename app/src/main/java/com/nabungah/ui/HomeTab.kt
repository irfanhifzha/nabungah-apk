package com.nabungah.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** Home: balance at a glance, quick entry, wallets, goals and the last few movements. */
@Composable
fun HomeTab(data: AppData, onSheet: (Sheet) -> Unit, onSeeAll: () -> Unit) {
    val recent = data.transactions.take(5)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListBottomPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { GreetingHeader() }
        item { BalanceHero(data) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSheet(Sheet.NewTransaction()) },
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
                OutlinedButton(
                    onClick = { onSheet(Sheet.Transfer) },
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Text("Transfer") }
                OutlinedButton(
                    onClick = { onSheet(Sheet.AddGoal) },
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Text("Goal") }
            }
        }

        if (data.quickActions.isNotEmpty()) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Quick actions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onSheet(Sheet.AddQuickAction) }) {
                            Text("+ New")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(data.quickActions, key = { it.id }) { action ->
                            QuickActionTile(action) { onSheet(Sheet.NewTransaction(action)) }
                        }
                    }
                }
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
            GoalsSection(
                goals = data.goals,
                onAdd = { onSheet(Sheet.AddGoal) },
                onEdit = { onSheet(Sheet.EditGoal(it)) },
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = "Recent activity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (data.transactions.isNotEmpty()) {
                    TextButton(onClick = onSeeAll) { Text("See all") }
                }
            }
        }

        if (recent.isEmpty()) {
            item {
                EmptyState(
                    title = "No transactions yet",
                    body = "Log your first income or expense to see it here.",
                    actionLabel = "Add transaction",
                    onAction = { onSheet(Sheet.NewTransaction()) },
                )
            }
        } else {
            items(recent, key = { it.id }) { trx ->
                TransactionRow(trx, data.walletNames[trx.walletId] ?: "Unknown Wallet")
            }
        }
    }
}

// ------------------------------------------------------------------- header

@Composable
private fun GreetingHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nabungah",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Your money, where it sits.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SoftTeal)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(IncomeGreen),
            )
            Text(
                text = "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// --------------------------------------------------------------------- hero

@Composable
private fun BalanceHero(data: AppData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(HeroTop, HeroBottom)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Total balance",
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
                label = "Income",
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
                label = "Expense",
                value = "- " + Fmt.rp(data.monthExpense),
                color = HeroExpense,
                modifier = Modifier.weight(1f),
            )
        }
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
private fun QuickActionTile(action: QuickAction, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
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
            fontWeight = FontWeight.SemiBold,
            color = if (action.type == "income") IncomeGreen else ExpenseRed,
        )
    }
}

// ------------------------------------------------------------------ wallets

@Composable
private fun WalletsSection(data: AppData, onAdd: () -> Unit, onTransfer: () -> Unit) {
    SectionCard(
        title = "Wallets",
        actions = {
            CardAction("+ Add", onAdd)
            if (data.wallets.size > 1) CardAction("Transfer", onTransfer)
        },
    ) {
        if (data.wallets.isEmpty()) {
            EmptyState(
                title = "No wallet yet",
                body = "A wallet is where a balance sits: cash, bank, e-wallet.",
                actionLabel = "Add wallet",
                onAction = onAdd,
            )
        } else {
            data.wallets.forEachIndexed { index, wallet ->
                if (index > 0) {
                    HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SoftTeal),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = wallet.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = Fmt.rp(wallet.balance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------- goals

@Composable
private fun GoalsSection(goals: List<Goal>, onAdd: () -> Unit, onEdit: (Goal) -> Unit) {
    SectionCard(
        title = "Savings goals",
        actions = { CardAction("+ Add", onAdd) },
    ) {
        if (goals.isEmpty()) {
            EmptyState(
                title = "Nothing saved yet",
                body = "Goals track progress on their own; they are not linked to wallets.",
                actionLabel = "Create goal",
                onAction = onAdd,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                goals.forEach { goal -> GoalProgress(goal, onEdit) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Goals are not connected to wallets. Move the money manually.",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
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
                text = "$percent% · ${Fmt.rp(goal.savedAmount)}",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "of ${Fmt.rp(goal.targetAmount)}",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
        }
    }
}
