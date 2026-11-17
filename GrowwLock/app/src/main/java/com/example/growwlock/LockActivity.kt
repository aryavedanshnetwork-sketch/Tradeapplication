package com.example.growwlock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Full-screen "Limit reached" page shown over Groww. */
class LockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val until = Prefs.lockedUntilMs(this)
        val whenText = if (until > System.currentTimeMillis()) {
            DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)
                .format(Instant.ofEpochMilli(until).atZone(LockLogic.IST))
        } else {
            "the next trading day"
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#B71C1C"))
        val pad = (32 * resources.displayMetrics.density).toInt()
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "Limit reached"
        title.textSize = 34f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER

        val body = TextView(this)
        body.text = "You already placed your 1 buy order for today.\n\nYou can buy again on\n$whenText IST"
        body.textSize = 20f
        body.setTextColor(Color.WHITE)
        body.gravity = Gravity.CENTER
        body.setPadding(0, pad, 0, pad)

        val button = Button(this)
        button.text = "OK, go to home"
        button.setOnClickListener { goHome() }

        root.addView(title)
        root.addView(body)
        root.addView(button)
        setContentView(root)
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
        home.addCategory(Intent.CATEGORY_HOME)
        home.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(home)
        finish()
    }

    @Deprecated("Back also goes home so the screen cannot be used to reach Groww")
    override fun onBackPressed() {
        goHome()
    }
}
