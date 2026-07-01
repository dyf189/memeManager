package com.mememanager.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
 * - 刚刚（<1分钟）
 * - X分钟前、X小时前
 * - 昨天、前天、三天前（以自然日零点为界）
 * - 4天前至今年内：MM-dd
 * - 去年及更早：yyyy-MM-dd
 */
object TimeGroupUtil {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3600_000L
    private const val DAY_MS = 86_400_000L

    fun getGroup(timestamp: Long): TimeGroup {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val todayStart = getDayStart(now)
        val yesterdayStart = todayStart - DAY_MS
        val dayBeforeYesterdayStart = todayStart - 2 * DAY_MS
        val threeDaysAgoStart = todayStart - 3 * DAY_MS
        val yearStart = getYearStart(now)

        return when {
            diff < MINUTE_MS -> TimeGroup("刚刚", todayStart + 100)
            diff < HOUR_MS -> TimeGroup("${diff / MINUTE_MS}分钟前", todayStart + 90)
            timestamp >= todayStart -> TimeGroup("${diff / HOUR_MS}小时前", todayStart + 80)
            timestamp >= yesterdayStart -> TimeGroup("昨天", yesterdayStart + 70)
            timestamp >= dayBeforeYesterdayStart -> TimeGroup("前天", dayBeforeYesterdayStart + 60)
            timestamp >= threeDaysAgoStart -> TimeGroup("三天前", threeDaysAgoStart + 50)
            timestamp >= yearStart -> {
                val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
                val dayStart = getDayStart(timestamp)
                TimeGroup(sdf.format(Date(timestamp)), dayStart + 40)
            }
            else -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dayStart = getDayStart(timestamp)
                TimeGroup(sdf.format(Date(timestamp)), dayStart + 30)
            }
        }
    }

    private fun getDayStart(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getYearStart(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.MONTH, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
