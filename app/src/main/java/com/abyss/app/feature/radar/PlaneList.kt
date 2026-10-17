package com.abyss.app.feature.radar

/**
 * Selecting and ordering the aircraft currently on screen, for the "in view"
 * list. Pure, so it is unit-tested. Planes with no position are dropped;
 * the rest are filtered to the viewport and sorted nearest-to-centre first,
 * airborne before on-ground.
 */
object PlaneList {
    fun inView(
        planes: List<Plane>, north: Double, south: Double, east: Double, west: Double,
        centerLat: Double, centerLon: Double, limit: Int = 200,
    ): List<Plane> {
        val crosses = west > east   // viewport spans the antimeridian
        fun lonIn(lon: Double) = if (crosses) lon >= west || lon <= east else lon in west..east
        return planes.asSequence()
            .filter { it.lat != 0.0 || it.lon != 0.0 }
            .filter { it.lat in south..north && lonIn(it.lon) }
            .sortedWith(
                compareBy({ if (it.onGround) 1 else 0 },
                    { distanceNm(centerLat, centerLon, it.lat, it.lon) }),
            )
            .take(limit)
            .toList()
    }
}
