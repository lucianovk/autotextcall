package com.autotextcall

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Ao surgir a tela de chamada recebida do Samsung (com.samsung.android.incallui) com uma
 * chamada marcada como desconhecida pelo AutoTextCallScreeningService, aciona o Chamada por
 * Texto, replicando o que o usuário faz manualmente:
 *
 *  0. Se só a "bolha" heads-up compacta estiver visível (outro app em primeiro plano), toca
 *     (toque sintético, via gesto) na área do nome/número (call_popup_card_container) para
 *     expandir a tela cheia — é esse toque que revela o botão flutuante "Chamada por texto".
 *  1. Toca no botão flutuante "Chamada por texto" (id ai_call_floating_button_container),
 *     que revela a confirmação de atendimento.
 *  2. Toca no botão de confirmação — um ImageView sem id, identificado pela
 *     contentDescription "Chamada por texto, Toque duas vezes para atender." — que de
 *     fato atende a chamada em modo texto.
 */
class AutoTextCallAccessibilityService : AccessibilityService() {

    private var awaitingConfirmUntil = 0L
    private var pollingUntil = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() > pollingUntil) {
                // Encerrou sem sucesso: se ainda há uma chamada desconhecida pendente, o
                // botão "Chamada por texto" (ou a bolha heads-up) não foi encontrado neste
                // aparelho — grava o diagnóstico para o usuário poder compartilhar.
                if (AppState.hasPendingUnknownCall(this@AutoTextCallAccessibilityService)) {
                    dumpDiagnostics()
                }
                return
            }
            if (attemptStep()) return
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    /**
     * Antes o filtro exigia "incallui" no pacote — mas isso deixa o serviço inteiramente mudo
     * (nenhuma tentativa, nenhum diagnóstico) em aparelhos onde a tela de chamada usa outro
     * nome de pacote. Ampliado para cobrir variações comuns de discador/telefonia/systemui.
     */
    private fun isDialerRelatedPackage(pkg: String): Boolean =
        pkg.contains("incallui", ignoreCase = true) ||
            pkg.contains("dialer", ignoreCase = true) ||
            pkg.contains("telecom", ignoreCase = true) ||
            pkg.contains("phone", ignoreCase = true) ||
            pkg.contains("systemui", ignoreCase = true) ||
            pkg.contains("bixby", ignoreCase = true)

    private fun dumpDiagnostics() {
        val snapshot = windows.orEmpty().map { it.root?.packageName?.toString() to it.root }
        DiagnosticDump.write(this, snapshot)
        Log.i(TAG, "Diagnóstico gravado (botão não encontrado neste aparelho)")
    }

    override fun onServiceConnected() {
        Log.i(TAG, "AutoTextCallAccessibilityService conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (!isDialerRelatedPackage(pkg)) return
        if (!AppState.isEnabled(this)) return

        // Em vez de só reagir a este evento, dispara/renova um polling ativo curto: a
        // cadência dos eventos de acessibilidade varia com a animação da UI do sistema, o que
        // deixava o fluxo perceptivelmente lento (até ~1s entre tentativas).
        startPolling()
    }

    private fun startPolling() {
        val now = System.currentTimeMillis()
        val alreadyPolling = pollingUntil > now
        pollingUntil = now + POLL_WINDOW_MS
        if (!alreadyPolling) {
            handler.removeCallbacks(pollRunnable)
            handler.post(pollRunnable)
        }
    }

    /** Executa o passo cabível no momento; retorna true se concluiu (sucesso final do fluxo). */
    private fun attemptStep(): Boolean {
        val now = System.currentTimeMillis()
        if (now < awaitingConfirmUntil) {
            return tryClickConfirm()
        }
        if (!AppState.hasPendingUnknownCall(this)) return true
        if (!tryClickFloatingButton()) {
            tryExpandHeadsUp()
        }
        return false
    }

    private fun tryClickFloatingButton(): Boolean {
        val clicked = allRoots()
            .mapNotNull { findNodeById(it, TEXT_CALL_FLOATING_BUTTON_ID) }
            .firstOrNull()
            ?.let { clickNode(it) }
            ?: false

        if (clicked) {
            Log.i(TAG, "Passo 1: botão flutuante 'Chamada por texto' acionado")
            AppState.clearPending(this)
            awaitingConfirmUntil = System.currentTimeMillis() + CONFIRM_WINDOW_MS
            // Garante que o polling continue até a janela de confirmação, mesmo que a UI
            // do sistema pare de emitir novos eventos de acessibilidade nesse meio-tempo.
            pollingUntil = awaitingConfirmUntil
        } else {
            Log.d(TAG, "Passo 1: botão flutuante ainda não encontrado nesta passada")
        }
        return clicked
    }

    /**
     * Com outro app em primeiro plano, a InCallUI some para uma "bolha" heads-up compacta
     * (call_popup_view) em vez da tela cheia — e o botão flutuante nunca existe nessa bolha.
     * Tocar na área do nome/número (call_popup_card_container) expande para a tela cheia, de
     * onde o Passo 1 consegue prosseguir na próxima passada. Essa área não é "clickable" na
     * árvore de acessibilidade (o toque é tratado via listener bruto na view), então
     * ACTION_CLICK não funciona — precisa de um toque sintético nas coordenadas reais.
     */
    private fun tryExpandHeadsUp() {
        val node = allRoots().mapNotNull { findNodeById(it, HEADS_UP_CARD_ID) }.firstOrNull()
        if (node == null) {
            Log.d(TAG, "Passo 0: bolha heads-up não encontrada nesta passada")
            return
        }

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        // Durante a animação de entrada da bolha, as bounds podem vir negativas ou vazias
        // (ex.: ainda deslizando de fora da tela) — GestureDescription rejeita isso com
        // IllegalArgumentException. Pulamos esta passada e tentamos de novo no próximo evento.
        if (bounds.isEmpty || bounds.left < 0 || bounds.top < 0) {
            Log.d(TAG, "Passo 0: bounds da bolha ainda inválidas ($bounds), aguardando próxima passada")
            return
        }

        val tapped = tapAt(bounds.exactCenterX(), bounds.exactCenterY())
        if (tapped) {
            Log.i(TAG, "Passo 0: toque sintético na bolha heads-up para expandir")
        } else {
            Log.w(TAG, "Passo 0: falha ao despachar o toque sintético na bolha heads-up")
        }
    }

    private fun tapAt(x: Float, y: Float): Boolean = try {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Passo 0: coordenadas inválidas para o toque sintético ($x, $y): ${e.message}")
        false
    }

    private fun tryClickConfirm(): Boolean {
        val clicked = allRoots()
            .mapNotNull { findNodeByDescription(it, ACCEPT_AS_TEXT_CALL_DESC_HINT) }
            .firstOrNull()
            ?.let { clickNode(it) }
            ?: false

        if (clicked) {
            Log.i(TAG, "Passo 2: confirmação 'Chamada por texto' acionada — chamada atendida em modo texto")
            awaitingConfirmUntil = 0L
        } else {
            Log.d(TAG, "Passo 2: botão de confirmação ainda não encontrado nesta passada")
        }
        return clicked
    }

    private fun allRoots(): Sequence<AccessibilityNodeInfo> =
        windows.orEmpty().asSequence().mapNotNull { it.root }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // Fallback: o nó encontrado pode não ser clicável mas um ancestral próximo sim.
        var parent = node.parent
        var hops = 0
        while (parent != null && hops < 3) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
            hops++
        }
        return false
    }

    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        if (root.viewIdResourceName == id) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNodeById(child, id)?.let { return it }
        }
        return null
    }

    /** Busca por conteúdo em vez de id, pois este nó específico não tem viewIdResourceName. */
    private fun findNodeByDescription(root: AccessibilityNodeInfo, descContains: String): AccessibilityNodeInfo? {
        val desc = root.contentDescription?.toString()
        if (desc != null && desc.contains(descContains, ignoreCase = true) && root.isClickable) {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNodeByDescription(child, descContains)?.let { return it }
        }
        return null
    }

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }

    companion object {
        private const val TAG = "AutoTextCall"
        private const val TEXT_CALL_FLOATING_BUTTON_ID =
            "com.samsung.android.incallui:id/ai_call_floating_button_container"
        private const val HEADS_UP_CARD_ID =
            "com.samsung.android.incallui:id/call_popup_card_container"
        private const val TAP_DURATION_MS = 50L
        // Distingue do botão flutuante (desc = apenas "Chamada por texto"): este exige
        // "atender" na descrição, presente só na confirmação de fato.
        private const val ACCEPT_AS_TEXT_CALL_DESC_HINT = "chamada por texto, toque duas vezes para atender"
        private const val CONFIRM_WINDOW_MS = 6_000L
        private const val POLL_INTERVAL_MS = 120L
        // Cada evento de acessibilidade renova esta janela; ela só se esgota se a UI do
        // discador parar de emitir eventos (ex.: usuário atendeu/recusou manualmente).
        private const val POLL_WINDOW_MS = 3_000L
    }
}
