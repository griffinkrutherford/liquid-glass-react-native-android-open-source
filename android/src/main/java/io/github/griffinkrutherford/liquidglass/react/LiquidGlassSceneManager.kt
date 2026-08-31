package io.github.griffinkrutherford.liquidglass.react

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.RNLiquidGlassSceneManagerDelegate
import com.facebook.react.viewmanagers.RNLiquidGlassSceneManagerInterface
import io.github.griffinkrutherford.liquidglass.LiquidGlassScene

@ReactModule(name = LiquidGlassSceneManager.NAME)
class LiquidGlassSceneManager :
    ViewGroupManager<LiquidGlassScene>(),
    RNLiquidGlassSceneManagerInterface<LiquidGlassScene> {
    private val delegate: ViewManagerDelegate<LiquidGlassScene> = RNLiquidGlassSceneManagerDelegate(this)

    override fun getName(): String = NAME

    override fun createViewInstance(reactContext: ThemedReactContext): LiquidGlassScene =
        LiquidGlassScene(reactContext).apply { managesChildLayout = false }

    override fun needsCustomLayoutForChildren(): Boolean = false

    override fun getDelegate(): ViewManagerDelegate<LiquidGlassScene> = delegate

    companion object {
        const val NAME = "RNLiquidGlassScene"
    }
}
