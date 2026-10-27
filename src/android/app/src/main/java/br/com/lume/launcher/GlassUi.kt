package br.com.lume.launcher

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import br.com.lume.launcher.core.NetworkStatus
import kotlin.math.roundToInt

object GlassUi {
    private fun isContentCard(view: View) = (view.tag as? String)?.let { it.startsWith("program:") || it.startsWith("platform:") } == true
    private fun focusScale(view: View): Float = when {
        view is HomeAppTile -> 1.10f
        isContentCard(view) -> 1.06f
        else -> 1f
    }

    fun revealFocus(view: View) {
        view.post {
            if (!view.isAttachedToWindow || (!view.hasFocus() && !(isContentCard(view) && view.isActivated))) return@post
            val growth = (focusScale(view) - 1f) / 2f
            if (growth <= 0f) return@post
            val halo = dp(view.context, if (isContentCard(view)) 10 else 4)
            val horizontal = kotlin.math.ceil(view.width * growth.toDouble()).toInt() + halo
            val vertical = kotlin.math.ceil(view.height * growth.toDouble()).toInt() + halo
            // Scroll the visual focus bounds, not only the smaller unscaled layout rectangle.
            view.requestRectangleOnScreen(Rect(-horizontal, -vertical, view.width + horizontal, view.height + vertical), false)
        }
    }
    const val FOCUS_STROKE_DP = 1f
    const val CORNER_RADIUS_DP = 18f
    fun appFocusColors() = intArrayOf(0xFFFF304F.toInt(), 0xFFFF8A16.toInt(), 0xFFFFE928.toInt(), 0xFF36ED72.toInt(), 0xFF25A5FF.toInt(), 0xFF6348FF.toInt(), 0xFFD936FF.toInt(), 0xFFFF304F.toInt())
    private val cloud = Color.rgb(244, 246, 247)
    private val muted = Color.rgb(193, 206, 215)
    private val connected = Color.rgb(150, 221, 189)
    private class BackControl(context: Context) : Button(context)

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun panel(
        transparency: Int = 58,
        reflections: Int = 65,
        focused: Boolean = false,
        border: Boolean = true,
        radiusDp: Float = CORNER_RADIUS_DP
    ): Drawable = surface(transparency, reflections, focused, false, border, radiusDp)

    private fun surface(transparency: Int, reflections: Int, focused: Boolean, secondary: Boolean, border: Boolean = true, radiusDp: Float = CORNER_RADIUS_DP): Drawable {
        val density = Resources.getSystem().displayMetrics.density
        val alpha = ((100 - transparency.coerceIn(0, 100)) * 255 / 100)
        val sheen = reflections.coerceIn(0, 100) / 100f
        val base = if (secondary) intArrayOf(53, 66, 79) else intArrayOf(20, 38, 54)
        val top = Color.argb(
            (alpha + 10 * sheen).roundToInt().coerceAtMost(255),
            base[0] + (18 * sheen).roundToInt(),
            base[1] + (22 * sheen).roundToInt(),
            base[2] + (26 * sheen).roundToInt()
        )
        val face = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(top, Color.argb(alpha, base[0], base[1], base[2]))
        ).apply {
            cornerRadius = radiusDp * density
            if (border) setStroke(
                (FOCUS_STROKE_DP * density).roundToInt().coerceAtLeast(1),
                if (focused) Color.rgb(218, 244, 255)
                else Color.argb((18 + 20 * sheen).roundToInt(), 210, 233, 249)
            )
        }
        val shadow = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.argb(38, 3, 12, 22))
        ).apply { cornerRadius = radiusDp * density }
        return LayerDrawable(arrayOf(shadow, face)).apply {
            setLayerInset(1, 0, 0, 0, density.roundToInt().coerceAtLeast(1))
        }
    }

    /** Four static, translucent round rectangles approximate a soft exterior halo without blur. */
    private fun contentSurface(context: Context, transparency: Int, reflections: Int, focused: Boolean, radiusDp: Float): Drawable {
        val face = panel(transparency, reflections, focused, false, radiusDp)
        if (!focused) return face
        val extents = intArrayOf(8, 6, 4, 2)
        val opacity = intArrayOf(10, 18, 30, 46)
        val layers = Array<Drawable>(5) { index ->
            if (index == 4) face else GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(opacity[index], 225, 242, 255), Color.argb(opacity[index] + 8, 225, 242, 255))).apply {
                cornerRadius = (radiusDp + extents[index]) * context.resources.displayMetrics.density
            }
        }
        return object : LayerDrawable(layers) {
            // Negative visual insets must not become layout padding for the card's children.
            override fun getPadding(padding: Rect): Boolean { padding.set(0, 0, 0, 0); return false }
        }.apply {
            for (index in extents.indices) {
                val outside = -dp(context, extents[index])
                setLayerInset(index, outside, outside, outside, outside)
            }
        }
    }

    fun text(
        context: Context,
        value: String,
        size: Float = 16f,
        secondary: Boolean = false
    ): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(if (secondary) muted else cloud)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
    }

    fun button(context: Context, value: String, onClick: () -> Unit): TextView =
        Button(context).apply {
            text = value
            textSize = 16f
            setTextColor(cloud)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            isAllCaps = false
            gravity = Gravity.CENTER
            minHeight = dp(context, 48)
            minimumWidth = dp(context, 48)
            setPaddingRelative(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            stateListAnimator = null
            backgroundTintList = null
            elevation = 0f
            setOnClickListener { onClick() }
            focus(this) { Triple(58, 65, true) }
        }

    fun backButton(context: Context, onClick: () -> Unit): TextView =
        backButton(context, "Voltar", onClick)

    fun backButton(
        context: Context,
        label: String,
        onClick: () -> Unit,
        appearance: () -> Triple<Int, Int, Boolean> = { Triple(58, 65, true) }
    ): TextView = BackControl(context).apply {
        text = label
        textSize = 14f
        setTextColor(cloud)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        isAllCaps = false
        gravity = Gravity.CENTER
        minHeight = dp(context, 48)
        minimumWidth = dp(context, 48)
        stateListAnimator = null
        backgroundTintList = null
        elevation = 0f
        setOnClickListener { onClick() }
        focus(this, appearance = appearance)
    }

    fun networkLabel(status: NetworkStatus): CharSequence {
        val title = "● ${status.connection}\n"
        return SpannableString(title + status.internet).apply {
            setSpan(ForegroundColorSpan(if (status.online) connected else muted), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 2, title.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(0.85f), title.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(muted), title.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun focus(view: View, radiusDp: Float = CORNER_RADIUS_DP, appearance: () -> Triple<Int, Int, Boolean>) {
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        fun update(focused: Boolean, animate: Boolean) {
            val highlighted = focused || (isContentCard(view) && view.isActivated)
            if (view is HomeAppTile) view.stopBorderAnimation()
            val (transparency, reflections, animations) = appearance()
            val background = if (view is BackControl) {
                InsetDrawable(surface(transparency, reflections, focused, true), 0, dp(view.context, 8), 0, dp(view.context, 8))
            } else if (isContentCard(view)) {
                contentSurface(view.context, transparency, reflections, highlighted, radiusDp)
            } else panel(transparency, reflections, focused, !(focused && view is HomeAppTile), radiusDp)
            view.background = background
            if (view is BackControl) view.setPaddingRelative(dp(view.context, 14), 0, dp(view.context, 14), 0)
            view.animate().cancel()
            val focusScale = focusScale(view)
            val scale = if (highlighted) focusScale else if (isContentCard(view)) 0.82f else 1f
            val lift = if (highlighted && focusScale > 1f) dp(view.context, 8).toFloat() else 0f
            // These tiles reserve their growth/shadow gutter in the containing launcher layout.
            if (animate && animations && focusScale > 1f && view.isAttachedToWindow) {
                view.animate().scaleX(scale).scaleY(scale).translationZ(lift).setDuration(150L).start()
            } else {
                view.scaleX = scale; view.scaleY = scale; view.translationZ = lift
            }
            if (view is HomeAppTile) view.configureBorder(animations)
            if (highlighted && focusScale > 1f) revealFocus(view)
            if (animate && focused && animations && view !is HomeAppTile && view.isAttachedToWindow) {
                ObjectAnimator.ofInt(background, "alpha", 220, 255).setDuration(150L).start()
            }
        }
        view.setOnFocusChangeListener { _, focused -> update(focused, true) }
        update(view.hasFocus(), false)
    }
}
