package com.atendeauto

import android.content.Context
import android.provider.CallLog

/** Números únicos mais recentes do log de chamadas, mais novos primeiro. */
object RecentCalls {

    data class Entry(val number: String, val name: String?)

    fun recentDistinctNumbers(context: Context, limit: Int = 20): List<Entry> {
        val result = LinkedHashMap<String, Entry>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                while (cursor.moveToNext() && result.size < limit) {
                    val number = cursor.getString(numberIdx)?.takeIf { it.isNotBlank() } ?: continue
                    val key = number.filter { it.isDigit() }
                    if (key.isEmpty() || result.containsKey(key)) continue
                    result[key] = Entry(number, cursor.getString(nameIdx))
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return result.values.toList()
    }
}
