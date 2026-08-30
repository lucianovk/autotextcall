package com.autotextcall

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Serviço de DIAGNÓSTICO (Fase 1).
 *
 * Não age sobre a chamada — apenas despeja a árvore de acessibilidade das janelas do
 * discador enquanto o telefone toca, para descobrirmos se o botão "Chamada por texto"
 * é alcançável e clicável neste aparelho.
 *
 * Ler com:  adb logcat -s AtendeAuto
 */
class DumpAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        Log.i(TAG, "=== DumpAccessibilityService conectado ===")
    }

    private var lastDumpAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Toda janela é registrada, para não perdermos o pacote certo caso a One UI
            // use um nome diferente do esperado.
            Log.i(TAG, "janela: $pkg / ${event.className}")
        }

        if (!isDialerPackage(pkg)) return

        // typeWindowContentChanged pode disparar muitas vezes seguidas (ex.: animação do
        // deslizar); throttle para não afogar o log mas ainda capturar o estado revelado.
        val now = System.currentTimeMillis()
        if (now - lastDumpAt < MIN_DUMP_INTERVAL_MS) return
        lastDumpAt = now

        Log.i(TAG, ">>> DUMP início ($pkg, evento=${AccessibilityEvent.eventTypeToString(event.eventType)})")

        // Percorre TODAS as janelas visíveis, não só a ativa — o botão pode estar numa
        // janela sobreposta (ex.: lockscreen/systemui por cima do InCallUI).
        val allWindows = windows
        if (allWindows.isNullOrEmpty()) {
            val root = rootInActiveWindow
            if (root == null) {
                Log.w(TAG, ">>> rootInActiveWindow NULO — tela provavelmente protegida (FLAG_SECURE)")
            } else {
                dump(root, 0)
            }
        } else {
            for (window in allWindows) {
                Log.i(TAG, "--- janela: pkg=${window.root?.packageName} layer=${window.layer} type=${window.type} ---")
                dump(window.root, 0)
            }
        }
        Log.i(TAG, ">>> DUMP fim")
    }

    private fun isDialerPackage(pkg: String): Boolean =
        pkg.contains("incallui", ignoreCase = true) ||
            pkg.contains("dialer", ignoreCase = true) ||
            pkg.contains("telecom", ignoreCase = true) ||
            pkg.contains("phone", ignoreCase = true) ||
            // A tela de chamada pode ser sobreposta pela lockscreen (systemui) neste
            // aparelho; capturamos também para não perder um botão renderizado ali.
            pkg.contains("systemui", ignoreCase = true) ||
            pkg.contains("bixby", ignoreCase = true)

    private fun dump(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > MAX_DEPTH) return

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val indent = "  ".repeat(depth)
        Log.i(
            TAG,
            "$indent[${node.className}] id=${node.viewIdResourceName} " +
                "text=${node.text} desc=${node.contentDescription} " +
                "clickable=${node.isClickable} visible=${node.isVisibleToUser} bounds=$bounds",
        )

        for (i in 0 until node.childCount) {
            dump(node.getChild(i), depth + 1)
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }

    companion object {
        const val TAG = "AutoTextCall"
        private const val MAX_DEPTH = 30
        private const val MIN_DUMP_INTERVAL_MS = 400L
    }
}
