package com.mememanager.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 时间分组模型
 * @param label 显示文字（"刚刚"、"昨天"、"06-19" 等）
 * @param sortKey 排序键（降序，越大越新）
 */
data class TimeGroup(
    val label: String,
    val sortKey: Long
)

/**
 * 时间分组工具
 *
 * 规则（来自 goals.md）：
 * - 今天（以自然日零点为界）
 * - 昨天、前天、三天前
 * - 4天前至今年内：MM-dd
 * - 去年及更早：yyyy-MM-dd
 *
 * 性能：用 java.time（minSdk 26+），Formatter/ZoneId 均为线程安全
 * 单例复用，避免每次调用 new SimpleDateFormat / Calendar（重且非线程安全）。
 */
object TimeGroupUtil {

    private const val DAY_MS = 86_400_000L

    private val zone: ZoneId = ZoneId.systemDefault()
    private val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getGroup(timestamp: Long): TimeGroup {
        val now = LocalDate.now(zone)
        val todayStart = now.atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayStart = todayStart - DAY_MS
        val dayBeforeYesterdayStart = todayStart - 2 * DAY_MS
        val threeDaysAgoStart = todayStart - 3 * DAY_MS
        val yearStart = now.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return when {
            timestamp >= todayStart -> TimeGroup("今天", todayStart + 100)
            timestamp >= yesterdayStart -> TimeGroup("昨天", yesterdayStart + 70)
            timestamp >= dayBeforeYesterdayStart -> TimeGroup("前天", dayBeforeYesterdayStart + 60)
            timestamp >= threeDaysAgoStart -> TimeGroup("三天前", threeDaysAgoStart + 50)
            timestamp >= yearStart -> {
                val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                TimeGroup(date.format(monthDayFormatter), dayStart + 40)
            }
            else -> {
                val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                TimeGroup(date.format(fullDateFormatter), dayStart + 30)
            }
        }
    }
}
