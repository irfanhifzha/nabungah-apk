package com.nabungah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nabungah.data.Snapshot
import com.nabungah.data.TransferLog
import com.nabungah.data.Trx

/** Label on the left, right-aligned amount. Shared by cards and snapshot rows. */
@Composable
fun AmountRow(label: String, amount: Long, color: Color, prefix: String = "", money: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = prefix + if (money) Fmt.rp(amount) else amount.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
fun TransactionRow(trx: Trx, walletName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appCard()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row {
            val accent = if (trx.type == "income") IncomeGreen else ExpenseRed
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 10.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (trx.type == "income") Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = trx.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${Fmt.timestamp(trx.occurredAt)} · $walletName",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                if (trx.note.isNotBlank()) {
                    Text(
                        text = trx.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = (if (trx.type == "income") "+ " else "- ") + Fmt.rp(trx.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (trx.type == "income") IncomeGreen else ExpenseRed,
                modifier = Modifier.padding(top = 2.dp, start = 10.dp),
                maxLines = 1,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            CopyButton(text = trxCopyText(trx, walletName))
        }
    }
}

/** Activity-only transfer entry: informational, never an income or expense row. */
@Composable
fun TransferRow(transfer: TransferLog, walletNames: Map<Long, String>) {
    val from = walletNames[transfer.fromWalletId] ?: "Dompet dihapus"
    val to = walletNames[transfer.toWalletId] ?: "Dompet dihapus"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appCard()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(SoftTeal),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⇄",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Transfer",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$from → $to",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Text(
                text = Fmt.timestamp(transfer.occurredAt),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        Text(
            text = Fmt.rp(transfer.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 10.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun SnapshotRow(snapshot: Snapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appCard()
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Fmt.monthLabel(snapshot.month),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (snapshot.isManual) "Manual" else "Otomatis",
                style = MaterialTheme.typography.labelSmall,
                color = if (snapshot.isManual) StatusOrange else IncomeGreen,
                modifier = Modifier.padding(end = 2.dp),
            )
            CopyButton(text = snapshotCopyText(snapshot))
        }
        HorizontalDivider(
            color = CardBorder,
            modifier = Modifier.padding(top = 6.dp, end = 8.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            AmountRow("Pemasukan", snapshot.totalIncome, IncomeGreen, prefix = "+ ")
            AmountRow("Pengeluaran", snapshot.totalExpense, ExpenseRed, prefix = "- ")
            AmountRow("Saldo", snapshot.totalBalance, MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Biaya ${Fmt.feePercentLabel(snapshot.feePercent)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Fmt.rp(snapshot.feeExpense),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Diperbarui ${Fmt.timestamp(snapshot.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}
