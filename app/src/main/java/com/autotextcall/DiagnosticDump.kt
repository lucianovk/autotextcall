package com.autotextcall

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import java.io.File

/**
 * Grava a árvore de acessibilidade das janelas do discador num arquivo local, para que o
 * usuário possa compartilhá-la (WhatsApp, e-mail, etc.) quando o botão "Chamada por texto"
 * não é encontrado num aparelho — sem precisar de adb/computador.
 */
object DiagnosticDump {

    private const val FILE_NAME = "diagnostico_chamada.txt"
    private const val MAX_DEPTH = 30

    fun write(context: Context, windows: List<Pair<String?, AccessibilityNodeInfo?>>) {
        val sb = StringBuilder()
        sb.appendLine("Diagnóstico Auto Text Call — ${java.util.Date()}")
        sb.appendLine("Modelo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
        sb.appendLine()
        windows.forEach { (pkg, root) ->
            sb.appendLine("--- janela: pkg=$pkg ---")
            if (root == null) {
                sb.appendLine("(root nulo)")
            } else {
                dump(root, 0, sb)
            }
            sb.appendLine()
        }
        file(context).writeText(sb.toString())
    }

    fun hasDump(context: Context): Boolean = file(context).exists()

    fun share(context: Context) {
        val target = file(context)
        if (!target.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar diagnóstico").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun dump(node: AccessibilityNodeInfo, depth: Int, sb: StringBuilder) {
        if (depth > MAX_DEPTH) return
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val indent = "  ".repeat(depth)
        sb.appendLine(
            "$indent[${node.className}] id=${node.viewIdResourceName} " +
                "text=${node.text} desc=${node.contentDescription} " +
                "clickable=${node.isClickable} visible=${node.isVisibleToUser} bounds=$bounds",
        )
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dump(child, depth + 1, sb)
        }
    }
}
