package com.atendeauto

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.edit

/** Decide se um número é "conhecido": está nos Contatos ou na allowlist do app. */
object ContactLookup {

    private const val PREFS = "atendeauto_prefs"
    private const val KEY_ALLOWLIST = "allowlist"

    fun isKnown(context: Context, number: String): Boolean =
        isInContacts(context, number) || isAllowed(context, number)

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
            // Sem permissão de Contatos: não temos como saber, tratamos como desconhecido
            // fica a cargo da tela de configuração pedir a permissão.
            false
        }
    }

    private fun normalize(number: String): String = number.filter { it.isDigit() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAllowlist(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWLIST, emptySet()).orEmpty().toSet()

    fun isAllowed(context: Context, number: String): Boolean =
        normalize(number) in getAllowlist(context).map(::normalize)

    fun addToAllowlist(context: Context, number: String) {
        val current = getAllowlist(context).toMutableSet()
        current.add(number)
        prefs(context).edit { putStringSet(KEY_ALLOWLIST, current) }
    }

    fun removeFromAllowlist(context: Context, number: String) {
        val current = getAllowlist(context).toMutableSet()
        current.remove(number)
        prefs(context).edit { putStringSet(KEY_ALLOWLIST, current) }
    }
}
