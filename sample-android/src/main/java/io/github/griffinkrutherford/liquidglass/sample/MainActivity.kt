package io.github.griffinkrutherford.liquidglass.sample

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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

        val materialLabel = label("◇  Crystal", 20f, Color.WHITE)
        val card = LiquidGlassView(this).apply {
            effect = LiquidGlassEffect.CLEAR
            interactive = true
            draggable = true
            cornerRadius = dp(34).toFloat()
            refractionStrength = dp(28).toFloat()
            dispersion = 1.5f
            indexOfRefraction = 1.50f
            bevelDepth = dp(27).toFloat()
            baseThickness = dp(8).toFloat()
            blurRadius = dp(2).toFloat()
            tintColor = Color.rgb(220, 242, 255)
            tintAmount = 0.08f
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

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), dp(24))
        }
        val tintLabel = label("TINT  •  8%", 11f, Color.WHITE)
        controls.addView(tintLabel, controlRow(0))
        controls.addView(
            controlSeekBar(
                max = 100,
                initialProgress = 8,
                description = "Glass tint strength",
            ) { progress ->
                card.tintAmount = progress / 100f
                tintLabel.text = "TINT  •  $progress%"
            },
            controlRow(4, 44),
        )

        val blurLabel = label("BLUR  •  2.0 DP", 11f, Color.WHITE)
        controls.addView(blurLabel, controlRow(12))
        controls.addView(
            controlSeekBar(
                max = 120,
                initialProgress = 20,
                description = "Glass blur radius",
            ) { progress ->
                val blur = progress / 10f
                card.blurRadius = px(blur)
                blurLabel.text = "BLUR  •  %.1f DP".format(blur)
            },
            controlRow(4, 44),
        )

        controls.addView(label("TINT COLOR", 11f, Color.WHITE), controlRow(12))
        val tintColors = listOf(
            "ICE" to Color.rgb(220, 242, 255),
            "VIOLET" to Color.rgb(210, 190, 255),
            "ROSE" to Color.rgb(255, 190, 210),
            "MINT" to Color.rgb(180, 255, 220),
        )
        controls.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                tintColors.forEach { (name, color) ->
                    addView(Button(this@MainActivity).apply {
                        text = name
                        textSize = 10f
                        setTextColor(if (name == "ICE") Color.rgb(5, 18, 30) else Color.WHITE)
                        backgroundTintList = ColorStateList.valueOf(color)
                        contentDescription = "$name glass tint"
                        setOnClickListener { card.tintColor = color }
                    }, LinearLayout.LayoutParams(0, dp(42), 1f))
                }
            },
            controlRow(6, 42),
        )

        val thicknessLabel = label("OPTICAL THICKNESS  •  8.0", 11f, Color.WHITE)
        controls.addView(thicknessLabel, controlRow(12))
        controls.addView(
            controlSeekBar(
                max = 640,
                initialProgress = 80,
                description = "Glass optical thickness",
            ) { progress ->
                val thickness = progress / 10f
                card.baseThickness = px(thickness)
                thicknessLabel.text = "OPTICAL THICKNESS  •  %.1f".format(thickness)
            },
            controlRow(4, 44),
        )
        controls.addView(label("REFRACTION  •  REFLECTION  •  PHYSICS", 11f, Color.WHITE), controlRow(12))
        scene.addView(
            ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                addView(controls, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            },
            ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { topMargin = dp(510) },
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

    private fun controlSeekBar(
        max: Int,
        initialProgress: Int,
        description: String,
        onChanged: (Int) -> Unit,
    ) = SeekBar(this).apply {
        this.max = max
        progress = initialProgress
        progressTintList = ColorStateList.valueOf(Color.rgb(174, 154, 255))
        progressBackgroundTintList = ColorStateList.valueOf(Color.argb(105, 255, 255, 255))
        thumbTintList = ColorStateList.valueOf(Color.rgb(174, 154, 255))
        contentDescription = description
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChanged(progress)

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun controlRow(top: Int, height: Int? = null) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height?.let(::dp)
            ?: ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun px(value: Float): Float = value * resources.displayMetrics.density
}
