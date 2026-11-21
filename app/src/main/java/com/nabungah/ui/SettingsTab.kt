package com.nabungah.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nabungah.BuildConfig
import com.nabungah.data.AppData
import com.nabungah.data.BackupPreview
import com.nabungah.data.ConsistencyReport
import com.nabungah.data.Goal
import com.nabungah.data.QuickAction
import com.nabungah.data.Wallet

/** Settings: data summary, backup, fee, setup, and all destructive tools. */
@Composable
fun SettingsTab(vm: MainViewModel, data: AppData, onSheet: (Sheet) -> Unit) {
    var backupMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var backupFailed by rememberSaveable { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var audit by remember { mutableStateOf<ConsistencyReport?>(null) }
    var auditError by rememberSaveable { mutableStateOf<String?>(null) }

    fun runAudit() {
        auditError = null
        vm.auditConsistency(
            onResult = { audit = it },
            onError = { auditError = it },
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            vm.exportBackup(
                uri = uri,
                onError = { backupMessage = it; backupFailed = true },
                onDone = { backupMessage = "Backup berhasil diekspor."; backupFailed = false },
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.previewBackup(
                uri = uri,
                onResult = { pendingUri = uri; preview = it },
                onError = { backupMessage = it; backupFailed = true },
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListBottomPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Pengaturan",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SoftTeal)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = "Semua data tersimpan di perangkat ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }

        item { DataSummary(data) }

        item {
            SectionCard(title = "Tampilan") {
                Text(
                    text = "Pilih tema aplikasi. Pengaturan ini hanya tersimpan di perangkat ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = vm.themeMode == mode,
                            onClick = { vm.updateThemeMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Backup & pemulihan") {
                Text(
                    text = "Ekspor seluruh data ke satu file JSON. Impor akan mengganti semua data " +
                        "yang ada sekarang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            exportLauncher.launch(Fmt.backupFileName(System.currentTimeMillis()))
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) { Text("Ekspor backup") }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream"),
                            )
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        border = BorderStroke(1.dp, CardBorder),
                    ) { Text("Impor") }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (data.lastBackupAt == 0L) {
                        "Belum pernah mengekspor backup dari perangkat ini."
                    } else {
                        "Terakhir diekspor ${Fmt.timestamp(data.lastBackupAt)} · " +
                            "${formatBytes(data.lastBackupBytes)} · " +
                            Fmt.backupFileName(data.lastBackupAt)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                if (backupMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = backupMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (backupFailed) MaterialTheme.colorScheme.error else IncomeGreen,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Biaya",
                actions = { CardAction("Ubah", { onSheet(Sheet.EditFee) }) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${Fmt.feePercentLabel(data.feePercent)}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "dari pemasukan bulanan",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Hanya informasi yang perlu disisihkan; tidak pernah dipotong dari " +
                        "saldo dompet. Nilai ini tersimpan dan dipakai di Beranda serta Laporan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }

        item {
            SectionCard(
                title = "Dompet",
                actions = { CardAction("+ Tambah", { onSheet(Sheet.AddWallet) }) },
            ) {
                if (data.wallets.isEmpty()) {
                    EmptyText("Belum ada dompet")
                } else {
                    data.wallets.forEachIndexed { index, wallet ->
                        if (index > 0) RowDivider()
                        WalletRow(wallet)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedActionButton(
                        text = "Transfer antar dompet",
                        onClick = { onSheet(Sheet.Transfer) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = data.wallets.size > 1,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Aksi cepat",
                actions = { CardAction("+ Baru", { onSheet(Sheet.AddQuickAction) }) },
            ) {
                Text(
                    text = "Template judul, nominal, dan tipe untuk mengisi form transaksi " +
                        "dengan satu ketukan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(6.dp))
                if (data.quickActions.isEmpty()) {
                    EmptyText("Belum ada aksi cepat")
                } else {
                    data.quickActions.forEachIndexed { index, action ->
                        if (index > 0) RowDivider()
                        QuickActionRow(action)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedActionButton(
                        text = "Hapus aksi cepat",
                        onClick = { onSheet(Sheet.DeleteQuickActions) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Target tabungan",
                actions = { CardAction("+ Tambah", { onSheet(Sheet.AddGoal) }) },
            ) {
                Text(
                    text = "Progres target diisi manual dan terpisah dari saldo dompet. " +
                        "Ketuk target untuk mengubahnya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(6.dp))
                if (data.goals.isEmpty()) {
                    EmptyText("Belum ada target")
                } else {
                    data.goals.forEachIndexed { index, goal ->
                        if (index > 0) RowDivider()
                        GoalRow(goal) { onSheet(Sheet.EditGoal(goal)) }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Kesehatan data",
                actions = { CardAction("Periksa", { runAudit() }) },
            ) {
                Text(
                    text = "Membandingkan saldo tersimpan dengan saldo awal + seluruh transaksi. " +
                        "Pemeriksaan tidak mengubah apa pun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                audit?.let { report ->
                    Spacer(Modifier.height(10.dp))
                    if (!report.hasIssues) {
                        Text(
                            text = "Semua saldo cocok. Tidak ada data yang perlu diperbaiki.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IncomeGreen,
                        )
                    } else {
                        Text(
                            text = "${report.walletDrift.size} saldo menyimpang · " +
                                "${report.monthMismatches} bulan transaksi tidak cocok · " +
                                "${report.snapshotIssues} snapshot tidak valid",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusOrange,
                        )
                        report.walletDrift.forEach { drift ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${drift.name}: tersimpan ${Fmt.rp(drift.stored)}, " +
                                    "seharusnya ${Fmt.rp(drift.expected)} " +
                                    "(selisih ${Fmt.rp(drift.difference)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedActionButton(
                            text = "Perbaiki sekarang",
                            onClick = {
                                vm.fixConsistency(onDone = { runAudit() })
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = StatusOrange,
                        )
                    }
                }
                if (auditError != null) {
                    Text(
                        text = auditError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            SectionCard(title = "Data & reset") {
                Text(
                    text = "Tindakan ini menghapus data secara permanen. Ekspor backup dulu jika ragu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                DangerAction("Hapus transaksi", data.transactions.size) {
                    onSheet(Sheet.DeleteTransactions)
                }
                Spacer(Modifier.height(8.dp))
                DangerAction("Hapus dompet", data.wallets.size) { onSheet(Sheet.DeleteWallets) }
                Spacer(Modifier.height(8.dp))
                DangerAction("Hapus snapshot bulanan", data.snapshots.size) {
                    onSheet(Sheet.DeleteSnapshots)
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Zona berbahaya",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onSheet(Sheet.DeleteAllData) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text("Hapus semua data", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Dompet, transaksi, transfer, target, snapshot",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Tentang") {
                Text(
                    text = "Nabungah ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Satu pengguna, sepenuhnya offline. Tanpa akun, akses jaringan, atau " +
                        "analitik. Data tersimpan di SQLite lokal dan hanya keluar lewat backup " +
                        "yang kamu ekspor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }
    }

    preview?.let { info ->
        val selectedUri = pendingUri
        ConfirmDialog(
            title = "Ganti data dengan backup ini?",
            message = "File versi ${info.version}, diekspor " +
                "${if (info.exportedAt > 0) Fmt.timestamp(info.exportedAt) else "tanpa tanggal"}.\n\n" +
                "${info.wallets} dompet · ${info.transactions} transaksi · " +
                "${info.transfers} transfer · ${info.goals} target · " +
                "${info.quickActions} aksi cepat · ${info.snapshots} snapshot · " +
                "biaya ${Fmt.feePercentLabel(info.feePercent)}%.\n\n" +
                "Seluruh data saat ini akan diganti dan tidak bisa dikembalikan.",
            confirmLabel = "Ganti data",
            onConfirm = {
                // ConfirmDialog calls onDismiss before onConfirm, which clears pendingUri.
                if (selectedUri != null) {
                    vm.importBackup(
                        uri = selectedUri,
                        onError = { backupMessage = it; backupFailed = true },
                        onDone = { backupMessage = "Backup berhasil dipulihkan."; backupFailed = false },
                    )
                }
            },
            onDismiss = { preview = null; pendingUri = null },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
private fun DataSummary(data: AppData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(HeroTop, HeroBottom)))
            .padding(18.dp),
    ) {
        Text(
            text = "Ringkasan data",
            style = MaterialTheme.typography.labelMedium,
            color = OnHeroMuted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = Fmt.rp(data.totalBalance),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryMetric("Dompet", data.wallets.size.toString(), Modifier.weight(1f))
            SummaryMetric("Transaksi", data.transactions.size.toString(), Modifier.weight(1f))
            SummaryMetric("Target", data.goals.size.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(Color(0x1AFFFFFF))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnHeroMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = CardBorder,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun WalletRow(wallet: Wallet) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(SoftTeal),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = wallet.name.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = wallet.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Saldo saat ini",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        Text(
            text = Fmt.rp(wallet.balance),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QuickActionRow(action: QuickAction) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (action.type == "income") "Pemasukan" else "Pengeluaran",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        Text(
            text = (if (action.type == "income") "+ " else "- ") + Fmt.rp(action.amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (action.type == "income") IncomeGreen else ExpenseRed,
        )
    }
}

@Composable
private fun GoalRow(goal: Goal, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .pressableCard(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (goal.description.isNotBlank()) {
                Text(
                    text = goal.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${Fmt.rp(goal.savedAmount)} dari ${Fmt.rp(goal.targetAmount)}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Edit ${goal.title}",
            tint = Muted,
        )
    }
}

@Composable
private fun DangerAction(label: String, count: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
        )
        Text("$count", color = Muted)
    }
}
