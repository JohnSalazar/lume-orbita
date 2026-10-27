package br.com.lume.launcher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import br.com.lume.launcher.core.ProceduralSceneRules
import br.com.lume.launcher.core.ProceduralArtwork
import kotlin.math.sin
import kotlin.random.Random

/** A static original landscape: no bitmaps, files, network or animation callbacks. */
class ProceduralLandscapeDrawable(private val seed: Long) : Drawable() {
    val title: String = ProceduralSceneRules.title(seed)
    val theme: String = ProceduralSceneRules.theme(seed)
    private val landscape = theme !in setOf("whale", "fish", "galaxy", "milkyway")
    private val palette = ProceduralSceneRules.palette(seed)
    private val sky = Paint(Paint.ANTI_ALIAS_FLAG)
    private val plane = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette[6] }
    private val detail = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paths = Array(4) { Path() }
    private val accents = Path()
    private val area = RectF()
    private var stars = FloatArray(0)
    private var sunX = 0f; private var sunY = 0f; private var sunRadius = 0f
    private var alphaValue = 255
    private var appliedFilter: ColorFilter? = null
    private data class Ink(val path: Path, val paint: Paint, val opacity: Int)
    private var illustration = emptyList<Ink>()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        area.set(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat())
        paths.forEach { it.reset() }; accents.reset(); stars = FloatArray(0)
        illustration = emptyList()
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val scene = ProceduralSceneRules.geometry(bounds.width(), bounds.height(), seed)
        illustration = ProceduralArtwork.compose(seed).map { mark ->
            val path = Path(); val p = mark.points
            if (mark.kind == "ellipse") path.addOval(p[0] - p[2], p[1] - p[3], p[0] + p[2], p[1] + p[3], Path.Direction.CW)
            else {
                path.moveTo(p[0], p[1])
                if (mark.kind == "curve") {
                    var i = 2
                    while (i < p.size) { path.cubicTo(p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5]); i += 6 }
                } else { var i = 2; while (i < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 } }
                if (mark.stroke == 0f) path.close()
            }
            Ink(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mark.color; style = if (mark.stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL; strokeWidth = mark.stroke; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; colorFilter = appliedFilter }, (mark.opacity * 255).toInt())
        }
        sky.shader = LinearGradient(0f, 0f, 0f, area.height(), palette[0], palette[1], Shader.TileMode.CLAMP)
        sunX = scene.sun.x; sunY = scene.sun.y; sunRadius = scene.radius
        scene.layers.forEachIndexed { index, points ->
            val path = paths[index]
            path.moveTo(0f, area.bottom); path.lineTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val previous = points[i - 1]; val point = points[i]
                if (theme == "mountain" || theme == "night") path.lineTo(point.x, point.y)
                else path.quadTo(previous.x, previous.y, (previous.x + point.x) / 2f, (previous.y + point.y) / 2f)
            }
            path.lineTo(points.last().x, points.last().y); path.lineTo(area.right, area.bottom); path.close()
        }
        stars = FloatArray(scene.stars.size * 2)
        scene.stars.forEachIndexed { i, point -> stars[i * 2] = point.x; stars[i * 2 + 1] = point.y }
        val random = Random(seed xor 0x3156AC42L)
        if (theme == "ocean") {
            detail.style = Paint.Style.STROKE; detail.strokeWidth = (area.width() / 1280f).coerceIn(0.6f, 3f)
            detail.color = palette[2]
            repeat(5) { line ->
                val y = area.height() * (0.58f + line * 0.075f)
                accents.moveTo(0f, y)
                for (step in 1..16) accents.lineTo(area.width() * step / 16f, y + sin(step + line.toFloat()) * area.height() * 0.003f)
            }
        } else if (theme == "hills") {
            detail.style = Paint.Style.FILL; detail.color = palette[5]
            repeat(22) {
                val x = area.width() * random.nextFloat()
                val y = area.height() * (0.78f + random.nextFloat() * 0.16f)
                val height = area.height() * (0.018f + random.nextFloat() * 0.025f)
                val half = height * 0.3f
                accents.moveTo((x - half).coerceAtLeast(0f), y)
                accents.lineTo(x, y - height); accents.lineTo((x + half).coerceAtMost(area.width()), y); accents.close()
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (area.isEmpty) return
        val checkpoint = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.clipRect(area)
        sky.alpha = alphaValue; plane.alpha = alphaValue; sun.alpha = alphaValue; detail.alpha = alphaValue
        canvas.drawRect(area, sky)
        if (landscape) canvas.drawCircle(sunX, sunY, sunRadius, sun)
        var layer = 0
        while (landscape && layer < paths.size) { plane.color = palette[layer + 2]; plane.alpha = alphaValue; canvas.drawPath(paths[layer], plane); layer++ }
        if (theme == "night") {
            sun.alpha = alphaValue * 120 / 255
            var i = 0
            val radius = (area.height() * 0.0012f).coerceIn(0.6f, 3f)
            while (i < stars.size) { canvas.drawCircle(stars[i], stars[i + 1], radius, sun); i += 2 }
        }
        if (theme == "ocean" || theme == "hills") canvas.drawPath(accents, detail)
        canvas.scale(area.width() / 1000f, area.height() / 562.5f)
        for (ink in illustration) { ink.paint.alpha = alphaValue * ink.opacity / 255; canvas.drawPath(ink.path, ink.paint) }
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) { alphaValue = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun getAlpha(): Int = alphaValue
    override fun setColorFilter(colorFilter: ColorFilter?) {
        appliedFilter = colorFilter
        sky.colorFilter = colorFilter; plane.colorFilter = colorFilter; sun.colorFilter = colorFilter; detail.colorFilter = colorFilter
        illustration.forEach { it.paint.colorFilter = colorFilter }
        invalidateSelf()
    }
    override fun getColorFilter(): ColorFilter? = appliedFilter
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = if (alphaValue == 255 && appliedFilter == null) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
}
