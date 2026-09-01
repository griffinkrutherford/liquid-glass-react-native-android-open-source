package io.github.griffinkrutherford.liquidglass.react

import android.graphics.Color
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.PixelUtil
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.RNLiquidGlassViewManagerDelegate
import com.facebook.react.viewmanagers.RNLiquidGlassViewManagerInterface
import io.github.griffinkrutherford.liquidglass.LiquidGlassColorScheme
import io.github.griffinkrutherford.liquidglass.LiquidGlassEffect
import io.github.griffinkrutherford.liquidglass.LiquidGlassView

/**
 * Fabric/legacy manager for the glass surface.
 *
 * Unit contract with JavaScript, mirrored in the TypeScript prop documentation:
 * - `cornerRadius`, `refractionStrength`, `bevelDepth`, `thickness` and `blurRadius` arrive as
 *   React Native density-independent pixels and are converted with [dip].
 * - `animationDuration` arrives in milliseconds.
 * - `indexOfRefraction`, `dispersion`, `effectAmount` and `tintAmount` are dimensionless and are
 *   forwarded unscaled.
 *
 * Material presets (`material="crystal" | "satin" | "nocturne"`) are resolved in JavaScript, so
 * every prop below always arrives with a concrete, already-merged value. Under Fabric a prop
 * carries its default even when the caller never supplied it, so preset merge semantics cannot be
 * implemented here.
 *
 * Out-of-range values are clamped by `LiquidGlassView`; JavaScript warns about them in `__DEV__`.
 */
@ReactModule(name = LiquidGlassViewManager.NAME)
class LiquidGlassViewManager :
    ViewGroupManager<LiquidGlassView>(),
    RNLiquidGlassViewManagerInterface<LiquidGlassView> {
    private val delegate: ViewManagerDelegate<LiquidGlassView> = RNLiquidGlassViewManagerDelegate(this)

    override fun getName(): String = NAME

    override fun createViewInstance(reactContext: ThemedReactContext): LiquidGlassView =
        LiquidGlassView(reactContext).apply { managesChildLayout = false }

    override fun needsCustomLayoutForChildren(): Boolean = false

    override fun getDelegate(): ViewManagerDelegate<LiquidGlassView> = delegate

    @ReactProp(name = "effect")
    override fun setEffect(view: LiquidGlassView, value: String?) {
        view.effect = when (value) {
            "clear" -> LiquidGlassEffect.CLEAR
            "satin" -> LiquidGlassEffect.SATIN
            "nocturne" -> LiquidGlassEffect.NOCTURNE
            "none" -> LiquidGlassEffect.NONE
            else -> LiquidGlassEffect.REGULAR
        }
    }

    @ReactProp(name = "interactive", defaultBoolean = false)
    override fun setInteractive(view: LiquidGlassView, value: Boolean) { view.interactive = value }

    @ReactProp(name = "draggable", defaultBoolean = false)
    override fun setDraggable(view: LiquidGlassView, value: Boolean) { view.draggable = value }

    @ReactProp(name = "animated", defaultBoolean = true)
    override fun setAnimated(view: LiquidGlassView, value: Boolean) { view.animated = value }

    @ReactProp(name = "animationDuration", defaultFloat = 320f)
    override fun setAnimationDuration(view: LiquidGlassView, value: Float) { view.animationDurationMillis = value.toLong() }

    @ReactProp(name = "cornerRadius", defaultFloat = 32f)
    override fun setCornerRadius(view: LiquidGlassView, value: Float) { view.cornerRadius = dip(value) }

    @ReactProp(name = "refractionStrength", defaultFloat = 24f)
    override fun setRefractionStrength(view: LiquidGlassView, value: Float) { view.refractionStrength = dip(value) }

    /**
     * Dimensionless. The chromatic split it produces already scales with the dp-based refraction
     * geometry, so converting it from DIP would scale the fringe a second time and make the same
     * JavaScript value look different on every screen density. Passed through unscaled.
     */
    @ReactProp(name = "dispersion", defaultFloat = 2.4f)
    override fun setDispersion(view: LiquidGlassView, value: Float) { view.dispersion = value }

    @ReactProp(name = "indexOfRefraction", defaultFloat = 1.47f)
    override fun setIndexOfRefraction(view: LiquidGlassView, value: Float) { view.indexOfRefraction = value }

    @ReactProp(name = "bevelDepth", defaultFloat = 22f)
    override fun setBevelDepth(view: LiquidGlassView, value: Float) { view.bevelDepth = dip(value) }

    @ReactProp(name = "thickness", defaultFloat = 6f)
    override fun setThickness(view: LiquidGlassView, value: Float) { view.baseThickness = dip(value) }

    @ReactProp(name = "blurRadius", defaultFloat = 2.2f)
    override fun setBlurRadius(view: LiquidGlassView, value: Float) { view.blurRadius = dip(value) }

    @ReactProp(name = "effectAmount", defaultFloat = 0.96f)
    override fun setEffectAmount(view: LiquidGlassView, value: Float) { view.effectAmount = value }

    @ReactProp(name = "tintColor", customType = "Color")
    override fun setTintColor(view: LiquidGlassView, value: Int?) { view.tintColor = value ?: Color.rgb(190, 229, 255) }

    @ReactProp(name = "tintAmount", defaultFloat = 0.11f)
    override fun setTintAmount(view: LiquidGlassView, value: Float) { view.tintAmount = value }

    @ReactProp(name = "colorScheme")
    override fun setColorScheme(view: LiquidGlassView, value: String?) {
        view.colorScheme = when (value) {
            "light" -> LiquidGlassColorScheme.LIGHT
            "dark" -> LiquidGlassColorScheme.DARK
            else -> LiquidGlassColorScheme.SYSTEM
        }
    }

    private fun dip(value: Float): Float = PixelUtil.toPixelFromDIP(value)

    companion object {
        const val NAME = "RNLiquidGlassView"
    }
}
