package io.github.griffinkrutherford.liquidglass.sample

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import io.github.griffinkrutherford.liquidglass.LiquidGlassEffect
import io.github.griffinkrutherford.liquidglass.LiquidGlassScene
import io.github.griffinkrutherford.liquidglass.LiquidGlassView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 18, 30)
        window.navigationBarColor = Color.rgb(5, 18, 30)

        val scene = LiquidGlassScene(this)
        scene.addView(
            BackdropArtworkView(this),
            ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        scene.addView(label("LIQUID GLASS", 13f, Color.WHITE), margins(24, 34))
        scene.addView(label("Move the lens", 34f, Color.WHITE), margins(22, 58))
        scene.addView(
            label("Drag across the 3K photo, then tap to switch material", 14f, Color.argb(220, 255, 255, 255)),
            margins(23, 108),
        )
        scene.addView(label("REFRACTION  •  REFLECTION  •  PHYSICS", 11f, Color.WHITE), margins(24, 742))

        val materialLabel = label("◇  Crystal", 20f, Color.WHITE)
        val card = LiquidGlassView(this).apply {
            effect = LiquidGlassEffect.CLEAR
            interactive = true
            draggable = true
            cornerRadius = dp(34).toFloat()
            refractionStrength = dp(28).toFloat()
            dispersion = dp(1.5f).toFloat()
            indexOfRefraction = 1.50f
            bevelDepth = dp(27).toFloat()
            baseThickness = dp(8).toFloat()
            blurRadius = dp(2).toFloat()
            tintColor = Color.rgb(220, 242, 255)
            tintAmount = 0.075f
            contentDescription = "Draggable liquid glass weather card"

            addView(label("SANUR, BALI", 14f, Color.WHITE), margins(22, 20))
            addView(label("29°", 58f, Color.WHITE), margins(20, 48))
            addView(materialLabel, margins(23, 126))
            addView(label("Drag to move  •  Tap to change material", 13f, Color.argb(225, 255, 255, 255)), margins(23, 164))
            setOnClickListener {
                effect = when (effect) {
                    LiquidGlassEffect.CLEAR -> LiquidGlassEffect.SATIN
                    LiquidGlassEffect.SATIN -> LiquidGlassEffect.NOCTURNE
                    else -> LiquidGlassEffect.CLEAR
                }
                materialLabel.text = when (effect) {
                    LiquidGlassEffect.SATIN -> "◌  Satin"
                    LiquidGlassEffect.NOCTURNE -> "●  Nocturne"
                    else -> "◇  Crystal"
                }
            }
        }
        scene.addView(card, ViewGroup.MarginLayoutParams(resources.displayMetrics.widthPixels - dp(48), dp(212)).apply {
            leftMargin = dp(24)
            topMargin = dp(278)
        })

        val thicknessLabel = label("OPTICAL THICKNESS  •  8.0", 11f, Color.WHITE)
        scene.addView(thicknessLabel, margins(24, 668))
        scene.addView(
            SeekBar(this).apply {
                max = 240
                progress = 80
                progressTintList = ColorStateList.valueOf(Color.rgb(174, 154, 255))
                progressBackgroundTintList = ColorStateList.valueOf(Color.argb(105, 255, 255, 255))
                thumbTintList = ColorStateList.valueOf(Color.rgb(174, 154, 255))
                contentDescription = "Glass optical thickness"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val thickness = progress / 10f
                        card.baseThickness = dp(thickness).toFloat()
                        thicknessLabel.text = "OPTICAL THICKNESS  •  %.1f".format(thickness)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            },
            ViewGroup.MarginLayoutParams(resources.displayMetrics.widthPixels - dp(48), dp(44)).apply {
                leftMargin = dp(24)
                topMargin = dp(687)
            },
        )

        setContentView(scene)
    }

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        letterSpacing = if (size <= 14f) 0.06f else 0f
    }

    private fun margins(left: Int, top: Int) =
        ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(left)
            topMargin = dp(top)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
}
