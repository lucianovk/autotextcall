package com.atendeauto

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var overridesContainer: LinearLayout
    private lateinit var root: LinearLayout
    private var pad = 0

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
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply { textSize = 18f; text = getString(R.string.app_name) })

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    TextView(this@MainActivity).apply {
                        text = getString(R.string.enable_switch_label)
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

        statusText = TextView(this).apply { textSize = 14f }
        root.addView(statusText)

        root.addView(
            Button(this).apply {
                text = getString(R.string.request_screening_role)
                setOnClickListener { requestScreeningRoleAction() }
            },
        )
        root.addView(
            Button(this).apply {
                text = getString(R.string.request_contacts_permission)
                setOnClickListener { requestContacts.launch(Manifest.permission.READ_CONTACTS) }
            },
        )
        root.addView(
            Button(this).apply {
                text = getString(R.string.open_accessibility_settings)
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            },
        )

        root.addView(
            TextView(this).apply {
                textSize = 16f
                setPadding(0, pad, 0, 0)
                text = getString(R.string.overrides_title)
            },
        )

        val numberInput = EditText(this).apply { hint = getString(R.string.overrides_hint) }
        root.addView(numberInput)

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    Button(this@MainActivity).apply {
                        text = getString(R.string.mode_auto_answer)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener { saveOverrideFromInput(numberInput, autoAnswer = true) }
                    },
                )
                addView(
                    Button(this@MainActivity).apply {
                        text = getString(R.string.mode_never_answer)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener { saveOverrideFromInput(numberInput, autoAnswer = false) }
                    },
                )
            },
        )

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    Button(this@MainActivity).apply {
                        text = getString(R.string.pick_from_contacts)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener { pickFromContacts() }
                    },
                )
                addView(
                    Button(this@MainActivity).apply {
                        text = getString(R.string.pick_from_recent_calls)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener { onPickFromRecentCallsClicked() }
                    },
                )
            },
        )

        overridesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, pad, 0, 0)
        }
        root.addView(overridesContainer)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // --- Papel de triagem / permissões -------------------------------------------------

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

    // --- Importar da agenda / chamadas recentes -----------------------------------------

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

    // --- Lista de overrides ---------------------------------------------------------

    private fun refreshOverridesList() {
        overridesContainer.removeAllViews()
        val overrides = ContactLookup.getOverrides(this)
        if (overrides.isEmpty()) {
            overridesContainer.addView(TextView(this).apply { text = getString(R.string.overrides_empty) })
            return
        }
        overrides.forEach { override ->
            overridesContainer.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, pad / 4, 0, pad / 4)
                    addView(
                        TextView(this@MainActivity).apply {
                            val modeLabel = if (override.autoAnswer) {
                                getString(R.string.mode_auto_answer)
                            } else {
                                getString(R.string.mode_never_answer)
                            }
                            text = "${override.number}\n$modeLabel"
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        },
                    )
                    addView(
                        Button(this@MainActivity).apply {
                            text = getString(R.string.toggle_mode)
                            setOnClickListener {
                                ContactLookup.setOverride(this@MainActivity, override.number, !override.autoAnswer)
                                refreshStatus()
                            }
                        },
                    )
                    addView(
                        Button(this@MainActivity).apply {
                            text = getString(R.string.allowlist_remove)
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

    // --- Status geral -----------------------------------------------------------------

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

        refreshOverridesList()
    }

    private fun status(label: String, ok: Boolean): String = "${if (ok) "✓" else "✗"} $label"
}
