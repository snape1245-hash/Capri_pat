package com.capri.pet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

@Suppress("DEPRECATION")
class MainActivity : Activity() {
    private lateinit var key: EditText

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (48 * d).toInt(), (20 * d).toInt(), 20 * d.toInt())
        }
        col.addView(TextView(this).apply { text = "Capri – Screen Critic"; textSize = 22f })
        col.addView(TextView(this).apply {
            text = "Claude API key ထည့်ပြီး Start နှိပ်ပါ။ Screen ပုံတွေကို Anthropic ဆီ ပို့မှာဖြစ်လို့ banking/password မျက်နှာပြင်တွေမှာ Stop လုပ်ပါ။"
            setPadding(0, (8 * d).toInt(), 0, (16 * d).toInt())
        })
        key = EditText(this).apply {
            hint = "sk-ant-..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(getSharedPreferences("p", 0).getString("key", ""))
        }
        col.addView(key)
        col.addView(Button(this).apply { text = "Start"; setOnClickListener { start() } })
        col.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener { stopService(Intent(this@MainActivity, PetService::class.java)) }
        })
        setContentView(col)
    }

    private fun start() {
        getSharedPreferences("p", 0).edit().putString("key", key.text.toString().trim()).apply()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps' ပြီး ပြန်လာပါ", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(m.createScreenCaptureIntent(), 100)
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (rc == 100 && res == RESULT_OK && data != null) {
            val i = Intent(this, PetService::class.java).putExtra("code", res).putExtra("data", data)
            startForegroundService(i)
            moveTaskToBack(true)
        }
    }
}
