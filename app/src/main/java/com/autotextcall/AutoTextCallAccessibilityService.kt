package com.autotextcall

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Ao surgir a tela de chamada recebida do Samsung (com.samsung.android.incallui) com uma
 * chamada marcada como desconhecida pelo AutoTextCallScreeningService, aciona o Chamada por
 * Texto em dois passos, replicando o que o usuário faz manualmente:
 *
 *  1. Toca no botão flutuante "Chamada por texto" (id ai_call_floating_button_container),
 *     que revela a confirmação de atendimento.
 *  2. Toca no botão de confirmação — um ImageView sem id, identificado pela
 *     contentDescription "Chamada por texto, Toque duas vezes para atender." — que de
 *     fato atende a chamada em modo texto.
 */
class AutoTextCallAccessibilityService : AccessibilityService() {

    private var lastAttemptAt = 0L
    private var awaitingConfirmUntil = 0L

    override fun onServiceConnected() {
        Log.i(TAG, "AutoTextCallAccessibilityService conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (!pkg.contains(DIALER_PACKAGE_HINT, ignoreCase = true)) return
        if (!AppState.isEnabled(this)) return

        val now = System.currentTimeMillis()
        if (now - lastAttemptAt < MIN_ATTEMPT_INTERVAL_MS) return

        if (now < awaitingConfirmUntil) {
            lastAttemptAt = now
            tryClickConfirm()
            return
        }

        if (!AppState.hasPendingUnknownCall(this)) return
        lastAttemptAt = now
        tryClickFloatingButton()
    }

    private fun tryClickFloatingButton() {
        val clicked = allRoots()
            .mapNotNull { findNodeById(it, TEXT_CALL_FLOATING_BUTTON_ID) }
            .firstOrNull()
            ?.let { clickNode(it) }
            ?: false

        if (clicked) {
            Log.i(TAG, "Passo 1: botão flutuante 'Chamada por texto' acionado")
            AppState.clearPending(this)
            awaitingConfirmUntil = System.currentTimeMillis() + CONFIRM_WINDOW_MS
        } else {
            Log.d(TAG, "Passo 1: botão flutuante ainda não encontrado nesta passada")
        }
    }

    private fun tryClickConfirm() {
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
        private const val DIALER_PACKAGE_HINT = "incallui"
        private const val TEXT_CALL_FLOATING_BUTTON_ID =
            "com.samsung.android.incallui:id/ai_call_floating_button_container"
        // Distingue do botão flutuante (desc = apenas "Chamada por texto"): este exige
        // "atender" na descrição, presente só na confirmação de fato.
        private const val ACCEPT_AS_TEXT_CALL_DESC_HINT = "chamada por texto, toque duas vezes para atender"
        private const val MIN_ATTEMPT_INTERVAL_MS = 300L
        private const val CONFIRM_WINDOW_MS = 6_000L
    }
}
