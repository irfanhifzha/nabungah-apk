package com.nabungah

import com.nabungah.data.Snapshot
import com.nabungah.data.Trx
import com.nabungah.ui.buildMonthReport
import com.nabungah.ui.monthActivityCopyText
import com.nabungah.ui.monthCopyText
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

    private fun trx(
        id: Long,
        title: String,
        amount: Long,
        type: String,
        note: String,
        at: Long,
    ) = Trx(
        id = id,
        title = title,
        amount = amount,
        type = type,
        walletId = 1L,
        note = note,
        occurredAt = at,
        month = "2026-11",
    )

    @Test
    fun transactionCopyBlockIsSimpleAndComplete() {
        val kopi = trx(
            id = 1,
            title = "Kopi",
            amount = 20_000,
            type = "expense",
            note = "pagi",
            at = millis(2026, Calendar.NOVEMBER, 21, 14, 5),
        )
        val expected = """
            *Nabungah — Transaksi*
            Kopi
            Rp 20.000 · Pengeluaran
            Dompet: Cash
            Tanggal: Sab, 21/11/2026, 14:05
            Catatan: pagi
        """.trimIndent()
        assertEquals(expected, trxCopyText(kopi, "Cash"))
    }

    @Test
    fun transactionCopyBlockUsesDashForEmptyNote() {
        val gaji = trx(
            id = 2,
            title = "Gaji",
            amount = 1_500_000,
            type = "income",
            note = "",
            at = millis(2026, Calendar.NOVEMBER, 2, 8, 30),
        )
        assertEquals(
            "*Nabungah — Transaksi*\n" +
                "Gaji\n" +
                "Rp 1.500.000 · Pemasukan\n" +
                "Dompet: Dompet tidak dikenal\n" +
                "Tanggal: Sen, 02/11/2026, 08:30\n" +
                "Catatan: -",
            trxCopyText(gaji, "Dompet tidak dikenal"),
        )
    }

    @Test
    fun snapshotCopyBlockIsSimpleAndComplete() {
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
            *Nabungah — November 2026*
            Sumber: Manual
            Pemasukan: Rp 1.500.000
            Pengeluaran: Rp 20.000
            Saldo: Rp 130.000
            Biaya (2,5%): Rp 37.500
            Diperbarui: Sab, 21/11/2026, 11:42
        """.trimIndent()
        assertEquals(expected, snapshotCopyText(snapshot))
    }

    @Test
    fun monthCopyBlockIsCompactAndListsEveryTransaction() {
        val transactions = listOf(
            trx(
                id = 1,
                title = "Kopi",
                amount = 20_000,
                type = "expense",
                note = "",
                at = millis(2026, Calendar.NOVEMBER, 21, 14, 5),
            ),
            trx(
                id = 2,
                title = "Gaji",
                amount = 1_500_000,
                type = "income",
                note = "",
                at = millis(2026, Calendar.NOVEMBER, 2, 8, 30),
            ),
        )
        val report = buildMonthReport("2026-11", transactions, 1_480_000, 2.5)
        val expected = """
            *Nabungah — November 2026*
            Pemasukan: Rp 1.500.000
            Pengeluaran: Rp 20.000
            Selisih: + Rp 1.480.000
            Transaksi: 2
            Saldo akhir bulan: Rp 1.480.000

            Rincian:
            21/11 Kopi — - Rp 20.000 · Cash
            02/11 Gaji — + Rp 1.500.000 · Cash
        """.trimIndent()
        assertEquals(expected, monthCopyText("2026-11", report, transactions, mapOf(1L to "Cash")))
    }

    @Test
    fun monthActivityCopyBlockIncludesEveryField() {
        val transactions = listOf(
            trx(
                id = 1,
                title = "Kopi",
                amount = 20_000,
                type = "expense",
                note = "pagi",
                at = millis(2026, Calendar.NOVEMBER, 21, 14, 5),
            ),
            trx(
                id = 2,
                title = "Gaji",
                amount = 1_500_000,
                type = "income",
                note = "",
                at = millis(2026, Calendar.NOVEMBER, 2, 8, 30),
            ),
        )
        val expected = """
            *Nabungah — Aktivitas November 2026*
            2 transaksi

            Sab, 21/11/2026, 14:05 · Kopi
            Pengeluaran · - Rp 20.000
            Dompet: Cash
            Catatan: pagi

            Sen, 02/11/2026, 08:30 · Gaji
            Pemasukan · + Rp 1.500.000
            Dompet: Cash
            Catatan: -
        """.trimIndent()
        assertEquals(
            expected,
            monthActivityCopyText("2026-11", transactions, mapOf(1L to "Cash")),
        )
    }
}
