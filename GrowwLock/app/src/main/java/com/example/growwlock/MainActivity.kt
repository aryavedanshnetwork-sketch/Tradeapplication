package com.example.growwlock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var keyBox: EditText
    private lateinit var secretBox: EditText
    private lateinit var pkgBox: EditText
    private lateinit var holidayBox: EditText
    private lateinit var istBox: CheckBox
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2_000L)
        }
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 14f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(Color.parseColor("#222222"))
        t.setPadding(0, dp(12), 0, dp(4))
        return t
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.setOnClickListener { onClick() }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Prefs.get(this)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(40), dp(20), dp(20))

        col.addView(label("Groww Lock", 28f))
        col.addView(label("1 buy order per day. After your buy, Groww is blocked until 9:15 AM on the next trading day."))

        status = label("")
        status.setBackgroundColor(Color.parseColor("#EEEEEE"))
        status.setPadding(dp(12), dp(12), dp(12), dp(12))
        col.addView(status)

        col.addView(label("Step 1: Groww API key"))
        keyBox = EditText(this)
        keyBox.setText(p.getString("api_key", ""))
        keyBox.setSingleLine()
        col.addView(keyBox)

        col.addView(label("Groww API secret (the TOTP secret)"))
        secretBox = EditText(this)
        secretBox.setText(p.getString("api_secret", ""))
        secretBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        col.addView(secretBox)

        col.addView(label("Groww app package name (leave as is unless blocking does not work)"))
        pkgBox = EditText(this)
        pkgBox.setText(Prefs.growwPackage(this))
        pkgBox.setSingleLine()
        col.addView(pkgBox)

        istBox = CheckBox(this)
        istBox.text = "Order times from Groww are already IST (tick only if the test shows times shifted by 5:30)"
        istBox.isChecked = p.getBoolean("ts_is_ist", false)
        col.addView(istBox)

        col.addView(label("Market holidays, one per line like 2026-12-25"))
        holidayBox = EditText(this)
        holidayBox.setText(p.getString("holidays", ""))
        holidayBox.minLines = 3
        holidayBox.gravity = android.view.Gravity.TOP
        holidayBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        col.addView(holidayBox)

        col.addView(button("Save") { save(); toast("Saved") })
        col.addView(label("Step 2: turn on the blocker"))
        col.addView(button("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(label("Find Groww Lock in the list and turn it on."))
        col.addView(label("Step 3: test"))
        col.addView(button("Check my orders now") {
            save()
            toast("Checking...")
            Thread {
                val msg = Monitor.checkOnce(applicationContext)
                runOnUiThread { toast(msg); refresh() }
            }.start()
        })
        col.addView(button("Preview the Limit reached screen") {
            startActivity(Intent(this, LockActivity::class.java))
        })

        val scroll = ScrollView(this)
        scroll.addView(col, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(scroll)
    }

    private fun save() {
        Prefs.get(this).edit()
            .putString("api_key", keyBox.text.toString().trim())
            .putString("api_secret", secretBox.text.toString().trim())
            .putString("groww_pkg", pkgBox.text.toString().trim())
            .putBoolean("ts_is_ist", istBox.isChecked)
            .putString("holidays", holidayBox.text.toString())
            .apply()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun blockerOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.contains("$packageName/")
    }

    private fun refresh() {
        val p = Prefs.get(this)
        val until = Prefs.lockedUntilMs(this)
        val lockLine = if (until > System.currentTimeMillis()) {
            "LOCKED until " + DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)
                .format(Instant.ofEpochMilli(until).atZone(LockLogic.IST)) + " IST"
        } else {
            "OPEN: you can place 1 buy order"
        }
        val last = p.getLong("last_check", 0L)
        val lastLine = if (last == 0L) "Not checked yet" else
            DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)
                .format(Instant.ofEpochMilli(last).atZone(LockLogic.IST)) + ": " + (p.getString("last_msg", "") ?: "")
        status.text = "Blocker: " + (if (blockerOn()) "ON" else "OFF (do step 2)") +
            "\n" + lockLine + "\nLast check " + lastLine
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }
}
