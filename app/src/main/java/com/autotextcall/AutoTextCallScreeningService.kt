package com.autotextcall

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Detecta o número da chamada recebida (Android 10+, sem precisar de READ_PHONE_STATE /
 * READ_CALL_LOG) e, se for desconhecido, silencia o toque IMEDIATAMENTE (sem rejeitar a
 * chamada — a tela de chamada continua normal) e sinaliza para o
 * AutoTextCallAccessibilityService acionar o botão nativo "Chamada por texto" em seguida.
 */
class AutoTextCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        if (!AppState.isEnabled(this)) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart
        if (number.isNullOrBlank() || ContactLookup.isKnown(this, number)) {
            Log.i(TAG, "Número conhecido ou vazio, nada a fazer: $number")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        Log.i(TAG, "Número desconhecido, silenciando toque e sinalizando: $number")
        // Silencia o toque na hora; a chamada segue tocando "muda" até o serviço de
        // acessibilidade atender em modo texto alguns instantes depois.
        respondToCall(callDetails, CallResponse.Builder().setSilenceCall(true).build())
        AppState.markUnknownCallPending(this, number)
        bringInCallUiToForeground()
    }

    /**
     * Com outro app em primeiro plano, o Android costuma rebaixar a tela de chamada a uma
     * notificação heads-up (sem o botão "Chamada por texto"), já que o full-screen intent só é
     * respeitado com tela bloqueada/desligada. Tentamos trazer a InCallUI do Samsung para frente
     * explicitamente para que o AutoTextCallAccessibilityService encontre o botão a clicar.
     */
    private fun bringInCallUiToForeground() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(INCALLUI_PACKAGE)
                ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível trazer a InCallUI para frente: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AutoTextCall"
        private const val INCALLUI_PACKAGE = "com.samsung.android.incallui"
    }
}
