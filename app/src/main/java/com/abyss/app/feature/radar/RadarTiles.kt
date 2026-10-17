package com.abyss.app.feature.radar

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * The basemaps the radar can draw. Every source is a 256-logical-pixel XYZ
 * layer so they line up pixel-for-pixel at the same zoom — switching a layer
 * never moves or rescales the view. Two of them (CARTO dark/light) follow the
 * app theme; the rest are fixed. All tiles come from open providers with no
 * API key.
 */
enum class BaseLayer(
    val id: String,
    /** True → the concrete tiles are chosen from the app theme (dark/light). */
    val themed: Boolean = false,
    val maxZoom: Int = 19,
) {
    Abyss("abyss", themed = true, maxZoom = 19),       // CARTO, matches the theme
    Streets("streets", maxZoom = 19),                  // OpenStreetMap standard
    Satellite("satellite", maxZoom = 19),              // Esri World Imagery
    Terrain("terrain", maxZoom = 17),                  // OpenTopoMap (relief + contours)
    Transport("transport", maxZoom = 19);              // CARTO Voyager (detailed streets, transit)

    fun tileSource(dark: Boolean): ITileSource = when (this) {
        Abyss -> carto(if (dark) "dark_all" else "light_all", "AbyssCarto" + if (dark) "Dark" else "Light")
        Transport -> carto("rastertiles/voyager", "AbyssVoyager")
        Streets -> XYTileSource(
            "AbyssOSM", 1, 19, 256, ".png",
            arrayOf("https://a.tile.openstreetmap.org/", "https://b.tile.openstreetmap.org/", "https://c.tile.openstreetmap.org/"),
            "© OpenStreetMap contributors",
        )
        Terrain -> XYTileSource(
            "AbyssTopo", 1, 17, 256, ".png",
            arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/"),
            "© OpenTopoMap (CC-BY-SA) · © OpenStreetMap contributors",
        )
        Satellite -> esriImagery()
    }

    private fun carto(style: String, name: String): XYTileSource = XYTileSource(
        name, 1, maxZoom, 256, "@2x.png",
        arrayOf("a", "b", "c", "d").map { "https://$it.basemaps.cartocdn.com/$style/" }.toTypedArray(),
        "© OpenStreetMap contributors © CARTO",
    )

    /** Esri serves tiles as {z}/{y}/{x}, so it needs its own URL builder. */
    private fun esriImagery(): OnlineTileSourceBase = object : OnlineTileSourceBase(
        "AbyssEsri", 1, 19, 256, "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community",
    ) {
        override fun getTileURLString(index: Long): String =
            baseUrl + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index)
    }
}
