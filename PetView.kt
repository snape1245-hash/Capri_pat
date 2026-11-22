package com.capri.pet

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sin

class PetView(c: Context) : View(c) {
    var phase = 0f
    var facing = 1
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension((64 * d).toInt(), (90 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val s = width / 64f
        c.scale(s, s)
        if (facing < 0) c.scale(-1f, 1f, 32f, 45f)
        fun box(l: Float, t: Float, r: Float, b: Float, rad: Float, col: Int) {
            p.color = col; c.drawRoundRect(l, t, r, b, rad, rad, p)
        }
        val a = sin(phase) * 25f
        c.save(); c.rotate(a, 24f, 62f); box(20f, 62f, 28f, 84f, 3f, 0xFF2B2650.toInt()); box(18f, 82f, 31f, 88f, 2f, 0xFFFF2D95.toInt()); c.restore()
        c.save(); c.rotate(-a, 40f, 62f); box(36f, 62f, 44f, 84f, 3f, 0xFF2B2650.toInt()); box(34f, 82f, 47f, 88f, 2f, 0xFFFF2D95.toInt()); c.restore()
        val bob = -Math.abs(sin(phase)) * 3f
        c.translate(0f, bob)
        box(14f, 32f, 50f, 66f, 8f, 0xFF3A3370.toInt())
        box(22f, 42f, 42f, 46f, 2f, 0xFF19E3FF.toInt())
        box(22f, 50f, 34f, 54f, 2f, 0xFFFF2D95.toInt())
        box(8f, 8f, 56f, 38f, 12f, 0xFF4A418C.toInt())
        box(14f, 16f, 50f, 30f, 7f, 0xFF0B0A14.toInt())
        box(18f, 19f, 46f, 27f, 4f, 0xFFFF2D95.toInt())
        box(30f, 0f, 34f, 10f, 0f, 0xFF19E3FF.toInt())
        p.color = 0xFFFF2D95.toInt(); c.drawCircle(32f, 2f, 3f, p)
    }
}
