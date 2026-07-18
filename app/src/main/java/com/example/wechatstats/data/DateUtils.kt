package com.example.wechatstats.data

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DateUtils {
    /** 中国时区偏移量（毫秒） */
    const val CHINA_OFFSET_MS = 8L * 60 * 60 * 1000

    fun today(): LocalDate = LocalDate.now(ZoneOffset.ofHours(8))

    /** UTC+8 时区的日期起始毫秒（UTC 时间戳） */
    fun dayStartMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.ofHours(8)).toInstant().toEpochMilli()

    fun dayEndMillis(date: LocalDate): Long =
        date.plusDays(1).atStartOfDay(ZoneOffset.ofHours(8)).toInstant().toEpochMilli()

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
