package com.liquidglassexample

import android.util.Log
import androidx.metrics.performance.FrameData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Logcat reporting intended for local profiling and benchmark result correlation. */
object ExampleFrameReporter {
  private const val TAG = "LiquidGlassJank"
  private const val REPORT_EVERY_FRAMES = 120L

  @Volatile private var screen = "Startup"
  private val screens = ConcurrentHashMap<String, ScreenFrames>()

  fun setScreen(value: String) {
    screen = value.ifBlank { "Unknown" }
    Log.i(TAG, "event=screen screen=${screen}")
  }

  fun record(frameData: FrameData) {
    val name = screen
    val stats = screens.getOrPut(name) { ScreenFrames() }
    val total = stats.total.incrementAndGet()
    if (frameData.isJank) stats.janky.incrementAndGet()
    stats.durationNanos.addAndGet(frameData.frameDurationUiNanos)

    if (frameData.isJank || total % REPORT_EVERY_FRAMES == 0L) report(name, stats, frameData)
  }

  fun flush() {
    screens.forEach { (name, stats) -> report(name, stats, null) }
  }

  private fun report(name: String, stats: ScreenFrames, frame: FrameData?) {
    val total = stats.total.get()
    val janky = stats.janky.get()
    val averageMs = if (total == 0L) 0.0 else stats.durationNanos.get() / total / 1_000_000.0
    Log.i(
        TAG,
        "event=frames screen=$name total=$total janky=$janky " +
            "jankPercent=${"%.2f".format(if (total == 0L) 0.0 else janky * 100.0 / total)} " +
            "averageUiMs=${"%.2f".format(averageMs)} latestJank=${frame?.isJank ?: false}",
    )
  }

  private class ScreenFrames(
      val total: AtomicLong = AtomicLong(),
      val janky: AtomicLong = AtomicLong(),
      val durationNanos: AtomicLong = AtomicLong(),
  )
}
