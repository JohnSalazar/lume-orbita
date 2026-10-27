package br.com.lume.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import br.com.lume.launcher.core.LauncherRules

class HomeAppTile(context: Context) : FrameLayout(context) {
    val cornerRadiusDp = 14f
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = GlassUi.FOCUS_STROKE_DP * density
    }
    private val ring = RectF()
    private val matrix = Matrix()
    private val startedAt = SystemClock.uptimeMillis()
    private var activityActive = false
    private var motionEnabled = false
    private var ready = false
    private val frame = object : Runnable {
        override fun run() {
            if (canRunBorderAnimation()) {
                rotateBorder()
                invalidate()
                postDelayed(this, 33L)
            }
        }
    }

    init { setWillNotDraw(false); ready = true }

    fun setActivityActive(value: Boolean) { activityActive = value; refreshBorder() }
    fun configureBorder(animations: Boolean) { motionEnabled = animations; refreshBorder() }
    fun stopBorderAnimation() { removeCallbacks(frame) }

    private fun canRunBorderAnimation() = activityActive && motionEnabled && hasFocus() && hasWindowFocus() &&
        isAttachedToWindow && isShown && windowVisibility == View.VISIBLE

    private fun rotateBorder() {
        matrix.setRotate(LauncherRules.focusBorderPhase((SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0)), width / 2f, height / 2f)
        paint.shader?.setLocalMatrix(matrix)
    }

    private fun refreshBorder() {
        if (!ready) return
        stopBorderAnimation()
        if (canRunBorderAnimation()) { rotateBorder(); postDelayed(frame, 33L) }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = paint.strokeWidth / 2f + 0.5f
        ring.set(inset, inset, w - inset, h - inset)
        if (w > 0 && h > 0) paint.shader = SweepGradient(w / 2f, h / 2f, GlassUi.appFocusColors(), null)
        refreshBorder()
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (hasFocus() && !ring.isEmpty) {
            val radius = (cornerRadiusDp * density - ring.left).coerceAtLeast(0f)
            canvas.drawRoundRect(ring, radius, radius, paint)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); refreshBorder() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); refreshBorder() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); refreshBorder() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); refreshBorder() }
    override fun onDetachedFromWindow() { stopBorderAnimation(); super.onDetachedFromWindow() }
}
