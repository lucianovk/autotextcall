package com.atendeauto

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
    private lateinit var allowlistText: TextView

    private val requestContacts = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshStatus() }

    private val requestScreeningRole = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(
            TextView(this).apply {
                textSize = 18f
                text = getString(R.string.app_name)
            },
        )

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    TextView(this@MainActivity).apply {
                        text = getString(R.string.enable_switch_label)
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
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
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            },
        )

        root.addView(
            TextView(this).apply {
                textSize = 16f
                setPadding(0, pad, 0, 0)
                text = getString(R.string.allowlist_title)
            },
        )

        val allowlistInput = EditText(this).apply {
            hint = getString(R.string.allowlist_hint)
        }
        root.addView(allowlistInput)

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    Button(this@MainActivity).apply {
                        text = getString(R.string.allowlist_add)
                        setOnClickListener {
                            val number = allowlistInput.text.toString().trim()
                            if (number.isNotEmpty()) {
                                ContactLookup.addToAllowlist(this@MainActivity, number)
                                allowlistInput.text.clear()
                                refreshStatus()
                            }
                        }
                    },
                )
                addView(
                    Button(this@MainActivity).apply {
                        text = getString(R.string.allowlist_remove)
                        setOnClickListener {
                            val number = allowlistInput.text.toString().trim()
                            if (number.isNotEmpty()) {
                                ContactLookup.removeFromAllowlist(this@MainActivity, number)
                                allowlistInput.text.clear()
                                refreshStatus()
                            }
                        }
                    },
                )
            },
        )

        allowlistText = TextView(this).apply { textSize = 14f }
        root.addView(allowlistText)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

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

    private fun refreshStatus() {
        val hasContacts = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

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

        allowlistText.text = ContactLookup.getAllowlist(this).ifEmpty { setOf(getString(R.string.allowlist_empty)) }
            .joinToString("\n")
    }

    private fun status(label: String, ok: Boolean): String =
        "${if (ok) "✓" else "✗"} $label"
}
