package com.capri.pet

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class PetService : Service() {
    companion object {
        const val MODEL = "claude-haiku-5-5"   // model name ပြောင်းချင်ရင် ဒီမှာ
        const val EVERY_MS = 20000L            // screen ကြည့်တဲ့ အကြိမ်ကြား
        const val SYS = "မင်းက Capri ဆိုတဲ့ cyberpunk Night City က AI robot ပါ။ သုံးသူရဲ့ ဖုန်း screen ပေါ်မှာ လျှောက်သွားနေပြီး သူလုပ်နေတာကို ကြည့်ပြီး မြန်မာလို အတိုချုပ် (စာကြောင်း ၁–၂ ကြောင်း) လှောင်ပြောင်ဝေဖန်ပါ။ ပါးစပ်ပေါက်ပေါ့ ဒါပေမဲ့ ရင်းနှီးတဲ့ သူငယ်ချင်းလို၊ အမုန်းစကား မသုံးနဲ့။ Screen ပေါ်က တကယ်မြင်ရတာကို ထည့်ပြောပါ။ Password၊ ငွေကြေးအချက်အလက်တွေ ကိုယ်တိုင်ပြန်မဖတ်နဲ့။"
    }

    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var bubble: TextView
    private lateinit var pet: PetView
    private lateinit var lp: WindowManager.LayoutParams
    private val h = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var proj: MediaProjection? = null
    private var reader: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private var last: Bitmap? = null
    private var dir = 1
    private var dragging = false
    private var busy = false
    private var screenW = 0
    private val recent = ArrayDeque<String>()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i == null || proj != null) return START_NOT_STICKY
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("c", "Capri", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "c").setContentTitle("Capri ကြည့်နေတယ်")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val code = i.getIntExtra("code", 0)
        val data = i.getParcelableExtra<Intent>("data")!!
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        proj = mpm.getMediaProjection(code, data)
        proj!!.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, h)

        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        val cw = dm.widthPixels / 2
        val ch = dm.heightPixels / 2
        reader = ImageReader.newInstance(cw, ch, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val pl = img.planes[0]
                val bw = pl.rowStride / pl.pixelStride
                val bm = Bitmap.createBitmap(bw, ch, Bitmap.Config.ARGB_8888)
                bm.copyPixelsFromBuffer(pl.buffer)
                last = Bitmap.createBitmap(bm, 0, 0, cw, ch)
            } finally { img.close() }
        }, h)
        vd = proj!!.createVirtualDisplay("cap", cw, ch, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, h)

        buildOverlay()
        h.post(walk)
        h.postDelayed(look, 4000)
        return START_NOT_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildOverlay() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        bubble = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; maxWidth = dp(230); visibility = View.GONE
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { setColor(0xEE161327.toInt()); setStroke(dp(2), 0xFFFF2D95.toInt()); cornerRadius = dp(12).toFloat() }
        }
        pet = PetView(this)
        root.addView(bubble); root.addView(pet)
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.START; x = dp(10); y = dp(70) }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        pet.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dragging = true; moved = false; sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - sx) + Math.abs(e.rawY - sy) > 12) moved = true
                    lp.x = (ox + e.rawX - sx).toInt(); lp.y = (oy - (e.rawY - sy)).toInt()
                    wm.updateViewLayout(root, lp)
                }
                MotionEvent.ACTION_UP -> { dragging = false; if (!moved) { h.removeCallbacks(look); h.post(look) } }
            }
            true
        }
        wm.addView(root, lp)
    }

    private val walk = object : Runnable {
        override fun run() {
            if (!dragging && !busy) {
                val maxX = (screenW - root.width).coerceAtLeast(0)
                lp.x += dir * dp(2)
                if (lp.x > maxX) { lp.x = maxX; dir = -1 }
                if (lp.x < 0) { lp.x = 0; dir = 1 }
                if (Math.random() < 0.004) dir = -dir
                pet.phase += 0.3f
                pet.facing = dir
                pet.invalidate()
                wm.updateViewLayout(root, lp)
            }
            h.postDelayed(this, 33)
        }
    }

    private val look = object : Runnable {
        override fun run() {
            val bm = last
            if (bm == null || busy) { h.postDelayed(this, EVERY_MS); return }
            busy = true
            io.execute {
                val msg = try { ask(bm) } catch (e: Exception) { "Capri ချိတ်မရဘူး: ${e.message}" }
                h.post { say(msg); busy = false; h.postDelayed(this, EVERY_MS) }
            }
        }
    }

    private fun say(t: String) {
        bubble.text = t; bubble.visibility = View.VISIBLE
        h.removeCallbacks(hide); h.postDelayed(hide, 9000)
    }
    private val hide = Runnable { bubble.visibility = View.GONE }

    private fun ask(bm: Bitmap): String {
        val out = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, 60, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        val key = getSharedPreferences("p", 0).getString("key", "") ?: ""
        val sys = SYS + if (recent.isEmpty()) "" else "\nအရင်ပြောခဲ့တာတွေ (ထပ်မပြောနဲ့): " + recent.joinToString(" | ")
        val content = JSONArray()
            .put(JSONObject().put("type", "image").put("source",
                JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64)))
            .put(JSONObject().put("type", "text").put("text", "ဒီ screen ကိုကြည့်ပြီး မှတ်ချက်ပေး။"))
        val body = JSONObject().put("model", MODEL).put("max_tokens", 250).put("system", sys)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 40000
        c.setRequestProperty("x-api-key", key)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.setRequestProperty("content-type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code == 200) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code != 200) return "API error $code"
        val r = JSONObject(txt).getJSONArray("content").getJSONObject(0).getString("text").trim()
        recent.addLast(r); if (recent.size > 3) recent.removeFirst()
        return r
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        try { wm.removeView(root) } catch (_: Exception) {}
        vd?.release(); reader?.close(); proj?.stop()
        super.onDestroy()
    }
}
