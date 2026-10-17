package com.abyss.app.feature.radar

import java.util.Locale

/**
 * "Open this point in …" — deep links to common public map viewers, so a
 * coordinate can be pivoted across tools (a routine geolocation step). Pure URL
 * building, unit-tested. All targets are public map services; no data leaves
 * the device beyond the coordinate itself when the user taps a link.
 */
data class GeoLink(val label: String, val url: String)

object GeoLinks {
    private fun f(v: Double) = String.format(Locale.US, "%.6f", v)
    private fun f5(v: Double) = String.format(Locale.US, "%.5f", v)

    fun forPoint(lat: Double, lon: Double, zoom: Int = 15): List<GeoLink> {
        val z = zoom.coerceIn(1, 20)
        val la = f(lat); val lo = f(lon)
        return listOf(
            GeoLink("OpenStreetMap", "https://www.openstreetmap.org/?mlat=$la&mlon=$lo#map=$z/${f5(lat)}/${f5(lon)}"),
            GeoLink("Google Maps", "https://www.google.com/maps/search/?api=1&query=$la,$lo"),
            GeoLink("Google Street View", "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=$la,$lo"),
            GeoLink("Google Earth", "https://earth.google.com/web/@$la,$lo,0a,1000d"),
            GeoLink("Bing Maps", "https://www.bing.com/maps?cp=$la~$lo&lvl=$z&style=h"),
            GeoLink("Yandex Maps", "https://yandex.com/maps/?ll=$lo,$la&z=$z&l=sat"),
            GeoLink("OpenTopoMap", "https://opentopomap.org/#map=$z/${f5(lat)}/${f5(lon)}"),
            GeoLink("Windy", "https://www.windy.com/?$la,$lo,$z"),
            GeoLink("SunCalc", "https://www.suncalc.org/#/${f5(lat)},${f5(lon)},$z/"),
        )
    }
}
