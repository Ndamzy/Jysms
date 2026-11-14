package com.example.core

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object Formatters {
    private val ngnLocale = Locale("en", "NG")

    fun formatNgn(amount: Long): String {
        val formatter = NumberFormat.getNumberInstance(ngnLocale)
        return "₦${formatter.format(amount)}"
    }

    fun formatNgn(amount: Int): String = formatNgn(amount.toLong())

    fun todayIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun addMonthsIsoDate(months: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, months.coerceAtLeast(1))
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(cal.time)
    }

    fun formatTimestamp(date: Date?): String {
        if (date == null) return "Just now"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
        return sdf.format(date)
    }

    fun formatShortTime(date: Date?): String {
        if (date == null) return "Now"
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        return sdf.format(date)
    }

    fun generateStudentCode(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val suffix = (1000..9999).random()
        return "JY-$year-$suffix"
    }

    fun generateReference(prefix: String): String {
        val token = UUID.randomUUID().toString().substring(0, 8).uppercase(Locale.US)
        return "$prefix-$token"
    }

    fun computeGrade(totalScore: Int): Pair<String, String> {
        return when {
            totalScore >= 75 -> "A" to "Distinction"
            totalScore >= 65 -> "B" to "Very Good"
            totalScore >= 55 -> "C" to "Credit"
            totalScore >= 45 -> "D" to "Pass"
            totalScore >= 40 -> "E" to "Fair"
            else -> "F" to "Fail"
        }
    }
}
