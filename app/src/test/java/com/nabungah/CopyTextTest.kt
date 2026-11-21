package com.nabungah

import com.nabungah.data.Snapshot
import com.nabungah.data.Trx
import com.nabungah.ui.snapshotCopyText
import com.nabungah.ui.trxCopyText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class CopyTextTest {

    private lateinit var previous: TimeZone

    @Before
    fun setUp() {
        previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jakarta"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(previous)
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"), Locale.US)
        cal.clear()
        cal.set(year, month, day, hour, minute, 0)
        return cal.timeInMillis
    }

    @Test
    fun transactionCopyBlockMatchesSpec() {
        val trx = Trx(
            id = 1,
            title = "Kopi",
            amount = 20_000,
            type = "expense",
            walletId = 2,
            note = "pagi",
            occurredAt = millis(2026, Calendar.NOVEMBER, 21, 14, 5),
            month = "2026-11",
        )
        val expected = """
            *🧾 Transaksi: Kopi*
            💰 Nominal: Rp 20.000
            📌 Tipe: Pengeluaran
            🏦 Dompet: Cash
            🗓️ Tanggal: Sab, 21/11/2026, 14:05
            📝 Catatan: pagi
        """.trimIndent()
        assertEquals(expected, trxCopyText(trx, "Cash"))
    }

    @Test
    fun transactionCopyBlockUsesDashForEmptyNote() {
        val trx = Trx(
            id = 1,
            title = "Gaji",
            amount = 1_500_000,
            type = "income",
            walletId = null,
            note = "",
            occurredAt = millis(2026, Calendar.NOVEMBER, 2, 8, 30),
            month = "2026-11",
        )
        val block = trxCopyText(trx, "Dompet tidak dikenal")
        assertEquals(
            "*🧾 Transaksi: Gaji*\n" +
                "💰 Nominal: Rp 1.500.000\n" +
                "📌 Tipe: Pemasukan\n" +
                "🏦 Dompet: Dompet tidak dikenal\n" +
                "🗓️ Tanggal: Sen, 02/11/2026, 08:30\n" +
                "📝 Catatan: -",
            block,
        )
    }

    @Test
    fun snapshotCopyBlockMatchesSpec() {
        val snapshot = Snapshot(
            month = "2026-11",
            totalBalance = 130_000,
            totalIncome = 1_500_000,
            totalExpense = 20_000,
            feePercent = 2.5,
            feeExpense = 37_500,
            updatedAt = millis(2026, Calendar.NOVEMBER, 21, 11, 42),
        )
        val expected = """
            *📅 Statistik Bulan: 2026-11*
            🔁 Sumber: Manual
            💰 Total Pemasukan: Rp 1.500.000
            💸 Total Pengeluaran: Rp 20.000
            💼 Total Saldo: Rp 130.000
            💳 Biaya (2,5%): Rp 37.500
            🕒 Diperbarui: Sab, 21/11/2026, 11:42
        """.trimIndent()
        assertEquals(expected, snapshotCopyText(snapshot))
    }
}
