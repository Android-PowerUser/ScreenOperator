package com.google.ai.sample

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Presentation-only fallback for an AI question while MainActivity's WebView is backgrounded.
 * Parsing, queueing, duplicate suppression, and forwarding the answer to the AI stay in JS.
 */
internal class AccessibilityQuestionOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun show(
        question: String,
        answers: List<String>,
        onAnswer: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        dismiss()

        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val backgroundColor = if (dark) Color.rgb(32, 31, 36) else Color.WHITE
        val foreground = if (dark) Color.rgb(242, 240, 244) else Color.rgb(30, 29, 32)
        val outline = if (dark) Color.rgb(147, 143, 153) else Color.rgb(121, 116, 126)
        val primary = if (dark) Color.rgb(208, 188, 255) else Color.rgb(103, 80, 164)

        var finished = false
        fun dismissByUser() {
            if (finished) return
            finished = true
            dismiss()
            onDismiss()
        }
        fun submit(rawAnswer: String) {
            val answer = rawAnswer.trim()
            if (finished || answer.isEmpty()) return
            finished = true
            dismiss()
            onAnswer(answer)
        }

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(110, 0, 0, 0))
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener { dismissByUser() }
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            clipToPadding = false
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(12), dp(20))
            background = roundedBackground(backgroundColor, dp(20).toFloat())
            isClickable = true // Consume taps so only the scrim dismisses the popup.
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = question
            setTextColor(foreground)
            textSize = 20f
            setLineSpacing(0f, 1.08f)
            setPadding(dp(8), dp(8), dp(8), dp(14))
            setTextIsSelectable(false)
            contentDescription = question
        }
        val close = TextView(context).apply {
            text = "×"
            setTextColor(foreground)
            textSize = 28f
            gravity = Gravity.CENTER
            contentDescription = "Close"
            isClickable = true
            isFocusable = true
            setOnClickListener { dismissByUser() }
        }
        header.addView(title, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ))
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        card.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        answers.forEach { answer ->
            val button = Button(context).apply {
                text = answer
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(foreground)
                textSize = 16f
                minHeight = dp(50)
                setPadding(dp(14), 0, dp(14), 0)
                background = outlinedBackground(backgroundColor, outline, dp(12).toFloat())
                setOnClickListener { submit(answer) }
            }
            card.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply { bottomMargin = dp(10) })
        }

        val customAnswer = EditText(context).apply {
            hint = "Enter something else"
            setHintTextColor(if (dark) Color.rgb(170, 166, 176) else Color.rgb(121, 116, 126))
            setTextColor(foreground)
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(14), 0, dp(14), 0)
            background = outlinedBackground(backgroundColor, primary, dp(12).toFloat())
            setOnEditorActionListener { _, actionId, event ->
                val isEnter = event != null &&
                    event.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                    event.action == android.view.KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_SEND || isEnter) {
                    submit(text.toString())
                    true
                } else {
                    false
                }
            }
            setOnClickListener {
                requestFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        card.addView(customAnswer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50)
        ))

        scroll.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.84f).toInt()
        ).apply {
            gravity = Gravity.CENTER
            leftMargin = dp(18)
            rightMargin = dp(18)
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            setTitle("Screen Operator question")
        }

        overlayView = root
        try {
            windowManager.addView(root, params)
            title.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
        } catch (error: Exception) {
            overlayView = null
            throw error
        }
    }

    fun dismiss() {
        val view = overlayView ?: return
        overlayView = null
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // The system may already have detached accessibility overlays during service shutdown.
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }

    private fun outlinedBackground(color: Int, strokeColor: Int, radius: Float) =
        roundedBackground(color, radius).apply { setStroke(dp(1), strokeColor) }
}
