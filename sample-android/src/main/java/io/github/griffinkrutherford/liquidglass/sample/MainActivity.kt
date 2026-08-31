package io.github.griffinkrutherford.liquidglass.sample

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 18, 31)
        window.navigationBarColor = Color.rgb(7, 18, 31)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 18, 31))
        }
        val demo = LiquidGlassDemoView(this)
        root.addView(demo, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        root.addView(label("LIQUID GLASS LAB", 13f, Color.rgb(125, 211, 252)),
            FrameLayout.LayoutParams(wrap, wrap).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(24)
                topMargin = dp(30)
            })
        root.addView(label("Touch the surface", 28f, Color.WHITE),
            FrameLayout.LayoutParams(wrap, wrap).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(24)
                topMargin = dp(54)
            })
        root.addView(label("Drag to send ripples through the mesh", 14f, Color.rgb(176, 198, 216)),
            FrameLayout.LayoutParams(wrap, wrap).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(25)
                topMargin = dp(96)
            })
        root.addView(label("Custom spring-membrane physics  •  Live", 12f, Color.rgb(148, 197, 220)),
            FrameLayout.LayoutParams(wrap, wrap).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(26)
            })
        setContentView(root)
    }

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        letterSpacing = if (size <= 13f) 0.08f else 0f
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
