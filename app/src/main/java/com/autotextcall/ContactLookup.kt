package com.autotextcall

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.edit

/**
 * Decide se um número deve ser atendido automaticamente em modo texto.
 *
 * Regra: números com um "override" explícito (cadastrados manualmente, da agenda ou das
 * chamadas recentes) sempre seguem o que foi definido para eles — "sempre atender por
 * texto" ou "nunca atender por texto" — independente de estarem ou não nos Contatos. Sem
 * override, o padrão é: contato salvo = não atende; qualquer outro número = atende.
 */
object ContactLookup {

    data class Override(val number: String, val autoAnswer: Boolean)

    private const val PREFS = "atendeauto_prefs"
    private const val KEY_OVERRIDES = "overrides"
    private const val SEPARATOR = "||"

    /** true = número conhecido (NÃO deve ser atendido automaticamente em modo texto). */
    fun isKnown(context: Context, number: String): Boolean {
        val override = findOverride(context, number)
        if (override != null) return !override.autoAnswer
        return isInContacts(context, number)
    }

    fun getOverrides(context: Context): List<Override> =
        prefs(context).getStringSet(KEY_OVERRIDES, emptySet()).orEmpty()
            .mapNotNull(::decode)
            .sortedBy { it.number }

    fun setOverride(context: Context, number: String, autoAnswer: Boolean) {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return
        val current = prefs(context).getStringSet(KEY_OVERRIDES, emptySet()).orEmpty().toMutableSet()
        current.removeAll { decode(it)?.let { o -> normalize(o.number) == normalize(trimmed) } == true }
        current.add(encode(trimmed, autoAnswer))
        prefs(context).edit { putStringSet(KEY_OVERRIDES, current) }
    }

    fun removeOverride(context: Context, number: String) {
        val current = prefs(context).getStringSet(KEY_OVERRIDES, emptySet()).orEmpty().toMutableSet()
        current.removeAll { decode(it)?.let { o -> normalize(o.number) == normalize(number) } == true }
        prefs(context).edit { putStringSet(KEY_OVERRIDES, current) }
    }

    private fun findOverride(context: Context, number: String): Override? =
        getOverrides(context).firstOrNull { normalize(it.number) == normalize(number) }

    private fun encode(number: String, autoAnswer: Boolean): String =
        "$number$SEPARATOR${if (autoAnswer) "1" else "0"}"

    private fun decode(entry: String): Override? {
        val idx = entry.lastIndexOf(SEPARATOR)
        if (idx < 0) return null
        return Override(entry.substring(0, idx), entry.substring(idx + SEPARATOR.length) == "1")
    }

    private fun isInContacts(context: Context, number: String): Boolean {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number),
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null,
            )?.use { cursor -> cursor.moveToFirst() } ?: false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun normalize(number: String): String = number.filter { it.isDigit() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
