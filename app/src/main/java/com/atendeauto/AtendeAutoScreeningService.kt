package com.atendeauto

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Detecta o número da chamada recebida (Android 10+, sem precisar de READ_PHONE_STATE /
 * READ_CALL_LOG) e, se for desconhecido, silencia o toque IMEDIATAMENTE (sem rejeitar a
 * chamada — a tela de chamada continua normal) e sinaliza para o
 * AtendeAutoAccessibilityService acionar o botão nativo "Chamada por texto" em seguida.
 */
class AtendeAutoScreeningService : CallScreeningService() {

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
    }

    companion object {
        private const val TAG = "AtendeAuto"
    }
}
