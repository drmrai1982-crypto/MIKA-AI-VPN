package com.msnguard.vpn

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.LinearInterpolator
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sin

/**
 * Animated world-network backdrop for the MIKA AI VPN home screen.
 *
 * This view remains decorative: it does not participate in tunnel decisions.
 * The map, routes and nodes are deliberately confined to the upper half.
 */
class WorldMapBackgroundView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var countryPaths: List<Path>? = null
    private var phase = 0f
    private var accent = Color.rgb(255, 23, 68)
    private var animator: ValueAnimator? = null
    private val density = resources.displayMetrics.density

    // Country outlines are loaded from the bundled Natural Earth 1:110m dataset.
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

    private fun loadCountryPaths(): List<Path> {
        countryPaths?.let { return it }
        val parsed = runCatching {
            val json = assets.open("ne_110m_admin_0_countries.geojson")
                .bufferedReader().use { it.readText() }
            val features = JSONObject(json).getJSONArray("features")
            val result = ArrayList<Path>(features.length())
            fun addPolygon(rings: JSONArray) {
                val polygon = Path().apply { fillType = Path.FillType.EVEN_ODD }
                for (r in 0 until rings.length()) {
                    val points = rings.getJSONArray(r)
                    var previousLon: Double? = null
                    for (p in 0 until points.length()) {
                        val coordinate = points.getJSONArray(p)
                        val lon = coordinate.getDouble(0)
                        val lat = coordinate.getDouble(1).coerceIn(-85.0, 85.0)
                        val x = ((lon + 180.0) / 360.0).toFloat()
                        val y = ((90.0 - lat) / 180.0).toFloat()
                        if (p == 0 || (previousLon != null && kotlin.math.abs(lon - previousLon!!) > 180.0)) {
                            polygon.moveTo(x, y)
                        } else {
                            polygon.lineTo(x, y)
                        }
                        previousLon = lon
                    }
                    polygon.close()
                }
                result.add(polygon)
            }
            for (i in 0 until features.length()) {
                val geometry = features.getJSONObject(i).optJSONObject("geometry") ?: continue
                val coordinates = geometry.optJSONArray("coordinates") ?: continue
                when (geometry.optString("type")) {
                    "Polygon" -> addPolygon(coordinates)
                    "MultiPolygon" -> for (p in 0 until coordinates.length()) {
                        addPolygon(coordinates.getJSONArray(p))
                    }
                }
            }
            result
        }.getOrElse { emptyList() }
        countryPaths = parsed
        return parsed
    }

    private fun drawRealWorldMap(canvas: Canvas, w: Float, mapTop: Float, mapHeight: Float) {
        val paths = loadCountryPaths()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(24, 0, 229, 255)
        paint.strokeWidth = 0.8f * density
        paths.forEach { source ->
            val matrix = android.graphics.Matrix().apply {
                setValues(floatArrayOf(
                    w, 0f, 0f,
                    0f, mapHeight, mapTop,
                    0f, 0f, 1f
                ))
            }
            val projected = Path(source)
            projected.transform(matrix)
            canvas.drawPath(projected, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(135, 0, 220, 245)
            canvas.drawPath(projected, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(24, 0, 229, 255)
        }
    }

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

        // Accurate country polygons and coastlines from Natural Earth geometry.
        drawRealWorldMap(canvas, w, mapTop, mapHeight)

        // Six links per node: dense local, regional and intercontinental connections.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 0.85f * density
        for (i in nodes.indices) {
            val offsets = intArrayOf(1, 2, 3, 5, 7, 10, 14, 19)
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
                    0 -> Color.argb(105, 0, 210, 235)
                    1 -> Color.argb(78, 0, 210, 235)
                    else -> Color.argb(58, 0, 210, 235)
                }
                paint.strokeWidth = (if (linkIndex == 0) 1.15f else 0.9f) * density
                canvas.drawPath(route, paint)

                val activeRoute = (i == wave && linkIndex == 0) ||
                    (i == (wave + nodes.size - 1) % nodes.size && linkIndex == 2) ||
                    (i == (wave + 7) % nodes.size && linkIndex == 4)
                if (activeRoute) {
                    paint.color = accent
                    paint.strokeWidth = 1.8f * density
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
