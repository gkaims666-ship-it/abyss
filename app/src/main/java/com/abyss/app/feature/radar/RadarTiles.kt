package com.abyss.app.feature.radar

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * The basemaps the radar can draw. Every source is a 256-logical-pixel XYZ
 * layer, so they line up pixel-for-pixel at the same zoom — switching a layer
 * never moves or rescales the view. All tiles come from open providers that
 * need no API key.
 *
 * The "Abyss" layer is OpenStreetMap; its dark variant is produced by
 * night-inverting the tiles in the view (see RadarScreen.applyLayer), so it
 * follows the theme without depending on a keyed dark-tile service.
 */
enum class BaseLayer(val id: String, val maxZoom: Int = 19) {
    Abyss("abyss", maxZoom = 19),      // OpenStreetMap (+ night filter when dark)
    Streets("streets", maxZoom = 19),  // OpenStreetMap
    Satellite("satellite", maxZoom = 19), // Esri World Imagery
    Terrain("terrain", maxZoom = 17),  // OpenTopoMap (relief + contours)
    Transport("transport", maxZoom = 18); // ÖPNVKarte (public transport)

    /** True when this layer should be night-inverted in a dark theme. */
    val invertsWhenDark: Boolean get() = this == Abyss

    @Suppress("UNUSED_PARAMETER")
    fun tileSource(dark: Boolean): ITileSource = when (this) {
        Abyss, Streets -> osm()
        Transport -> XYTileSource(
            "AbyssOPNV", 1, 18, 256, ".png",
            arrayOf("https://tileserver.memomaps.de/tilegen/"),
            "© OpenStreetMap contributors · ÖPNVKarte (memomaps.de)",
        )
        Terrain -> XYTileSource(
            "AbyssTopo", 1, 17, 256, ".png",
            arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/"),
            "© OpenTopoMap (CC-BY-SA) · © OpenStreetMap contributors",
        )
        Satellite -> esri("AbyssEsri", "World_Imagery", 19,
            "Esri, Maxar, Earthstar Geographics, and the GIS User Community")
    }

    private fun osm(): XYTileSource = XYTileSource(
        "AbyssOSM", 1, 19, 256, ".png",
        arrayOf("https://a.tile.openstreetmap.org/", "https://b.tile.openstreetmap.org/", "https://c.tile.openstreetmap.org/"),
        "© OpenStreetMap contributors",
    )

    /** Esri tiles are served as {z}/{y}/{x}, so they need their own URL builder. */
    private fun esri(name: String, service: String, max: Int, attribution: String): OnlineTileSourceBase =
        object : OnlineTileSourceBase(
            name, 1, max, 256, "",
            arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/$service/MapServer/tile/"),
            attribution,
        ) {
            override fun getTileURLString(index: Long): String =
                baseUrl + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index)
        }
}
