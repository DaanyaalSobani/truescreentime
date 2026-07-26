package dev.daanyaal.truescreentime

import java.util.Calendar

sealed class DateRange {
    abstract val start: Long
    abstract val end: Long

    object Today : DateRange() {
        override val start: Long get() = startOfToday()
        override val end: Long get() = System.currentTimeMillis()
    }

    /** Calendar week, Monday 00:00 through now. */
    object ThisWeek : DateRange() {
        override val start: Long
            get() {
                val cal = atMidnight(Calendar.getInstance())
                // Walk back to Monday.
                while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                }
                return cal.timeInMillis
            }
        override val end: Long get() = System.currentTimeMillis()
    }

    class Custom(override val start: Long, override val end: Long) : DateRange()

    companion object {
        fun startOfToday(): Long = atMidnight(Calendar.getInstance()).timeInMillis

        fun startOfDay(year: Int, month: Int, day: Int): Long {
            val cal = Calendar.getInstance()
            cal.set(year, month, day)
            return atMidnight(cal).timeInMillis
        }

        fun endOfDay(year: Int, month: Int, day: Int): Long =
            startOfDay(year, month, day) + 24L * 60 * 60 * 1000

        private fun atMidnight(cal: Calendar): Calendar {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal
        }
    }
}
