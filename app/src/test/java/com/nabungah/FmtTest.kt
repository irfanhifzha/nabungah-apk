package com.nabungah

import com.nabungah.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class FmtTest {

    @Test
    fun rupiahUsesDotGrouping() {
        assertEquals("Rp 1.500.000", Fmt.rp(1_500_000))
        assertEquals("Rp 0", Fmt.rp(0))
        assertEquals("Rp 15.000", Fmt.rp(15_000))
    }

    @Test
    fun digitsAreRegroupedAsTyped() {
        assertEquals("20.000", Fmt.groupDigits("20000"))
        assertEquals("0", Fmt.groupDigits("0"))
        assertEquals("1.234.567", Fmt.groupDigits("1234567"))
        assertEquals("", Fmt.groupDigits(""))
        // Grouped input round-trips: "20.000" -> digits -> same display.
        assertEquals("20.000", Fmt.groupDigits("20000"))
    }

    @Test
    fun digitsToLongIgnoresSeparators() {
        assertEquals(1_500_000L, Fmt.digitsToLong("1.500.000"))
        assertEquals(0L, Fmt.digitsToLong(""))
    }

    @Test
    fun feeAcceptsCommaOrDotAndOnlyOneSeparator() {
        assertEquals("2,5", Fmt.sanitizeFeeInput("2,5"))
        assertEquals("2.5", Fmt.sanitizeFeeInput("2.5"))
        assertEquals("2.5", Fmt.sanitizeFeeInput("2.5.5"))
        assertEquals(2.5, Fmt.parseFee("2,5"), 0.0001)
        assertEquals(2.5, Fmt.parseFee("2.5"), 0.0001)
        assertEquals("2,5", Fmt.feePercentLabel(2.5))
    }

    @Test
    fun feeExpenseRoundsToWholeRupiah() {
        assertEquals(37_500L, Fmt.feeExpense(1_500_000, 2.5))
        assertEquals(3L, Fmt.feeExpense(100, 2.5))
        assertEquals(0L, Fmt.feeExpense(0, 2.5))
    }

    @Test
    fun backupFileNameUsesTimestamp() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jakarta"))
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"), Locale.US)
            cal.clear()
            cal.set(2026, Calendar.NOVEMBER, 21, 9, 5, 0)
            assertEquals("nabungah-backup-20261121-0905.json", Fmt.backupFileName(cal.timeInMillis))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun monthKeyUsesDeviceTimeZone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jakarta"))
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"), Locale.US)
            cal.clear()
            cal.set(2026, Calendar.NOVEMBER, 21, 23, 30, 0)
            assertEquals("2026-11", Fmt.monthKey(cal.timeInMillis))
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
