package com.msnguard.vpn

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * Animated world-network backdrop for the MIKA AI VPN home screen.
 *
 * This view remains decorative: it does not participate in tunnel decisions.
 * The map, routes and nodes are deliberately confined to the upper half.
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

    // More regional and intercontinental endpoints create a denser global mesh.
    private val nodes = arrayOf(
        floatArrayOf(.09f,.29f), floatArrayOf(.14f,.23f), floatArrayOf(.20f,.30f),
        floatArrayOf(.25f,.39f), floatArrayOf(.29f,.28f), floatArrayOf(.31f,.49f),
        floatArrayOf(.37f,.35f), floatArrayOf(.43f,.27f), floatArrayOf(.47f,.36f),
        floatArrayOf(.51f,.30f), floatArrayOf(.55f,.42f), floatArrayOf(.59f,.28f),
        floatArrayOf(.63f,.35f), floatArrayOf(.68f,.27f), floatArrayOf(.72f,.38f),
        floatArrayOf(.77f,.29f), floatArrayOf(.82f,.34f), floatArrayOf(.87f,.27f),
        floatArrayOf(.91f,.36f), floatArrayOf(.23f,.60f), floatArrayOf(.30f,.69f),
        floatArrayOf(.50f,.59f), floatArrayOf(.55f,.72f), floatArrayOf(.81f,.63f),
        floatArrayOf(.86f,.70f), floatArrayOf(.67f,.55f), floatArrayOf(.40f,.55f),
        floatArrayOf(.12f,.48f), floatArrayOf(.94f,.49f), floatArrayOf(.74f,.52f)
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

        // Keep every map element inside the upper half of the screen.
        val mapTop = h * .035f
        val mapBottom = h * .485f
        val mapHeight = mapBottom - mapTop
        val wave = (phase * nodes.size).toInt() % nodes.size

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

        // Existing land silhouettes are retained pending the approved globe reference.
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

        // Three links per node: local, regional and long-distance connections.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 0.85f * density
        for (i in nodes.indices) {
            val offsets = intArrayOf(1, 5, 11)
            offsets.forEachIndexed { linkIndex, offset ->
                val a = nodes[i]
                val b = nodes[(i + offset) % nodes.size]
                val ax = a[0] * w
                val ay = mapTop + a[1] * mapHeight
                val bx = b[0] * w
                val by = mapTop + b[1] * mapHeight
                val cx = (ax + bx) / 2f
                val arcLift = mapHeight * (0.055f + linkIndex * 0.025f)
                val cy = minOf(ay, by) - arcLift
                val route = Path().apply { moveTo(ax, ay); quadTo(cx, cy, bx, by) }
                paint.color = when (linkIndex) {
                    0 -> Color.argb(65, 0, 210, 235)
                    1 -> Color.argb(48, 0, 210, 235)
                    else -> Color.argb(34, 0, 210, 235)
                }
                paint.strokeWidth = (if (linkIndex == 0) 0.95f else 0.7f) * density
                canvas.drawPath(route, paint)

                val activeRoute = i == wave && linkIndex == 0 ||
                    i == (wave + nodes.size - 1) % nodes.size && linkIndex == 1
                if (activeRoute) {
                    paint.color = accent
                    paint.strokeWidth = 1.45f * density
                    canvas.drawPath(route, paint)
                }
            }
        }

        nodes.forEachIndexed { i, point ->
            val x = point[0] * w
            val y = mapTop + point[1] * mapHeight
            val active = i == wave || i == (wave + 1) % nodes.size
            paint.style = Paint.Style.FILL
            paint.color = if (active) accent else Color.rgb(0, 220, 245)
            paint.alpha = if (active) {
                (170 + 85 * sin(phase * 6.28318f)).toInt().coerceIn(70, 255)
            } else 155
            if (active) {
                canvas.drawCircle(x, y, 5.0f * density, paint.apply { alpha = 38 })
                paint.alpha = 255
            }
            canvas.drawCircle(x, y, (if (active) 2.8f else 1.5f) * density, paint)
        }
        paint.alpha = 255
    }
}
