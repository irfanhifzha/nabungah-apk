package com.nabungah.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nabungah.data.AppData
import com.nabungah.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repo(app)

    var data by mutableStateOf(AppData())
        private set

    var loaded by mutableStateOf(false)
        private set

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            data = withContext(Dispatchers.IO) { repo.load() }
            loaded = true
        }
    }

    /**
     * Runs a write on IO and reloads. Validation failures are reported through
     * [onError]; success calls [onDone].
     */
    private fun write(
        onError: (String) -> Unit = {},
        onDone: () -> Unit = {},
        block: (Repo) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    block(repo)
                    repo.load()
                }.let { fresh -> data = fresh }
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "Something went wrong")
            }
        }
    }

    fun addWallet(name: String, balance: Long, onError: (String) -> Unit, onDone: () -> Unit) =
        write(onError, onDone) { it.addWallet(name, balance) }

    fun deleteWallet(id: Long, onError: (String) -> Unit = {}) =
        write(onError) { it.deleteWallet(id) }

    fun deleteAllWallets(onError: (String) -> Unit = {}) =
        write(onError) { it.deleteAllWallets() }

    fun addTransaction(
        title: String,
        amount: Long,
        type: String,
        walletId: Long?,
        note: String,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) = write(onError, onDone) { it.addTransaction(title, amount, type, walletId, note) }

    fun deleteTransaction(id: Long) = write { it.deleteTransaction(id) }

    fun deleteAllTransactions() = write { it.deleteAllTransactions() }

    fun addQuickAction(title: String, amount: Long, type: String, onError: (String) -> Unit, onDone: () -> Unit) =
        write(onError, onDone) { it.addQuickAction(title, amount, type) }

    fun deleteQuickAction(id: Long, onError: (String) -> Unit = {}) =
        write(onError) { it.deleteQuickAction(id) }

    fun deleteAllQuickActions(onError: (String) -> Unit = {}) =
        write(onError) { it.deleteAllQuickActions() }

    fun addGoal(title: String, target: Long, onError: (String) -> Unit, onDone: () -> Unit) =
        write(onError, onDone) { it.addGoal(title, target) }

    fun updateGoal(
        id: Long,
        title: String,
        target: Long,
        saved: Long,
        status: String,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) = write(onError, onDone) { it.updateGoal(id, title, target, saved, status) }

    fun deleteGoal(id: Long, onDone: () -> Unit = {}) =
        write(onDone = onDone) { it.deleteGoal(id) }

    fun transfer(amount: Long, fromId: Long?, toId: Long?, onError: (String) -> Unit, onDone: () -> Unit) =
        write(onError, onDone) { it.transfer(amount, fromId, toId) }

    fun upsertSnapshot(
        month: String,
        totalBalance: Long,
        totalIncome: Long,
        totalExpense: Long,
        feePercent: Double,
        feeExpense: Long,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) = write(onError, onDone) {
        it.upsertSnapshot(month, totalBalance, totalIncome, totalExpense, feePercent, feeExpense)
    }

    fun deleteSnapshot(month: String, onError: (String) -> Unit = {}) =
        write(onError) { it.deleteSnapshot(month) }

    fun deleteAllSnapshots(onError: (String) -> Unit = {}) =
        write(onError) { it.deleteAllSnapshots() }

    // ------------------------------------------------------------- backup

    fun exportBackup(uri: Uri, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                withContext(Dispatchers.IO) {
                    val json = repo.exportJson()
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not write the backup file")
                }
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "Export failed")
            }
        }
    }

    fun importBackup(uri: Uri, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Could not read the backup file")
                }
                data = withContext(Dispatchers.IO) {
                    repo.importJson(text)
                    repo.load()
                }
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "Restore failed")
            }
        }
    }
}
