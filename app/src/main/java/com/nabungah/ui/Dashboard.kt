package com.nabungah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nabungah.data.Goal
import com.nabungah.data.QuickAction

sealed interface Sheet {
    data object AddWallet : Sheet
    data class NewTransaction(val prefill: QuickAction? = null) : Sheet
    data object AddQuickAction : Sheet
    data object AddGoal : Sheet
    data class EditGoal(val goal: Goal) : Sheet
    data object Transfer : Sheet
    data class Sync(val feeDraft: String = "2,5") : Sheet
    data object DeleteWallets : Sheet
    data object DeleteTransactions : Sheet
    data object DeleteQuickActions : Sheet
    data object DeleteSnapshots : Sheet
}

private enum class Tab(val key: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Transactions("transactions", "Activity", Icons.AutoMirrored.Filled.List),
    Review("review", "Review", Icons.Default.Star),
    Settings("settings", "Settings", Icons.Default.Settings),
}

/**
 * App shell: four top-level destinations in a bottom bar plus one shared bottom-sheet
 * host. Destructive and setup actions live under Settings so the dashboard and the
 * transaction list stay focused on reading money.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(vm: MainViewModel, modifier: Modifier = Modifier) {
    var tabKey by rememberSaveable { mutableStateOf(Tab.Home.key) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    if (!vm.loaded) {
        // Keep the first frame as cheap as possible: the window background is already
        // the app canvas, so no spinner or Material composition is needed while the
        // first database read completes off the main thread.
        Box(modifier.fillMaxSize().background(CanvasBg))
        return
    }

    val data = vm.data
    val tab = Tab.entries.firstOrNull { it.key == tabKey } ?: Tab.Home

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { AppBottomBar(selected = tab, onSelect = { tabKey = it.key }) },
        floatingActionButton = {
            if (tab == Tab.Home || tab == Tab.Transactions) {
                ExtendedFloatingActionButton(
                    onClick = { sheet = Sheet.NewTransaction() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add") },
                )
            }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                Tab.Home -> HomeTab(
                    data = data,
                    onSheet = { sheet = it },
                    onSeeAll = { tabKey = Tab.Transactions.key },
                )
                Tab.Transactions -> TransactionsTab(data = data)
                Tab.Review -> ReviewTab(
                    vm = vm,
                    data = data,
                    onSheet = { sheet = it },
                )
                Tab.Settings -> SettingsTab(
                    vm = vm,
                    data = data,
                    onSheet = { sheet = it },
                )
            }
        }
    }

    val current = sheet
    if (current != null) {
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
        ) {
            when (current) {
                is Sheet.AddWallet -> AddWalletSheet(vm) { sheet = null }
                is Sheet.NewTransaction -> NewTransactionSheet(
                    vm = vm,
                    wallets = data.wallets,
                    prefill = current.prefill,
                    onClose = { sheet = null },
                )
                is Sheet.AddQuickAction -> AddQuickActionSheet(vm) { sheet = null }
                is Sheet.AddGoal -> AddGoalSheet(vm) { sheet = null }
                is Sheet.EditGoal -> EditGoalSheet(vm, current.goal) { sheet = null }
                is Sheet.Transfer -> TransferSheet(vm, data.wallets) { sheet = null }
                is Sheet.Sync -> SyncSheet(vm, feeDraft = current.feeDraft) { sheet = null }
                is Sheet.DeleteWallets -> DeleteWalletsSheet(vm, data.wallets) { sheet = null }
                is Sheet.DeleteTransactions -> DeleteTransactionsSheet(
                    vm = vm,
                    transactions = data.transactions,
                    walletNames = data.walletNames,
                    onClose = { sheet = null },
                )
                is Sheet.DeleteQuickActions -> DeleteQuickActionsSheet(vm, data.quickActions) { sheet = null }
                is Sheet.DeleteSnapshots -> DeleteSnapshotsSheet(vm, data.snapshots) { sheet = null }
            }
        }
    }
}

@Composable
private fun AppBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
    ) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = SoftTeal,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted,
                ),
            )
        }
    }
}

/** Bottom padding that clears the floating action button on list screens. */
val ListBottomPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp)
