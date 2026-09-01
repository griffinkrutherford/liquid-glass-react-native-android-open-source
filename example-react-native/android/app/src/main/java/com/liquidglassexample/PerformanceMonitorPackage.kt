package com.liquidglassexample

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.ViewManager

class PerformanceMonitorPackage : ReactPackage {
  override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
      listOf(PerformanceMonitorModule(context))

  override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}

private class PerformanceMonitorModule(context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
  override fun getName() = "PerformanceMonitor"

  @ReactMethod
  fun setScreen(screen: String) = ExampleFrameReporter.setScreen(screen)
}
