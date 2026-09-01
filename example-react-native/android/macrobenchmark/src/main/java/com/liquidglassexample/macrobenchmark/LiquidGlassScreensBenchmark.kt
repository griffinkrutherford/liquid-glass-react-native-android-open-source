package com.liquidglassexample.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class LiquidGlassScreensBenchmark {
  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

  @Test
  fun coldStartupSingleCard() = measure(
      metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
      startupMode = StartupMode.COLD,
      startInMeasuredBlock = true,
  ) {
    awaitText("Nested content")
    // Exercise the interactive glass content, not merely app startup.
    device.findObject(By.textContains("Tapped inside glass")).click()
    device.waitForIdle()
  }

  @Test
  fun multipleGlassElements() = measure {
    navigateTo("Multiple glass elements", "Draggable overlay — drag me across the tiles")
    device.waitForIdle()
  }

  @Test
  fun flatListScroll() = measure {
    navigateTo("Glass over a FlatList", "Overlay above the list")
    repeat(4) {
      val list = device.findObject(By.scrollable(true))
      if (list != null) list.fling(Direction.DOWN) else device.swipe(
          device.displayWidth / 2,
          device.displayHeight * 3 / 4,
          device.displayWidth / 2,
          device.displayHeight / 4,
          12,
      )
      device.waitForIdle()
    }
  }

  @Test
  fun dragInteraction() = measure {
    navigateTo("Glass over a ScrollView", "Floating control")
    val control = device.findObject(By.text("Floating control"))
    val bounds = control.visibleBounds
    device.drag(
        bounds.centerX(),
        bounds.centerY(),
        (bounds.centerX() + device.displayWidth / 4).coerceAtMost(device.displayWidth - 1),
        (bounds.centerY() - device.displayHeight / 5).coerceAtLeast(1),
        60,
    )
    device.waitForIdle()
  }

  private fun measure(
      metrics: List<Metric> = listOf(FrameTimingMetric()),
      startupMode: StartupMode = StartupMode.WARM,
      startInMeasuredBlock: Boolean = false,
      block: () -> Unit,
  ) {
    benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = metrics,
        compilationMode = CompilationMode.Partial(),
        startupMode = startupMode,
        iterations = 5,
        setupBlock = {
          pressHome()
          if (!startInMeasuredBlock) startActivityAndWait()
        },
    ) {
      if (startInMeasuredBlock) startActivityAndWait()
      block()
    }
  }

  private fun navigateTo(button: String, destinationMarker: String) {
    awaitText("Nested content")
    device.findObject(By.text(button)).click()
    awaitText(destinationMarker)
  }

  private fun awaitText(text: String) {
    check(device.wait(Until.hasObject(By.text(text)), TIMEOUT_MS)) {
      "Timed out waiting for '$text'"
    }
  }

  private companion object {
    const val PACKAGE_NAME = "com.liquidglassexample"
    const val TIMEOUT_MS = 15_000L
  }
}
