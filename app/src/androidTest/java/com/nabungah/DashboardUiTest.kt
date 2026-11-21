package com.nabungah

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabungah.data.Repo
import com.nabungah.ui.Dashboard
import com.nabungah.ui.MainViewModel
import com.nabungah.ui.NabungTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("nabungah.db")
    }

    @After
    fun tearDown() {
        app.deleteDatabase("nabungah.db")
    }

    private fun launch(): MainViewModel {
        val vm = MainViewModel(app)
        compose.setContent { NabungTheme { Dashboard(vm) } }
        compose.waitUntil(5_000) { vm.loaded }
        return vm
    }

    @Test
    fun switchesBetweenTopLevelTabs() {
        launch()

        compose.onNodeWithText("Aktivitas").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Semua transaksi dan transfer, terbaru dulu.")
            .assertIsDisplayed()

        compose.onNodeWithText("Laporan").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Laporan bulanan dari transaksimu, plus biaya yang perlu disisihkan.")
            .assertIsDisplayed()
    }

    @Test
    fun addsTransactionFromTheSharedSheet() {
        val repo = Repo(app)
        try {
            repo.addWallet("Cash", 100_000)
        } finally {
            repo.close()
        }
        launch()

        compose.onNodeWithTag("add-transaction").performClick()
        compose.onNodeWithText("Judul").performTextInput("Kopi")
        compose.onNodeWithText("Nominal").performTextInput("20000")
        compose.onNodeWithText("Dompet").performClick()
        compose.onNodeWithText("Cash").performClick()
        compose.onNodeWithText("Simpan Transaksi").performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Kopi").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Kopi").assertIsDisplayed()
    }

    @Test
    fun filtersActivityByWallet() {
        val repo = Repo(app)
        try {
            repo.addWallet("Cash", 100_000)
            repo.addWallet("Bank", 0)
            val cash = repo.load().wallets.first { it.name == "Cash" }.id
            repo.addTransaction("Kopi", 20_000, "expense", cash, "")
        } finally {
            repo.close()
        }
        launch()

        compose.onNodeWithText("Aktivitas").performClick()
        compose.onNodeWithText("Bulan ini").performClick()
        compose.onNodeWithText("Dompet").performClick()
        compose.onNodeWithText("Bank").performClick()
        compose.onNodeWithText("Tidak ada hasil").assertIsDisplayed()
    }
}

