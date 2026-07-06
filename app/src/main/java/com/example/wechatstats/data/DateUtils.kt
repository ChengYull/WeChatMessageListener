package com.example.wechatstats.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {
    fun today(): LocalDate = LocalDate.now()

    fun dayStartMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun dayEndMillis(date: LocalDate): Long =
        date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 最近 N 天（含今天），从新到旧 */
    fun recentDates(count: Int = 14): List<LocalDate> =
        (0 until count).map { today().minusDays(it.toLong()) }

    fun formatLabel(date: LocalDate): String {
        val today = today()
        return when (date) {
            today                -> "今天"
            today.minusDays(1)   -> "昨天"
            today.minusDays(2)   -> "前天"
            else                 -> date.format(DateTimeFormatter.ofPattern("MM-dd"))
        }
    }
}
