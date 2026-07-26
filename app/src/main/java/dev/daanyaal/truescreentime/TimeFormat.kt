package dev.daanyaal.truescreentime

import android.content.Context

object TimeFormat {
    fun format(context: Context, millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> context.getString(R.string.time_hours_minutes, hours, minutes)
            minutes > 0 -> context.getString(R.string.time_minutes, minutes)
            else -> context.getString(R.string.time_seconds, millis / 1000)
        }
    }
}
