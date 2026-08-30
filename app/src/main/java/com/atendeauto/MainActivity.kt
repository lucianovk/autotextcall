package com.atendeauto

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela única da build de diagnóstico: explica o procedimento e leva às
 * configurações de Acessibilidade para habilitar o serviço.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(
            TextView(this).apply {
                textSize = 16f
                text = buildString {
                    appendLine("AtendeAuto — build de diagnóstico")
                    appendLine()
                    appendLine("1. Ative o serviço em Acessibilidade (botão abaixo).")
                    appendLine("   Se estiver esmaecido: Informações do app → ⋮ →")
                    appendLine("   \"Permitir configurações restritas\".")
                    appendLine()
                    appendLine("2. No PC, rode: adb logcat -s AtendeAuto")
                    appendLine()
                    appendLine("3. Ligue para este telefone de outro número e")
                    appendLine("   deixe tocar alguns segundos.")
                    appendLine()
                    appendLine("Este app não atende, rejeita nem altera chamadas.")
                }
            },
        )

        root.addView(
            Button(this).apply {
                text = getString(R.string.open_accessibility_settings)
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            },
        )

        setContentView(root)
    }
}
