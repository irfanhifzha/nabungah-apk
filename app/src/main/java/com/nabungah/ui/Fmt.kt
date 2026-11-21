package com.nabungah.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Formatting and parsing helpers. Device-local timezone is used throughout;
 * timestamps are stored as UTC epoch millis and only translated for display.
 */
object Fmt {

    private val ID = Locale("id", "ID")

    private fun symbols() = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    /** "Rp 1.500.000" */
    fun rp(value: Long): String = "Rp " + grouped(value)

    /** 1500000 -> "1.500.000" */
    fun grouped(value: Long): String =
        DecimalFormat("#,##0", symbols()).format(value)

    /** Raw digit string -> grouped digits, e.g. "20000" -> "20.000". */
    fun groupDigits(raw: String): String {
        val digits = raw.filter { it.isDigit() }.trimStart('0')
        if (digits.isEmpty()) return if (raw.isNotEmpty()) "0" else ""
        val sb = StringBuilder(digits.length + digits.length / 3)
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    fun digitsToLong(digits: String): Long =
        digits.filter { it.isDigit() }.toLongOrNull() ?: 0L

    /** Keeps digits and at most one decimal separator ("," or "."). */
    fun sanitizeFeeInput(input: String): String {
        val sb = StringBuilder()
        var hasSeparator = false
        for (c in input) {
            when {
                c.isDigit() -> sb.append(c)
                c == ',' || c == '.' -> {
                    if (hasSeparator) break
                    sb.append(if (c == ',') ',' else '.')
                    hasSeparator = true
                }
            }
        }
        return sb.toString()
    }

    fun parseFee(input: String): Double =
        input.replace(',', '.').toDoubleOrNull() ?: 0.0

    fun feePercentLabel(percent: Double): String =
        DecimalFormat("0.##", symbols()).format(percent)

    /** round(income * feePercent / 100), whole rupiah. */
    fun feeExpense(income: Long, percent: Double): Long =
        (income * percent / 100.0).roundToLong()

    /** "Mon, 21/11/2026, 14:05" in the device locale/timezone. */
    fun timestamp(millis: Long): String =
        SimpleDateFormat("EEE, dd/MM/yyyy, HH:mm", ID).format(Date(millis))

    fun monthKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date(millis))

    /** "21 Nov 2026" for transaction date groups. */
    fun dayLabel(millis: Long): String =
        SimpleDateFormat("d MMM yyyy", ID).format(Date(millis))

    /** Stable grouping key for a day: "2026-11-21". */
    fun dayKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    /** "2026-11" -> "November 2026"; falls back to the raw key. */
    fun monthLabel(key: String): String = runCatching {
        val parsed = SimpleDateFormat("yyyy-MM", Locale.US).parse(key) ?: return@runCatching key
        SimpleDateFormat("MMMM yyyy", ID).format(parsed)
    }.getOrDefault(key)

    /** "nabungah-backup-20261121-1142.json" for the file picker. */
    fun backupFileName(millis: Long): String =
        "nabungah-backup-" +
            SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(millis)) +
            ".json"
}
