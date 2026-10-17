package com.abyss.app.feature.radar

/** A place the user saved on the map, to return to later. */
data class Bookmark(val id: String, val name: String, val lat: Double, val lon: Double)

/**
 * Saved-places storage format. Pure (no Android), so the encode/decode and the
 * add/remove rules are unit-tested. One bookmark per line, fields separated by
 * a unit-separator so names may contain anything printable.
 */
object Bookmarks {
    private const val SEP = "\u001F"

    fun idFor(lat: Double, lon: Double): String =
        "%.5f,%.5f".format(java.util.Locale.US, lat, lon)

    fun encode(list: List<Bookmark>): String =
        list.joinToString("\n") { listOf(it.id, it.lat.toString(), it.lon.toString(), it.name).joinToString(SEP) }

    fun decode(blob: String?): List<Bookmark> {
        if (blob.isNullOrBlank()) return emptyList()
        return blob.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val p = line.split(SEP)
            if (p.size < 4) return@mapNotNull null
            val lat = p[1].toDoubleOrNull() ?: return@mapNotNull null
            val lon = p[2].toDoubleOrNull() ?: return@mapNotNull null
            Bookmark(p[0], p.subList(3, p.size).joinToString(SEP), lat, lon)
        }.toList()
    }

    /** Add (or rename in place), newest first, deduped by id. */
    fun add(list: List<Bookmark>, b: Bookmark): List<Bookmark> =
        listOf(b) + list.filterNot { it.id == b.id }

    fun remove(list: List<Bookmark>, id: String): List<Bookmark> = list.filterNot { it.id == id }

    fun has(list: List<Bookmark>, lat: Double, lon: Double): Boolean {
        val id = idFor(lat, lon)
        return list.any { it.id == id }
    }
}
