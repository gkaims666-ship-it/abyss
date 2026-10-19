package com.abyss.app.feature.radar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Political-map data: country outlines + Russian names, from Natural Earth
 * (public domain, 1:110m). Pure Kotlin so parsing is unit-testable on a plain
 * JVM; the compact asset is app/src/main/assets/countries_ru.json, shaped as
 * [{"n":"Россия","x":<labelLon>,"y":<labelLat>,"r":[[[lon,lat],…ring…], …]}, …].
 */

/** One country: its Russian name, a label anchor (lon/lat) and fill rings. */
data class Country(
    val name: String,
    val labelLon: Double,
    val labelLat: Double,
    /** Rings; each point is [lon, lat]. */
    val rings: List<List<DoubleArray>>,
)

object PoliticalMap {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): List<Country> {
        val arr = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = runCatching { e.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = o["n"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val lx = o["x"]?.jsonPrimitive?.double ?: 0.0
            val ly = o["y"]?.jsonPrimitive?.double ?: 0.0
            val rings = (o["r"] as? JsonArray)?.mapNotNull { r ->
                (r as? JsonArray)?.mapNotNull { pt ->
                    val a = pt as? JsonArray ?: return@mapNotNull null
                    if (a.size < 2) return@mapNotNull null
                    doubleArrayOf(a[0].jsonPrimitive.double, a[1].jsonPrimitive.double)
                }
            }?.filter { it.size >= 4 } ?: emptyList()
            if (rings.isEmpty()) return@mapNotNull null
            Country(name, lx, ly, rings)
        }
    }

    /**
     * HoI4-style palette: a small set of distinct, slightly desaturated fills
     * assigned round-robin so neighbouring countries read apart. Bold dark
     * borders are drawn on top by the renderer.
     */
    val FILLS = intArrayOf(
        0xFF8C6E57.toInt(), 0xFF5E7E8C.toInt(), 0xFF7E8C5E.toInt(), 0xFF8C5E6E.toInt(),
        0xFF6E5E8C.toInt(), 0xFF5E8C73.toInt(), 0xFF8C7E5E.toInt(), 0xFF738C5E.toInt(),
        0xFF5E6E8C.toInt(), 0xFF8C5E5E.toInt(), 0xFF5E8C8C.toInt(), 0xFF8C8C5E.toInt(),
    )
}
