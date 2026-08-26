package com.google.ai.sample

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * Presentation-only "traffic light" status indicator shown near the top-right screen edge
 * while Screen Operator works in the background (small label + colored circle).
 *
 * Deliberately DUMB by design: the entire state machine (which text/color is shown when,
 * visibility, hold timers, foreground detection) lives in the WebView (index.html). This
 * class only renders a JSON payload it receives via the bridge, so any future change to
 * wording, colors, sizes or timing is a pure web-bundle change - no APK rebuild required.
 *
 * JSON payload keys (all optional except "text"):
 *   text          String  label text (e.g. "Sending Screenshot")
 *   textColor     String  hex color for the label (default "#E8D66B")
 *   textSizeSp    Double  label text size in sp (default 11.0)
 *   circleColor   String  inner fill color of the circle; ABSENT/empty -> no circle at all
 *                         (used for the Error/Stop states where the light is off)
 *   ringColor     String  outer ring color of the circle (default "#FFFFFF")
 *   ringWidthDp   Double  ring stroke width in dp (default 1.5)
 *   circleSizeDp  Double  circle diameter in dp (default 10.0)
 *   gapDp         Double  gap between text and circle in dp (default 5.0)
 *   marginTopDp   Double  distance from the top screen edge in dp (default 4.0)
 *   marginEndDp   Double  distance from the right screen edge in dp (default 6.0)
 *   bgColor       String  optional pill background behind label+circle (default none)
 *   bgCornerDp    Double  corner radius of the pill background in dp (default 8.0)
 *   paddingHDp    Double  horizontal padding inside the pill in dp (default 0.0)
 *   paddingVDp    Double  vertical padding inside the pill in dp (default 0.0)
 *
 * The window is NOT focusable and NOT touchable, so it can never intercept input or
 * interfere with the accessibility automation running underneath it.
 */
internal class AccessibilityStatusLightOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: LinearLayout? = null
    private var labelView: TextView? = null
    private var circleView: View? = null

    fun update(json: String) {
        val spec = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
        val text = spec.optString("text", "").take(200)
        if (text.isEmpty()) {
            dismiss()
            return
        }

        val textColor = parseColor(spec.optString("textColor"), Color.rgb(232, 214, 107))
        val textSizeSp = spec.optDouble("textSizeSp", 11.0).toFloat()
        val circleColorRaw = spec.optString("circleColor", "")
        val showCircle = circleColorRaw.isNotBlank()
        val circleColor = parseColor(circleColorRaw, Color.BLACK)
        val ringColor = parseColor(spec.optString("ringColor"), Color.WHITE)
        val ringWidthPx = dp(spec.optDouble("ringWidthDp", 1.5))
        val circleSizePx = dp(spec.optDouble("circleSizeDp", 10.0))
        val gapPx = dp(spec.optDouble("gapDp", 5.0))
        val marginTopPx = dp(spec.optDouble("marginTopDp", 4.0))
        val marginEndPx = dp(spec.optDouble("marginEndDp", 6.0))
        val bgColorRaw = spec.optString("bgColor", "")
        val paddingHPx = dp(spec.optDouble("paddingHDp", 0.0))
        val paddingVPx = dp(spec.optDouble("paddingVDp", 0.0))

        val root = rootView ?: createViews()
        val label = labelView ?: return
        val circle = circleView ?: return

        label.text = text
        label.setTextColor(textColor)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

        if (showCircle) {
            circle.visibility = View.VISIBLE
            circle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(circleColor)
                setStroke(ringWidthPx.coerceAtLeast(1), ringColor)
            }
            circle.layoutParams = (circle.layoutParams as LinearLayout.LayoutParams).apply {
                width = circleSizePx
                height = circleSizePx
                marginStart = gapPx
            }
        } else {
            circle.visibility = View.GONE
        }

        root.background = if (bgColorRaw.isNotBlank()) {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(parseColor(bgColorRaw, Color.TRANSPARENT))
                cornerRadius = dp(spec.optDouble("bgCornerDp", 8.0)).toFloat()
            }
        } else {
            null
        }
        root.setPadding(paddingHPx, paddingVPx, paddingHPx, paddingVPx)

        val params = windowParams(marginEndPx, marginTopPx)
        if (root.parent == null) {
            try {
                windowManager.addView(root, params)
            } catch (_: Exception) {
                // If the window can't be added (e.g. service shutting down), fail silently -
                // this is a purely cosmetic indicator and must never break command execution.
                rootView = null
                labelView = null
                circleView = null
            }
        } else {
            try {
                windowManager.updateViewLayout(root, params)
            } catch (_: Exception) {
                // View got detached in between; drop and let the next update recreate it.
                rootView = null
                labelView = null
                circleView = null
            }
        }
    }

    fun dismiss() {
        val view = rootView ?: return
        rootView = null
        labelView = null
        circleView = null
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // The system may already have detached accessibility overlays during shutdown.
        }
    }

    private fun createViews(): LinearLayout {
        val label = TextView(context).apply {
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        val circle = View(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(circle, LinearLayout.LayoutParams(dp(10.0), dp(10.0)))
        }
        rootView = root
        labelView = label
        circleView = circle
        return root
    }

    private fun windowParams(xOffset: Int, yOffset: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = xOffset
        y = yOffset
        setTitle("Screen Operator status")
    }

    private fun dp(value: Double): Int =
        (value * context.resources.displayMetrics.density + 0.5).toInt()

    private fun parseColor(value: String?, fallback: Int): Int =
        try {
            if (value.isNullOrBlank()) fallback else Color.parseColor(value)
        } catch (_: Exception) {
            fallback
        }
}
