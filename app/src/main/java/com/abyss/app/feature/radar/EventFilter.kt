package com.abyss.app.feature.radar

/**
 * What to show on the events overlay. Pure, so [apply] and the prefs
 * encode/decode are unit-tested. [minMag] only gates earthquakes; news is
 * gated by [showNews] alone.
 */
data class EventFilter(
    val showQuakes: Boolean = true,
    val showNews: Boolean = true,
    val minMag: Double = 0.0,
) {
    fun apply(events: List<MapEvent>): List<MapEvent> = events.filter { e ->
        when (e.kind) {
            MapEvent.Kind.Quake -> showQuakes && (e.mag ?: 0.0) >= minMag
            MapEvent.Kind.News -> showNews
        }
    }

    fun encode(): String = "${showQuakes.toInt()}${showNews.toInt()}${minMag}"

    companion object {
        val MAG_STEPS = listOf(0.0, 2.5, 4.0, 5.0, 6.0)

        private fun Boolean.toInt() = if (this) 1 else 0

        fun decode(s: String?): EventFilter {
            if (s == null || s.length < 3) return EventFilter()
            val mag = s.substring(2).toDoubleOrNull() ?: 0.0
            return EventFilter(s[0] == '1', s[1] == '1', mag)
        }

        /** Next magnitude threshold in the cycle, wrapping around. */
        fun nextMag(current: Double): Double {
            val i = MAG_STEPS.indexOfFirst { kotlin.math.abs(it - current) < 0.01 }
            return MAG_STEPS[(if (i < 0) 0 else i + 1) % MAG_STEPS.size]
        }
    }
}
