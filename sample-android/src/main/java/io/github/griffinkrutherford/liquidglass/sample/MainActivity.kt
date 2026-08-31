package io.github.griffinkrutherford.liquidglass.sample

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import io.github.griffinkrutherford.liquidglass.LiquidGlassScene
import io.github.griffinkrutherford.liquidglass.LiquidGlassView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 18, 31)
        window.navigationBarColor = Color.rgb(7, 18, 31)

        val root = LiquidGlassScene(this).apply {
            setBackgroundColor(Color.rgb(7, 18, 31))
        }

        root.addView(BackdropArtworkView(this), ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        val glass = LiquidGlassView(this).apply {
            contentDescription = "Interactive refractive liquid glass surface"
            cornerRadius = dp(34).toFloat()
            refractionStrength = dp(22).toFloat()
            dispersion = dp(3).toFloat()
            blurRadius = dp(2).toFloat()
        }
        root.addView(glass, ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(540),
        ).apply {
            leftMargin = dp(18)
            rightMargin = dp(18)
            topMargin = dp(175)
        })

        root.addView(label("LIQUID GLASS LAB", 13f, Color.rgb(125, 211, 252)),
            ViewGroup.MarginLayoutParams(wrap, wrap).apply {
                leftMargin = dp(24)
                topMargin = dp(30)
            })
        root.addView(label("Touch the surface", 28f, Color.WHITE),
            ViewGroup.MarginLayoutParams(wrap, wrap).apply {
                leftMargin = dp(24)
                topMargin = dp(54)
            })
        root.addView(label("Drag to bend the elements behind the glass", 14f, Color.rgb(176, 198, 216)),
            ViewGroup.MarginLayoutParams(wrap, wrap).apply {
                leftMargin = dp(25)
                topMargin = dp(96)
            })
        root.addView(label("REFRACTION  •  DISPERSION  •  FRESNEL  •  PHYSICS", 11f, Color.rgb(148, 197, 220)),
            ViewGroup.MarginLayoutParams(wrap, wrap).apply {
                leftMargin = dp(24)
                topMargin = dp(742)
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
