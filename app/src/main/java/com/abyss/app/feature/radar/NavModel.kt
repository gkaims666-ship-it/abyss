package com.abyss.app.feature.radar

import kotlin.math.abs

/*
 * Turn-by-turn routing model. Pure Kotlin (no Android, no network) so the
 * engine-combining rules and instruction wording are unit-tested on a JVM.
 *
 * Several routing engines are queried in parallel (OSRM key-free; GraphHopper
 * and OpenRouteService with the user's own free keys). Each has blind spots —
 * a stale road, an odd speed profile — so instead of trusting one, the route
 * whose travel time sits closest to the engines' median is chosen: a single
 * outlier can't win, and with one engine available it simply is the answer.
 */

enum class NavMode(val id: String) { Car("car"), Foot("foot"), Bike("bike") }

/** One manoeuvre: what to do, how far/long until the next one, and where. */
data class NavStep(val text: String, val distM: Double, val durS: Double, val lat: Double, val lon: Double)

/** A full route from one engine. [points] are (lat, lon). */
data class NavRoute(
    val engine: String,
    val points: List<DoubleArray>,
    val distM: Double,
    val durS: Double,
    val steps: List<NavStep>,
)

/** The chosen route plus every engine that answered (for the "verified by" line and ETA range). */
data class NavResult(val best: NavRoute, val all: List<NavRoute>) {
    val engines: List<String> get() = all.map { it.engine }
    val minDurS: Double get() = all.minOf { it.durS }
    val maxDurS: Double get() = all.maxOf { it.durS }
}

object NavLogic {
    const val OSRM = "OSRM"
    const val GRAPHHOPPER = "GraphHopper"
    const val ORS = "OpenRouteService"

    /** Tie-break order: richer instructions first. */
    private val PRIORITY = listOf(GRAPHHOPPER, ORS, OSRM)

    /** Consensus pick: closest to the median duration; ties → engine priority. */
    fun pick(routes: List<NavRoute>): NavResult? {
        val ok = routes.filter { it.points.size >= 2 && it.durS > 0 && it.distM > 0 }
        if (ok.isEmpty()) return null
        if (ok.size == 1) return NavResult(ok[0], ok)
        val sorted = ok.map { it.durS }.sorted()
        val n = sorted.size
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        val best = ok.minWith(compareBy<NavRoute>({ abs(it.durS - median) / median }, { prio(it.engine) }))
        return NavResult(best, ok.sortedBy { prio(it.engine) })
    }

    private fun prio(e: String) = PRIORITY.indexOf(e).let { if (it < 0) 99 else it }

    /** Language the engines should speak: Russian for ru/uk/be, else English. */
    fun ruLike(lang: String): Boolean = lang.startsWith("ru") || lang.startsWith("uk") || lang.startsWith("be")

    /** GraphHopper locales it actually ships; anything else falls back to English. */
    fun ghLocale(lang: String): String {
        val l = lang.take(2)
        return if (l in setOf("ru", "uk", "es", "de", "fr", "pt", "tr", "zh", "ja", "en")) l else "en"
    }

    /** OpenRouteService instruction languages (no Ukrainian → Russian). */
    fun orsLanguage(lang: String): String {
        val l = lang.take(2)
        return when {
            l == "uk" || l == "be" -> "ru"
            l in setOf("ru", "es", "de", "fr", "pt", "tr", "zh", "ja", "en") -> l
            else -> "en"
        }
    }

    /** Builds a human instruction from an OSRM manoeuvre (OSRM returns no text). */
    fun osrmText(type: String?, modifier: String?, name: String?, exit: Int?, lang: String): String {
        val ru = ruLike(lang)
        val road = name?.takeIf { it.isNotBlank() }
        val on = if (road != null) (if (ru) " на $road" else " onto $road") else ""
        val along = if (road != null) (if (ru) " по $road" else " on $road") else ""
        val dir = dirWord(modifier, ru)
        val side = sideWord(modifier, ru)
        return when (type) {
            "depart" -> (if (ru) "Начните движение" else "Head out") + along
            "arrive" -> if (ru) "Вы прибыли" else "You have arrived"
            "turn", "end of road" -> {
                val base = if (modifier == "uturn") (if (ru) "Развернитесь" else "Make a U-turn")
                           else (if (ru) "Поверните $dir" else "Turn $dir")
                (if (type == "end of road") (if (ru) "В конце дороги " else "At the end of the road, ") +
                    base.replaceFirstChar { it.lowercase() } else base) + on
            }
            "continue", "new name", "notification" -> (if (ru) "Продолжайте $dir" else "Continue $dir") + along
            "fork" -> (if (ru) "На развилке держитесь $side" else "Keep $side at the fork") + on
            "merge" -> (if (ru) "Перестройтесь $side" else "Merge $side") + on
            "on ramp" -> (if (ru) "Въезд $dir" else "Take the ramp $dir") + on
            "off ramp" -> (if (ru) "Съезд $dir" else "Take the exit $dir") + on
            "roundabout", "rotary", "roundabout turn" -> {
                val ex = exit?.let { if (ru) ", $it-й съезд" else ", exit $it" } ?: ""
                (if (ru) "Круговое движение$ex" else "At the roundabout$ex") + on
            }
            "exit roundabout", "exit rotary" -> (if (ru) "Съезд с круга" else "Exit the roundabout") + on
            else -> (if (ru) "Продолжайте" else "Continue") + along
        }.trim()
    }

    private fun dirWord(m: String?, ru: Boolean): String = when (m) {
        "right" -> if (ru) "направо" else "right"
        "left" -> if (ru) "налево" else "left"
        "sharp right" -> if (ru) "резко направо" else "sharp right"
        "sharp left" -> if (ru) "резко налево" else "sharp left"
        "slight right" -> if (ru) "плавно направо" else "slight right"
        "slight left" -> if (ru) "плавно налево" else "slight left"
        "uturn" -> if (ru) "с разворотом" else "with a U-turn"
        else -> if (ru) "прямо" else "straight"
    }

    private fun sideWord(m: String?, ru: Boolean): String = when {
        m?.contains("right") == true -> if (ru) "правее" else "right"
        m?.contains("left") == true -> if (ru) "левее" else "left"
        else -> if (ru) "прямо" else "straight"
    }
}
