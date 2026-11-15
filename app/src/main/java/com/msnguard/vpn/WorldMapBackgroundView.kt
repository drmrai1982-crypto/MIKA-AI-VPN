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

    // More detailed, hand-traced flat-map silhouettes in normalized coordinates.
    // Coordinates are intentionally kept as vector paths so routes remain animated.
    private val continents = listOf(
        floatArrayOf(.035f,.22f, .055f,.16f, .085f,.13f, .115f,.12f, .145f,.15f, .175f,.14f, .205f,.17f, .235f,.16f, .265f,.20f, .295f,.24f, .315f,.29f, .300f,.34f, .275f,.36f, .255f,.41f, .235f,.43f, .215f,.48f, .195f,.46f, .180f,.41f, .155f,.39f, .140f,.34f, .115f,.33f, .095f,.29f, .070f,.29f, .050f,.26f),
        floatArrayOf(.245f,.47f, .270f,.47f, .295f,.50f, .315f,.55f, .320f,.61f, .310f,.68f, .300f,.75f, .285f,.82f, .270f,.88f, .255f,.84f, .245f,.77f, .235f,.69f, .225f,.61f, .230f,.54f),
        floatArrayOf(.405f,.23f, .420f,.19f, .445f,.16f, .470f,.15f, .495f,.17f, .515f,.20f, .535f,.22f, .550f,.26f, .535f,.30f, .510f,.32f, .490f,.36f, .475f,.40f, .455f,.38f, .440f,.34f, .420f,.31f, .410f,.27f),
        floatArrayOf(.455f,.40f, .480f,.38f, .505f,.39f, .530f,.42f, .550f,.46f, .555f,.52f, .545f,.59f, .530f,.66f, .515f,.73f, .495f,.79f, .480f,.75f, .470f,.68f, .455f,.61f, .445f,.54f, .440f,.47f),
        floatArrayOf(.535f,.22f, .555f,.18f, .585f,.16f, .615f,.14f, .650f,.15f, .680f,.17f, .715f,.16f, .750f,.18f, .785f,.20f, .820f,.22f, .855f,.25f, .885f,.29f, .910f,.32f, .895f,.36f, .865f,.38f, .835f,.37f, .810f,.40f, .785f,.43f, .755f,.42f, .730f,.39f, .700f,.41f, .675f,.38f, .650f,.36f, .625f,.33f, .600f,.31f, .575f,.29f, .555f,.26f),
        floatArrayOf(.765f,.55f, .790f,.53f, .820f,.54f, .845f,.56f, .870f,.60f, .885f,.65f, .875f,.70f, .855f,.75f, .830f,.78f, .805f,.76f, .785f,.72f, .770f,.67f),
        floatArrayOf(.105f,.055f, .145f,.035f, .185f,.045f, .205f,.075f, .185f,.105f, .145f,.115f, .115f,.095f)
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

        // Six links per node: dense local, regional and intercontinental connections.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 0.85f * density
        for (i in nodes.indices) {
            val offsets = intArrayOf(1, 2, 4, 7, 11, 16)
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

                val activeRoute = (i == wave && linkIndex == 0) ||
                    (i == (wave + nodes.size - 1) % nodes.size && linkIndex == 2) ||
                    (i == (wave + 7) % nodes.size && linkIndex == 4)
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
