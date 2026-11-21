package com.nabungah.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nabungah.data.AppData
import com.nabungah.data.BackupPreview
import com.nabungah.data.ConsistencyReport
import com.nabungah.data.Repo
import com.nabungah.data.Trx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-shot confirmation shown by the app shell; undo is only set for single deletes. */
data class UiEvent(
    val id: Long,
    val message: String,
    val undoTransaction: Trx? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repo(app)
    private var nextEventId = 0L

    var data by mutableStateOf(AppData())
        private set

    var loaded by mutableStateOf(false)
        private set

    var loadError by mutableStateOf<String?>(null)
        private set

    var event by mutableStateOf<UiEvent?>(null)
        private set

    var themeMode by mutableStateOf(ThemeMode.fromStorage(repo.themeMode()))
        private set

    fun updateThemeMode(mode: ThemeMode) {
        repo.saveThemeMode(mode.storageValue)
        themeMode = mode
    }

    fun consumeEvent(id: Long) {
        if (event?.id == id) event = null
    }

    private fun post(message: String, undoTransaction: Trx? = null) {
        event = UiEvent(++nextEventId, message, undoTransaction)
    }

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            loadError = null
            try {
                data = withContext(Dispatchers.IO) {
                    // Cheap no-op unless the calendar month changed since the last run.
                    repo.syncAutoSnapshots()
                    repo.load()
                }
                loaded = true
            } catch (e: Exception) {
                loadError = e.message ?: "Database lokal tidak bisa dibuka"
                loaded = true
            }
        }
    }

    /**
     * Runs a write on IO and reloads. Validation failures are reported through
     * [onError]; success calls [onDone].
     */
    private fun write(
        onError: (String) -> Unit = {},
        onDone: () -> Unit = {},
        successMessage: String? = null,
        block: (Repo) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    block(repo)
                    repo.load()
                }.let { fresh -> data = fresh }
                if (successMessage != null) post(successMessage)
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "Terjadi kesalahan")
            }
        }
    }

    /** Re-reads the clock only when the calendar month actually rolled over. */
    fun checkMonthRollover() {
        if (loaded && Fmt.monthKey(System.currentTimeMillis()) != data.currentMonth) reload()
    }

    fun addWallet(name: String, balance: Long, onError: (String) -> Unit, onDone: () -> Unit) =
        write(onError, onDone, "Dompet ditambahkan") { it.addWallet(name, balance) }

    fun deleteWallet(id: Long, onError: (String) -> Unit = {}) =
        write(onError, successMessage = "Dompet dihapus") { it.deleteWallet(id) }

    fun deleteAllWallets(onError: (String) -> Unit = {}) =
        write(onError, successMessage = "Semua dompet dihapus") { it.deleteAllWallets() }

    fun setFeePercent(percent: Double, onError: (String) -> Unit = {}, onDone: () -> Unit = {}) =
        write(onError, onDone, "Biaya disimpan") { it.saveFeePercent(percent) }

    fun deleteAllData(onError: (String) -> Unit = {}, onDone: () -> Unit = {}) =
        write(onError, onDone, "Semua data dihapus") { it.deleteAllData() }

    fun addTransaction(
        title: String,
        amount: Long,
        type: String,
        walletId: Long?,
        note: String,
        occurredAt: Long = System.currentTimeMillis(),
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) = write(onError, onDone, "Transaksi ditambahkan") {
        it.addTransaction(title, amount, type, walletId, note, occurredAt)
    }

    fun updateTransaction(
        id: Long,
        title: String,
        amount: Long,
        type: String,
        walletId: Long?,
        note: String,
        occurredAt: Long,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) = write(onError, onDone, "Transaksi diperbarui") {
        it.updateTransaction(id, title, amount, type, walletId, note, occurredAt)
    }

    /** Deletes one row and offers Undo in the shared snackbar. */
    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    repo.deleteTransaction(id)
                }
                data = withContext(Dispatchers.IO) { repo.load() }
                if (deleted != null) post("Transaksi dihapus", deleted)
            } catch (e: Exception) {
                post(e.message ?: "Gagal menghapus transaksi")
            }
        }
    }

    fun restoreTransaction(trx: Trx, onError: (String) -> Unit = {}) =
        write(onError, successMessage = "Transaksi dikembalikan") { it.restoreTransaction(trx) }

    fun deleteTransactions(ids: Collection<Long>) =
        write(successMessage = "${ids.size} transaksi dihapus") { it.deleteTransactions(ids) }

    fun deleteAllTransactions() = write(successMessage = "Semua transaksi dihapus") {
        it.deleteAllTransactions()
    }

    fun addQuickAction(title: String, amount: Long, type: String, onError: (String) -> Unit, onDone: () -> Unit) =
        write(onError, onDone, "Aksi cepat ditambahkan") { it.addQuickAction(title, amount, type) }

    fun deleteQuickAction(id: Long, onError: (String) -> Unit = {}) =
        write(onError, successMessage = "Aksi cepat dihapus") { it.deleteQuickAction(id) }

    fun deleteAllQuickActions(onError: (String) -> Unit = {}) =
        write(onError, successMessage = "Semua aksi cepat dihapus") { it.deleteAllQuickActions() }

    fun addGoal(
        title: String,
        target: Long,
        description: String,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) = write(onError, onDone, "Target dibuat") { it.addGoal(title, target, description) }

    fun updateGoal(
        id: Long,
        title: String,
        target: Long,
        saved: Long,
        status: String,
        description: String,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) = write(onError, onDone, "Target diperbarui") {
        it.updateGoal(id, title, target, saved, status, description)
    }

    fun deleteGoal(id: Long, onDone: () -> Unit = {}) =
        write(onDone = onDone, successMessage = "Target dihapus") { it.deleteGoal(id) }

    fun transfer(amount: Long, fromId: Long?, toId: Long?, onError: (String) -> Unit, onDone: () -> Unit) =
        write(onError, onDone, "Transfer dicatat") { it.transfer(amount, fromId, toId) }

    fun upsertSnapshot(
        month: String,
        totalBalance: Long,
        totalIncome: Long,
        totalExpense: Long,
        feePercent: Double,
        feeExpense: Long,
        source: String,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) = write(onError, onDone, "Laporan disimpan") {
        it.upsertSnapshot(
            month, totalBalance, totalIncome, totalExpense, feePercent, feeExpense, source,
        )
    }

    /** Recomputes a month from its transactions and hands it back to the automatic flow. */
    fun resetSnapshotToAuto(month: String, onError: (String) -> Unit, onDone: () -> Unit) =
        write(onError, onDone, "Laporan kembali otomatis") {
            if (month == it.todayMonth()) it.deleteSnapshot(month) else it.refreshAutoSnapshot(month)
        }

    fun deleteSnapshot(month: String, onError: (String) -> Unit = {}) =
        write(onError, successMessage = "Snapshot dihapus") { it.deleteSnapshot(month) }

    fun deleteAllSnapshots(onError: (String) -> Unit = {}) =
        write(onError, successMessage = "Semua snapshot dihapus") { it.deleteAllSnapshots() }

    // ---------------------------------------------------------- consistency

    fun auditConsistency(onResult: (ConsistencyReport) -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                onResult(withContext(Dispatchers.IO) { repo.auditConsistency() })
            } catch (e: Exception) {
                onError(e.message ?: "Pemeriksaan gagal")
            }
        }
    }

    fun fixConsistency(onError: (String) -> Unit = {}, onDone: () -> Unit = {}) =
        write(onError, onDone, "Data diperbaiki") { it.fixConsistency() }

    // ------------------------------------------------------------- backup

    fun exportBackup(uri: Uri, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val bytes = withContext(Dispatchers.IO) {
                    val json = repo.exportJson()
                    val dataBytes = json.toByteArray(Charsets.UTF_8)
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(dataBytes)
                    } ?: throw IllegalStateException("File backup tidak bisa ditulis")
                    repo.markBackupExported(dataBytes.size.toLong())
                    dataBytes.size.toLong()
                }
                data = withContext(Dispatchers.IO) { repo.load() }
                post("Backup tersimpan (${bytes / 1024} KB)")
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "Ekspor gagal")
            }
        }
    }

    /** Reads only counts and metadata so the replace dialog can describe the file. */
    fun previewBackup(uri: Uri, onResult: (BackupPreview) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("File backup tidak bisa dibaca")
                    repo.inspectBackup(text)
                }
                onResult(preview)
            } catch (e: Exception) {
                onError(e.message ?: "File backup tidak valid")
            }
        }
    }

    fun importBackup(uri: Uri, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("File backup tidak bisa dibaca")
                }
                data = withContext(Dispatchers.IO) {
                    repo.importJson(text)
                    repo.load()
                }
                post("Backup dipulihkan")
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "Pemulihan gagal")
            }
        }
    }
}
