package com.msnguard.vpn

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Quiet, flat world map backdrop for the MIKA AI VPN home screen.
 *
 * The landmass stays turquoise in every state. Only the network nodes and
 * animated routes follow the live tunnel state.
 */
class WorldMapBackgroundView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val land = Path()
    private var phase = 0f
    private var accent = Color.rgb(255, 23, 68)
    private var animator: ValueAnimator? = null
    private val density = resources.displayMetrics.density

    private val continents = listOf(
        floatArrayOf(0.05f,0.25f, 0.10f,0.16f, 0.19f,0.13f, 0.27f,0.18f, 0.32f,0.27f, 0.28f,0.36f, 0.23f,0.39f, 0.20f,0.51f, 0.16f,0.48f, 0.13f,0.38f, 0.08f,0.36f),
        floatArrayOf(0.25f,0.48f, 0.31f,0.49f, 0.34f,0.58f, 0.32f,0.72f, 0.29f,0.87f, 0.26f,0.79f, 0.24f,0.65f),
        floatArrayOf(0.42f,0.22f, 0.47f,0.14f, 0.55f,0.15f, 0.59f,0.22f, 0.56f,0.31f, 0.51f,0.34f, 0.49f,0.44f, 0.45f,0.39f),
        floatArrayOf(0.48f,0.40f, 0.55f,0.38f, 0.60f,0.45f, 0.59f,0.60f, 0.55f,0.76f, 0.51f,0.72f, 0.48f,0.58f, 0.45f,0.48f),
        floatArrayOf(0.57f,0.20f, 0.65f,0.14f, 0.76f,0.17f, 0.86f,0.20f, 0.94f,0.28f, 0.90f,0.38f, 0.81f,0.36f, 0.75f,0.44f, 0.68f,0.39f, 0.63f,0.32f),
        floatArrayOf(0.77f,0.56f, 0.85f,0.54f, 0.91f,0.62f, 0.89f,0.75f, 0.83f,0.79f, 0.78f,0.70f)
    )

    private val nodes = arrayOf(
        floatArrayOf(.13f,.30f), floatArrayOf(.22f,.34f), floatArrayOf(.29f,.30f),
        floatArrayOf(.46f,.29f), floatArrayOf(.51f,.34f), floatArrayOf(.56f,.30f),
        floatArrayOf(.66f,.31f), floatArrayOf(.75f,.32f), floatArrayOf(.84f,.30f),
        floatArrayOf(.29f,.58f), floatArrayOf(.53f,.59f), floatArrayOf(.82f,.65f)
    )

    fun setConnectionState(state: OrbitDialView.State) {
        accent = when (state) {
            OrbitDialView.State.DISCONNECTED, OrbitDialView.State.FAILED -> Color.rgb(255, 23, 68)
            OrbitDialView.State.CONNECTING -> Color.rgb(0, 170, 255)
            OrbitDialView.State.CONNECTED -> Color.rgb(0, 255, 102)
            OrbitDialView.State.DEGRADED -> Color.rgb(255, 214, 0)
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 5200L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val mapTop = h * .17f
        val mapBottom = h * .78f
        val mapHeight = mapBottom - mapTop

        // Low-contrast technical grid.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.55f * density
        paint.color = Color.argb(22, 0, 229, 255)
        for (i in 1..5) {
            val y = mapTop + mapHeight * i / 6f
            canvas.drawLine(w * .035f, y, w * .965f, y, paint)
        }
        for (i in 1..9) {
            val x = w * i / 10f
            canvas.drawLine(x, mapTop, x, mapBottom, paint)
        }

        // Flat, delicate turquoise continent silhouettes.
        continents.forEach { points ->
            land.reset()
            land.moveTo(points[0] * w, mapTop + points[1] * mapHeight)
            var i = 2
            while (i < points.size) {
                land.lineTo(points[i] * w, mapTop + points[i + 1] * mapHeight)
                i += 2
            }
            land.close()
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(18, 0, 229, 255)
            canvas.drawPath(land, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.9f * density
            paint.color = Color.argb(105, 0, 220, 245)
            canvas.drawPath(land, paint)
        }

        // Routes and nodes: turquoise map remains constant; only live links glow.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * density
        val wave = (phase * nodes.size).toInt()
        for (i in nodes.indices) {
            val a = nodes[i]
            val b = nodes[(i * 5 + 3) % nodes.size]
            val ax = a[0] * w; val ay = mapTop + a[1] * mapHeight
            val bx = b[0] * w; val by = mapTop + b[1] * mapHeight
            val cx = (ax + bx) / 2f
            val cy = minOf(ay, by) - mapHeight * .10f
            val path = Path().apply { moveTo(ax, ay); quadTo(cx, cy, bx, by) }
            paint.color = Color.argb(52, 0, 210, 235)
            canvas.drawPath(path, paint)
            if (i == wave % nodes.size || i == (wave + 1) % nodes.size) {
                paint.color = accent
                paint.strokeWidth = 1.35f * density
                canvas.drawPath(path, paint)
                paint.strokeWidth = 0.8f * density
            }
        }

        nodes.forEachIndexed { i, point ->
            val x = point[0] * w
            val y = mapTop + point[1] * mapHeight
            val active = i == wave % nodes.size || i == (wave + 1) % nodes.size
            paint.style = Paint.Style.FILL
            paint.color = if (active) accent else Color.rgb(0, 220, 245)
            paint.alpha = if (active) (150 + 105 * sin(phase * 6.28318f).toInt()).coerceIn(55,255) else 155
            canvas.drawCircle(x, y, (if (active) 2.6f else 1.35f) * density, paint)
        }
        paint.alpha = 255
    }
}
