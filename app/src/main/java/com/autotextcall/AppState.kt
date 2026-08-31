package com.autotextcall

import android.content.Context
import androidx.core.content.edit

/**
 * Estado compartilhado entre o CallScreeningService (detecta o número desconhecido) e o
 * AccessibilityService (aciona o botão "Chamada por texto"). Os dois rodam como
 * componentes independentes do mesmo processo, então SharedPreferences é suficiente —
 * não é preciso IPC.
 */
object AppState {

    private const val PREFS = "atendeauto_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ANSWER_ALL = "answer_all"
    private const val KEY_PENDING_NUMBER = "pending_number"
    private const val KEY_PENDING_AT = "pending_at"

    /** Chamada de número desconhecido só é válida por esta janela de tempo. */
    private const val PENDING_TTL_MS = 20_000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    /** Se true, toda chamada é atendida por texto — mesmo de contatos salvos —, exceto
     *  números explicitamente cadastrados como "Não atender por texto". */
    fun isAnswerAllCalls(context: Context): Boolean = prefs(context).getBoolean(KEY_ANSWER_ALL, false)

    fun setAnswerAllCalls(context: Context, answerAll: Boolean) {
        prefs(context).edit { putBoolean(KEY_ANSWER_ALL, answerAll) }
    }

    fun markUnknownCallPending(context: Context, number: String) {
        prefs(context).edit {
            putString(KEY_PENDING_NUMBER, number)
            putLong(KEY_PENDING_AT, System.currentTimeMillis())
        }
    }

    /** true se há uma chamada de número desconhecido recém-sinalizada pelo screening. */
    fun hasPendingUnknownCall(context: Context): Boolean {
        val at = prefs(context).getLong(KEY_PENDING_AT, 0L)
        return at != 0L && System.currentTimeMillis() - at <= PENDING_TTL_MS
    }

    fun clearPending(context: Context) {
        prefs(context).edit {
            remove(KEY_PENDING_NUMBER)
            remove(KEY_PENDING_AT)
        }
    }
}
