package io.github.griffinkrutherford.liquidglass.sample

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import io.github.griffinkrutherford.liquidglass.LiquidGlassScene
import io.github.griffinkrutherford.liquidglass.LiquidGlassEffect
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

        val cardWidth = (resources.displayMetrics.widthPixels - dp(54)) / 2
        val clearCard = weatherCard("Wrocław", "25°", "Sunny", LiquidGlassEffect.CLEAR, Color.rgb(255, 190, 105))
        root.addView(clearCard, ViewGroup.MarginLayoutParams(cardWidth, dp(245)).apply {
            leftMargin = dp(18)
            topMargin = dp(175)
        })

        val regularCard = weatherCard("Miami", "35°", "Sunny", LiquidGlassEffect.REGULAR, Color.WHITE)
        root.addView(regularCard, ViewGroup.MarginLayoutParams(cardWidth, dp(245)).apply {
            leftMargin = dp(36) + cardWidth
            topMargin = dp(175)
        })

        val buttonGlass = LiquidGlassView(this).apply {
            effect = LiquidGlassEffect.REGULAR
            interactive = true
            cornerRadius = dp(28).toFloat()
            refractionStrength = dp(18).toFloat()
            addView(label("Interactive regular glass", 20f, Color.WHITE), ViewGroup.MarginLayoutParams(wrap, wrap).apply {
                leftMargin = dp(52)
                topMargin = dp(28)
            })
            setOnClickListener {
                effect = if (effect == LiquidGlassEffect.NONE) LiquidGlassEffect.REGULAR else LiquidGlassEffect.NONE
            }
        }
        root.addView(buttonGlass, ViewGroup.MarginLayoutParams(resources.displayMetrics.widthPixels - dp(36), dp(88)).apply {
            leftMargin = dp(18)
            topMargin = dp(445)
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
        root.addView(label("CLEAR  •  REGULAR  •  INTERACTIVE  •  TINT", 11f, Color.rgb(148, 197, 220)),
            ViewGroup.MarginLayoutParams(wrap, wrap).apply {
                leftMargin = dp(24)
                topMargin = dp(565)
            })
        setContentView(root)
    }

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        letterSpacing = if (size <= 13f) 0.08f else 0f
    }

    private fun weatherCard(
        city: String,
        temperature: String,
        description: String,
        glassEffect: LiquidGlassEffect,
        tint: Int,
    ) = LiquidGlassView(this).apply {
        effect = glassEffect
        interactive = true
        cornerRadius = dp(28).toFloat()
        refractionStrength = dp(if (glassEffect == LiquidGlassEffect.CLEAR) 24 else 18).toFloat()
        tintColor = tint
        tintAmount = if (glassEffect == LiquidGlassEffect.CLEAR) 0.10f else 0.08f
        addView(label(city, 18f, Color.WHITE), ViewGroup.MarginLayoutParams(wrap, wrap).apply {
            leftMargin = dp(18)
            topMargin = dp(18)
        })
        addView(label(temperature, 48f, Color.WHITE), ViewGroup.MarginLayoutParams(wrap, wrap).apply {
            leftMargin = dp(16)
            topMargin = dp(53)
        })
        addView(label("☀", 34f, tint), ViewGroup.MarginLayoutParams(wrap, wrap).apply {
            leftMargin = dp(18)
            topMargin = dp(124)
        })
        addView(label(description, 17f, Color.WHITE), ViewGroup.MarginLayoutParams(wrap, wrap).apply {
            leftMargin = dp(18)
            topMargin = dp(184)
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
