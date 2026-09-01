package com.liquidglassexample

import android.os.Bundle
import androidx.metrics.performance.JankStats
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

  private lateinit var jankStats: JankStats

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "LiquidGlassExample"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  /**
   * Required by react-native-screens. Passing null discards the Android-restored fragment
   * hierarchy so React Navigation rebuilds its screens from JavaScript state instead.
   * See https://reactnavigation.org/docs/getting-started/#installing-dependencies-into-a-bare-react-native-project
   */
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(null)
    jankStats = JankStats.createAndTrack(window, ExampleFrameReporter::record)
  }

  override fun onStart() {
    super.onStart()
    if (::jankStats.isInitialized) jankStats.isTrackingEnabled = true
  }

  override fun onStop() {
    if (::jankStats.isInitialized) {
      jankStats.isTrackingEnabled = false
      ExampleFrameReporter.flush()
    }
    super.onStop()
  }
}
