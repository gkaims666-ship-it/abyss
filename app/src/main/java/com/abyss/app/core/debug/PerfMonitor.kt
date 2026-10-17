package com.abyss.app.core.debug

import android.os.Build
import kotlin.math.roundToInt

/**
 * A tiny, allocation-free frame-time sampler. The living-background frame loop
 * calls [frame] once per rendered frame with the inter-frame delta; everything
 * here is O(1). The debug self-review reads [snapshot] to tell the model how the
 * app is actually performing on *this* device, right now.
 *
 * Kept deliberately simple (a ring of recent frame deltas) so it costs nothing
 * on the hot path and needs no Choreographer/JankStats dependency.
 */
object PerfMonitor {
    private const val CAP = 240                 // ~2–4 s of frames
    private val ring = FloatArray(CAP)          // frame deltas, ms
    private var head = 0
    private var filled = 0
    @Volatile var totalFrames = 0L; private set
    @Volatile var jankFrames = 0L; private set  // frames over ~1.5× the target budget

    /** Nominal display refresh; set once from the Activity (defaults to 60). */
    @Volatile var refreshHz = 60f
    private val budgetMs get() = 1000f / refreshHz

    /** Record one rendered frame. [dtSec] is the delta used by the sim. */
    fun frame(dtSec: Float) {
        if (dtSec <= 0f) return
        val ms = dtSec * 1000f
        ring[head] = ms
        head = (head + 1) % CAP
        if (filled < CAP) filled++
        totalFrames++
        if (ms > budgetMs * 1.5f) jankFrames++
    }

    data class Snapshot(
        val samples: Int,
        val refreshHz: Int,
        val avgFps: Int,
        val p50Ms: Float,
        val p95Ms: Float,
        val worstMs: Float,
        val jankPct: Float,
        val totalFrames: Long,
        val device: String,
        val androidApi: Int,
        val abi: String,
    )

    fun snapshot(): Snapshot {
        val n = filled
        if (n == 0) return Snapshot(0, refreshHz.roundToInt(), 0, 0f, 0f, 0f, 0f,
            totalFrames, deviceName(), Build.VERSION.SDK_INT, abi())
        val copy = FloatArray(n) { ring[it] }
        copy.sort()
        val p50 = copy[(n * 0.50f).toInt().coerceIn(0, n - 1)]
        val p95 = copy[(n * 0.95f).toInt().coerceIn(0, n - 1)]
        val worst = copy[n - 1]
        val mean = copy.average().toFloat()
        val avgFps = if (mean > 0f) (1000f / mean).roundToInt() else 0
        val jankPct = if (totalFrames > 0) jankFrames * 100f / totalFrames else 0f
        return Snapshot(n, refreshHz.roundToInt(), avgFps,
            (p50 * 10).roundToInt() / 10f, (p95 * 10).roundToInt() / 10f,
            (worst * 10).roundToInt() / 10f, (jankPct * 10).roundToInt() / 10f,
            totalFrames, deviceName(), Build.VERSION.SDK_INT, abi())
    }

    /** A compact, model-readable report of current on-device performance. */
    fun report(): String {
        val s = snapshot()
        return buildString {
            appendLine("Device: ${s.device} (Android API ${s.androidApi}, ${s.abi})")
            appendLine("Display: ${s.refreshHz} Hz target")
            if (s.samples == 0) {
                append("No frames sampled yet.")
                return@buildString
            }
            appendLine("Frames sampled: ${s.samples} (lifetime ${s.totalFrames})")
            appendLine("Avg FPS: ${s.avgFps}")
            appendLine("Frame time p50: ${s.p50Ms} ms, p95: ${s.p95Ms} ms, worst: ${s.worstMs} ms")
            append("Jank (>1.5x budget): ${s.jankPct}%")
        }
    }

    private fun deviceName(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private fun abi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    fun reset() { head = 0; filled = 0; totalFrames = 0; jankFrames = 0 }
}
