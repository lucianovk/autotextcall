package com.autotextcall

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var overridesContainer: LinearLayout
    private lateinit var shareDiagnosticButton: MaterialButton
    private var pad = 0
    private var padHalf = 0

    private val requestContacts = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshStatus() }

    private val requestCallLog = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) pickFromRecentCalls() else refreshStatus() }

    private val requestScreeningRole = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshStatus() }

    private val pickContact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        val number = readPhoneNumberFromUri(uri)
        if (number != null) askModeAndSave(number)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pad = (16 * resources.displayMetrics.density).toInt()
        padHalf = pad / 2

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.surface_light))
            setPadding(pad, pad, pad, pad)
        }

        page.addView(header())
        page.addView(spacer())
        page.addView(setupCard())
        page.addView(spacer())
        page.addView(numbersCard())

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(color(R.color.surface_light))
                addView(page)
            },
        )
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // --- Seções de UI -------------------------------------------------------------------

    private fun header(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            TextView(this@MainActivity).apply {
                textSize = 22f
                setTextColor(color(R.color.text_primary_light))
                text = getString(R.string.app_name)
            },
        )
    }

    private fun setupCard(): MaterialCardView = card {
        addView(sectionTitle(R.string.section_setup))

        addView(
            TextView(this@MainActivity).apply {
                text = getString(R.string.prerequisite_warning)
                textSize = 13f
                setTextColor(color(R.color.text_secondary_light))
                setPadding(0, 0, 0, padHalf)
            },
        )

        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, padHalf, 0, padHalf)
                addView(
                    TextView(this@MainActivity).apply {
                        text = getString(R.string.enable_switch_label)
                        setTextColor(color(R.color.text_primary_light))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    },
                )
                addView(
                    Switch(this@MainActivity).apply {
                        isChecked = AppState.isEnabled(this@MainActivity)
                        setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                            AppState.setEnabled(this@MainActivity, checked)
                        }
                    },
                )
            },
        )

        statusText = TextView(this@MainActivity).apply {
            textSize = 14f
            setTextColor(color(R.color.text_secondary_light))
            setPadding(0, 0, 0, padHalf)
        }
        addView(statusText)

        addView(outlinedButton(R.string.request_screening_role) { requestScreeningRoleAction() })
        addView(outlinedButton(R.string.request_contacts_permission) { requestContacts.launch(Manifest.permission.READ_CONTACTS) })
        addView(outlinedButton(R.string.open_accessibility_settings) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })

        shareDiagnosticButton = outlinedButton(R.string.share_diagnostic) { DiagnosticDump.share(this@MainActivity) }
        addView(shareDiagnosticButton)
    }

    private fun numbersCard(): MaterialCardView = card {
        addView(sectionTitle(R.string.section_numbers))

        val numberInput = EditText(this@MainActivity).apply {
            hint = getString(R.string.overrides_hint)
            setPadding(padHalf, padHalf, padHalf, padHalf)
        }
        addView(numberInput)

        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, padHalf, 0, 0)
                weight(filledButton(R.string.mode_auto_answer) { saveOverrideFromInput(numberInput, autoAnswer = true) })
                weight(filledButton(R.string.mode_never_answer) { saveOverrideFromInput(numberInput, autoAnswer = false) })
            },
        )

        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, padHalf, 0, 0)
                weight(outlinedButton(R.string.pick_from_contacts) { pickFromContacts() })
                weight(outlinedButton(R.string.pick_from_recent_calls) { onPickFromRecentCallsClicked() })
            },
        )

        overridesContainer = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, pad, 0, 0)
        }
        addView(overridesContainer)
    }

    // --- Helpers visuais ------------------------------------------------------------

    private fun card(build: LinearLayout.() -> Unit): MaterialCardView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            build()
        }
        return MaterialCardView(this).apply {
            radius = 20f
            cardElevation = 3f
            setCardBackgroundColor(color(R.color.surface_card_light))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            addView(content)
        }
    }

    private fun sectionTitle(resId: Int): TextView = TextView(this).apply {
        textSize = 16f
        setTextColor(color(R.color.brand_primary))
        setPadding(0, 0, 0, padHalf)
        text = getString(resId)
    }

    private fun outlinedButton(resId: Int, onClick: () -> Unit): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(resId)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = padHalf / 2 }
        }

    private fun filledButton(resId: Int, onClick: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = getString(resId)
            setBackgroundColor(color(R.color.brand_primary))
            setTextColor(color(R.color.brand_on_primary))
            setOnClickListener { onClick() }
        }

    private fun LinearLayout.weight(view: android.view.View) {
        (view.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.width = 0
            it.weight = 1f
            it.marginEnd = padHalf / 2
        } ?: run {
            view.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        addView(view)
    }

    private fun spacer(): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, padHalf)
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    // --- Papel de triagem / permissões ---------------------------------------------------

    private fun requestScreeningRoleAction() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            statusText.text = getString(R.string.role_unavailable)
            return
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            refreshStatus()
            return
        }
        requestScreeningRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // --- Importar da agenda / chamadas recentes -------------------------------------------

    private fun pickFromContacts() {
        pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
    }

    private fun readPhoneNumberFromUri(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun onPickFromRecentCallsClicked() {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            requestCallLog.launch(Manifest.permission.READ_CALL_LOG)
            return
        }
        pickFromRecentCalls()
    }

    private fun pickFromRecentCalls() {
        val entries = RecentCalls.recentDistinctNumbers(this)
        if (entries.isEmpty()) {
            statusText.text = getString(R.string.no_recent_calls)
            return
        }
        val labels = entries.map { e -> e.name?.let { "$it (${e.number})" } ?: e.number }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_from_recent_calls)
            .setItems(labels) { _, which -> askModeAndSave(entries[which].number) }
            .show()
    }

    private fun askModeAndSave(number: String) {
        AlertDialog.Builder(this)
            .setTitle(number)
            .setItems(arrayOf(getString(R.string.mode_auto_answer), getString(R.string.mode_never_answer))) { _, which ->
                ContactLookup.setOverride(this, number, autoAnswer = which == 0)
                refreshStatus()
            }
            .show()
    }

    private fun saveOverrideFromInput(input: EditText, autoAnswer: Boolean) {
        val number = input.text.toString().trim()
        if (number.isEmpty()) return
        ContactLookup.setOverride(this, number, autoAnswer)
        input.text.clear()
        refreshStatus()
    }

    // --- Lista de números cadastrados -----------------------------------------------------

    private fun refreshOverridesList() {
        overridesContainer.removeAllViews()
        val overrides = ContactLookup.getOverrides(this)
        if (overrides.isEmpty()) {
            overridesContainer.addView(
                TextView(this).apply {
                    text = getString(R.string.overrides_empty)
                    setTextColor(color(R.color.text_secondary_light))
                },
            )
            return
        }
        overrides.forEach { override ->
            overridesContainer.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, padHalf / 2, 0, padHalf / 2)
                    addView(
                        TextView(this@MainActivity).apply {
                            val modeLabel = if (override.autoAnswer) {
                                getString(R.string.mode_auto_answer)
                            } else {
                                getString(R.string.mode_never_answer)
                            }
                            setTextColor(color(R.color.text_primary_light))
                            text = "${override.number}\n$modeLabel"
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        },
                    )
                    addView(
                        MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                            text = getString(R.string.toggle_mode)
                            setOnClickListener {
                                ContactLookup.setOverride(this@MainActivity, override.number, !override.autoAnswer)
                                refreshStatus()
                            }
                        },
                    )
                    addView(
                        MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                            text = getString(R.string.allowlist_remove)
                            setTextColor(Color.parseColor("#D9483A"))
                            setOnClickListener {
                                ContactLookup.removeOverride(this@MainActivity, override.number)
                                refreshStatus()
                            }
                        },
                    )
                },
            )
        }
    }

    // --- Status geral ---------------------------------------------------------------------

    private fun refreshStatus() {
        val hasContacts = hasPermission(Manifest.permission.READ_CONTACTS)
        val roleManager = getSystemService(RoleManager::class.java)
        val hasScreeningRole = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        val accessibilityOn = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )?.contains(packageName) == true

        statusText.text = buildString {
            appendLine(status(getString(R.string.status_contacts), hasContacts))
            appendLine(status(getString(R.string.status_screening_role), hasScreeningRole))
            append(status(getString(R.string.status_accessibility), accessibilityOn))
        }

        shareDiagnosticButton.isEnabled = DiagnosticDump.hasDump(this)
        shareDiagnosticButton.visibility = if (DiagnosticDump.hasDump(this)) android.view.View.VISIBLE else android.view.View.GONE

        refreshOverridesList()
    }

    private fun status(label: String, ok: Boolean): String = "${if (ok) "✓" else "✗"} $label"
}
