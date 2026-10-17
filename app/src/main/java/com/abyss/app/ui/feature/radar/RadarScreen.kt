package com.abyss.app.ui.feature.radar

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.BitmapFactory
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.net.Uri
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.abyss.app.feature.radar.AircraftInfo
import com.abyss.app.feature.radar.MapCamera
import com.abyss.app.feature.radar.MapGestureEngine
import com.abyss.app.feature.radar.MapGestureSink
import com.abyss.app.feature.radar.BaseLayer
import com.abyss.app.feature.radar.FlightInfo
import com.abyss.app.feature.radar.RadarSnapshot
import com.abyss.app.feature.radar.RadarSource
import com.abyss.app.feature.radar.GeoApi
import com.abyss.app.feature.radar.MapEvent
import com.abyss.app.feature.radar.PlanePhoto
import com.abyss.app.feature.radar.SunCalc
import com.abyss.app.feature.radar.parseLatLon
import com.abyss.app.feature.radar.Place
import com.abyss.app.feature.radar.Plane
import com.abyss.app.feature.radar.WikiPlace
import com.abyss.app.feature.radar.RadarApi
import com.abyss.app.feature.radar.Route
import com.abyss.app.feature.radar.distanceNm
import com.abyss.app.ui.components.LocalTabActive
import com.abyss.app.ui.i18n.LocalStrings
import com.abyss.app.ui.i18n.Strings
import com.abyss.app.ui.theme.AbyssFonts
import com.abyss.app.ui.theme.AbyssPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.events.MapEventsReceiver
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class RadarFilter { All, Military, Emergency, Airborne }
private enum class SearchMode { Planes, Places }

/**
 * osmdroid is used purely as a renderer. Its own touch handling (multi-touch
 * controller + scale "pin") could get stuck and freeze panning, so touches
 * never reach it: a Compose layer runs [MapGestureEngine] and drives the
 * camera directly, Google-Maps style.
 */
private class AbyssMapView(ctx: Context) : MapView(ctx) {
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean = false
}

/** Fling glide time constant (s): distance = v·τ. Close to Google Maps' feel. */
private const val FLING_TAU = 0.34
private const val ZOOM_FLING_TAU = 0.16
private const val ZOOM_ANIM_S = 0.28

/** Inverts map tiles for a dark-theme "night" basemap (no keyed dark service). */
private val NIGHT_FILTER = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
    -0.9f, 0f, 0f, 0f, 240f,
    0f, -0.9f, 0f, 0f, 240f,
    0f, 0f, -0.9f, 0f, 240f,
    0f, 0f, 0f, 1f, 0f,
)))

/**
 * Map + live data for the radar. Lives exactly as long as the screen: leaving
 * the tool stops all network polling and frame work.
 */
@Stable
private class RadarController(private val app: Context, private val scope: CoroutineScope) {
    var map: MapView? = null
    var overlay: PlaneOverlay? = null

    var planes by mutableStateOf<List<Plane>>(emptyList())
    var selected by mutableStateOf<Plane?>(null)
    var route by mutableStateOf<Route?>(null)
    var following by mutableStateOf(false)
    var filter by mutableStateOf(RadarFilter.All)
    var loading by mutableStateOf(true)
    var offline by mutableStateOf(false)
    var tooWide by mutableStateOf(false)
    var lastOk by mutableLongStateOf(0L)
    var results by mutableStateOf<List<Plane>?>(null)
    var searching by mutableStateOf(false)
    var metric by mutableStateOf(true)
    var locationDenied by mutableStateOf(false)

    // Map layers + open-data overlays
    var layer by mutableStateOf(BaseLayer.Abyss)
    var weatherOn by mutableStateOf(false)
    var wikiOn by mutableStateOf(false)
    var eventsOn by mutableStateOf(false)
    var terminatorOn by mutableStateOf(false)
    var photo by mutableStateOf<PlanePhoto?>(null)
    var photoBitmap by mutableStateOf<ImageBitmap?>(null)
    var info by mutableStateOf<AircraftInfo?>(null)
    var flight by mutableStateOf<FlightInfo?>(null)
    // Live-position networks (all on by default; choice saved)
    var sources by mutableStateOf(RadarSource.entries.toSet())
    var sourceOk by mutableStateOf<Set<RadarSource>>(emptySet())
    var sourceCounts by mutableStateOf<Map<RadarSource, Int>>(emptyMap())
    var selectedEvent by mutableStateOf<MapEvent?>(null)
    var planesOn by mutableStateOf(true)
    var sheetOpen by mutableStateOf(false)

    // Search mode + place results
    var searchMode by mutableStateOf(SearchMode.Planes)
    var placeResults by mutableStateOf<List<Place>?>(null)

    // Long-press pin + reverse geocode
    var pin by mutableStateOf<DoubleArray?>(null)
    var pinPlace by mutableStateOf<Place?>(null)
    var pinLoading by mutableStateOf(false)

    // Measure tool
    var measuring by mutableStateOf(false)
    var measureTotalNm by mutableStateOf(0.0)
    private val measurePts = ArrayList<GeoPoint>()
    var orientation by mutableStateOf(0f)
    var me by mutableStateOf<DoubleArray?>(null)

    var lastFetch = 0L
    var movedAt = 0L
    var dirty = true
    private var dark: Boolean? = null
    private val trails = HashMap<String, ArrayList<DoubleArray>>()
    private val trailSeen = HashMap<String, Long>()
    private val routeCache = HashMap<String, Route?>()
    private val prefs = app.getSharedPreferences("radar", Context.MODE_PRIVATE)
    private val tmp = DoubleArray(2)
    private var pinFolder: FolderOverlay? = null
    private var wikiFolder: FolderOverlay? = null
    private var measureFolder: FolderOverlay? = null
    private var measureLine: Polyline? = null
    private var weatherOverlay: TilesOverlay? = null
    private var weatherTemplate: String? = null
    private var weatherFetchedAt = 0L
    private var wikiAt = 0L
    private var eventsFolder: FolderOverlay? = null
    private var eventsAt = 0L
    private var terminator: Polygon? = null
    private var terminatorAt = 0L
    private var photoJob: kotlinx.coroutines.Job? = null

    // ------------------------------------------------------------ map

    fun createMap(ctx: Context, palette: AbyssPalette): MapView {
        val cfg = Configuration.getInstance()
        cfg.load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        cfg.userAgentValue = "Abyss/1.0 (Android; com.abyss.app)"
        cfg.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
        cfg.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")

        val mv = AbyssMapView(ctx)
        mv.isFlingEnabled = false
        mv.setMultiTouchControls(false)
        mv.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mv.isTilesScaledToDpi = true
        mv.minZoomLevel = 2.5
        mv.maxZoomLevel = 18.0
        mv.isVerticalMapRepetitionEnabled = false
        // Bottom → top: map tiles, weather, wiki pins, measure line, dropped pin,
        // aircraft (topmost so plane taps win), map-events (empty-map taps).
        val events = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(pt: GeoPoint): Boolean {
                if (measuring) { addMeasurePoint(pt); return true }
                if (selectedEvent != null) { selectedEvent = null; return true }
                if (selected != null) { select(null); return true }
                if (pin != null) { clearPin(); return true }
                return false
            }
            override fun longPressHelper(pt: GeoPoint): Boolean { dropPin(pt.latitude, pt.longitude); return true }
        })
        mv.overlays.add(events)

        wikiFolder = FolderOverlay().also { mv.overlays.add(it) }
        measureFolder = FolderOverlay().also { mv.overlays.add(it) }
        pinFolder = FolderOverlay().also { mv.overlays.add(it) }
        eventsFolder = FolderOverlay().also { mv.overlays.add(it) }

        val ov = PlaneOverlay(ctx.resources.displayMetrics.density)
        ov.onTap = { p -> select(p) }
        ov.onUserGesture = { if (following) following = false }
        mv.overlays.add(ov)
        map = mv; overlay = ov

        layer = BaseLayer.entries.firstOrNull { it.id == prefs.getString("layer", "abyss") } ?: BaseLayer.Abyss
        prefs.getString("sources", null)?.let { saved ->
            val set = saved.split(',').mapNotNull { id -> RadarSource.entries.firstOrNull { it.id == id } }.toSet()
            if (set.isNotEmpty()) sources = set
        }
        dark = null   // force first tile source
        applyLayer()
        applyPalette(palette)

        mv.controller.setZoom(prefs.getFloat("z", 6.5f).toDouble())
        mv.controller.setCenter(GeoPoint(prefs.getFloat("lat", 55.75f).toDouble(), prefs.getFloat("lon", 37.62f).toDouble()))
        mv.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { moved(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { moved(); return false }
        })
        mv.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) syncCam()
        }
        mv.onResume()
        return mv
    }

    fun applyPalette(p: AbyssPalette) {
        val mv = map ?: return
        val ov = overlay ?: return
        if (dark != p.dark) {
            dark = p.dark
            applyLayer()
            val bg = p.background.toArgb()
            mv.setBackgroundColor(bg)
            mv.overlayManager.tilesOverlay.setLoadingBackgroundColor(bg)
            mv.overlayManager.tilesOverlay.setLoadingLineColor(bg)
        }
        ov.lowColor = p.secondary.toArgb()
        ov.highColor = p.primary.toArgb()
        ov.glowColor = p.primary.toArgb()
        ov.selectedColor = if (p.dark) -0x1 else p.onBackground.toArgb()
        ov.labelColor = (p.onBackground.copy(alpha = 0.8f)).toArgb()
        ov.outlineColor = (p.background.copy(alpha = 0.75f)).toArgb()
        ov.emergencyColor = p.error.toArgb()
    }

    fun saveCamera() {
        val mv = map ?: return
        val c = mv.mapCenter
        prefs.edit()
            .putFloat("lat", c.latitude.toFloat())
            .putFloat("lon", c.longitude.toFloat())
            .putFloat("z", mv.zoomLevelDouble.toFloat())
            .apply()
    }

    private fun moved() {
        if (following) return
        movedAt = System.currentTimeMillis()
        dirty = true
    }

    /** Per frame: follow the selected aircraft and repaint (planes glide). */
    fun frame() {
        val mv = map ?: return
        stepCamera(System.nanoTime())
        val s = selected
        if (following && s != null && !touching && fly == null) {
            s.positionAt(System.currentTimeMillis(), tmp)
            cam.set(tmp[0], tmp[1])
            pushCam()
        }
        val o = mv.mapOrientation
        if (kotlin.math.abs(o - orientation) > 0.4f || (o == 0f && orientation != 0f)) orientation = o
        if (terminatorOn && System.currentTimeMillis() - terminatorAt > 60_000L) refreshTerminator()
        mv.invalidate()
    }

    // ------------------------------------------------------------ data

    suspend fun refresh() {
        val mv = map ?: return
        lastFetch = System.currentTimeMillis()
        val bb = mv.boundingBox
        val c = mv.mapCenter
        val halfDiag = distanceNm(bb.latNorth, bb.lonWest, bb.latSouth, bb.lonEast) / 2.0
        val f = filter
        tooWide = halfDiag > 250.0 && (f == RadarFilter.All || f == RadarFilter.Airborne)
        val radius = ceil(halfDiag * 1.08).toInt().coerceIn(5, 250)
        try {
            val en = sources
            suspend fun area(): List<Plane> {
                val snap = RadarApi.around(c.latitude, c.longitude, radius,
                    bb.latNorth, bb.latSouth, bb.lonEast, bb.lonWest, en)
                applySnapshot(snap)
                return snap.planes
            }
            val list = when (f) {
                RadarFilter.All -> area()
                RadarFilter.Airborne -> area().filter { !it.onGround }
                RadarFilter.Military -> RadarApi.military(en)
                RadarFilter.Emergency -> {
                    val all = LinkedHashMap<String, Plane>()
                    for (sq in listOf("7700", "7600", "7500")) {
                        runCatching { RadarApi.bySquawk(sq, en) }.getOrNull()?.forEach { all[it.hex] = it }
                    }
                    runCatching { area() }.getOrNull()
                        ?.filter { it.isEmergency }?.forEach { all[it.hex] = it }
                    all.values.toList()
                }
            }
            record(list)
            planes = list
            offline = false
            lastOk = System.currentTimeMillis()
            selected?.let { s ->
                val upd = list.firstOrNull { it.hex == s.hex }
                    ?: runCatching { RadarApi.byHex(s.hex, sources).firstOrNull() }.getOrNull()
                if (upd != null) { selected = upd; record(listOf(upd)) }
            }
        } catch (e: Exception) {
            offline = true
        }
        loading = false
        push()
        if (wikiOn && System.currentTimeMillis() - wikiAt > 1500) {
            wikiAt = System.currentTimeMillis()
            scope.launch { refreshWiki(currentLang) }
        }
        if (eventsOn && System.currentTimeMillis() - eventsAt > 2000) {
            eventsAt = System.currentTimeMillis()
            scope.launch { refreshEvents(currentLang) }
        }
    }

    /** UI language tag, set by the screen so background lookups match it. */
    var currentLang: String = "en"


    private fun applySnapshot(snap: RadarSnapshot) {
        sourceOk = snap.ok
        sourceCounts = snap.perSource
    }

    /** Turns a live-position network on/off (at least one stays on). */
    fun toggleSource(src: RadarSource) {
        val next = if (src in sources) sources - src else sources + src
        if (next.isEmpty()) return
        sources = next
        prefs.edit().putString("sources", next.joinToString(",") { it.id }).apply()
        dirty = true; lastFetch = 0L
    }

    /** Remembers positions so a selected aircraft shows where it has been. */
    private fun record(list: List<Plane>) {
        val now = System.currentTimeMillis()
        for (p in list) {
            val t = trails.getOrPut(p.hex) { ArrayList() }
            val last = t.lastOrNull()
            if (last == null || distanceNm(last[0], last[1], p.lat, p.lon) > 0.25) {
                t.add(doubleArrayOf(p.lat, p.lon))
                if (t.size > 400) t.removeAt(0)
            }
            trailSeen[p.hex] = now
        }
        if (trails.size > 3000) {
            val stale = trailSeen.filterValues { now - it > 15 * 60_000L }.keys
            stale.forEach { trails.remove(it); trailSeen.remove(it) }
        }
    }

    fun push() {
        val ov = overlay ?: return
        val s = selected
        ov.planes = if (s != null && planes.none { it.hex == s.hex }) planes + s else planes
        ov.selectedHex = s?.hex
        ov.trail = s?.let { trails[it.hex] }?.toList() ?: emptyList()
        ov.route = route
    }

    fun select(p: Plane?) {
        if (p != null) selectedEvent = null
        selected = p
        route = null
        if (p == null) following = false
        photo = null; photoBitmap = null; info = null; flight = null
        photoJob?.cancel()
        if (p != null) {
            // Registry + airline/route (adsbdb) and the photo, in parallel.
            photoJob = scope.launch {
                val db = async { runCatching { RadarApi.adsbdb(p.hex, p.callsign) }.getOrNull() }
                val ph = runCatching { GeoApi.planePhoto(p.hex) }.getOrNull()
                val (ai, fi) = db.await() ?: (null to null)
                if (selected?.hex != p.hex) return@launch
                info = ai; flight = fi
                if (route == null && fi?.route != null) { route = fi.route; push() }
                val chosen = ph ?: ai?.photoThumb?.let { PlanePhoto(it, ai?.photo ?: it, null) }
                photo = chosen
                if (chosen != null) {
                    val bytes = runCatching { GeoApi.fetchBytes(chosen.thumbUrl) }.getOrNull()
                    val bmp = bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
                    if (selected?.hex == p.hex) photoBitmap = bmp?.asImageBitmap()
                }
            }
        }
        push()
        val cs = p?.callsign ?: return
        routeCache[cs]?.let { route = it; push(); return }
        scope.launch {
            val r = runCatching { RadarApi.route(cs, p.lat, p.lon) }.getOrNull() ?: return@launch
            routeCache[cs] = r
            if (selected?.hex == p.hex) { route = r; push() }
        }
    }

    fun focus(p: Plane) {
        select(p)
        val mv = map ?: return
        p.positionAt(System.currentTimeMillis(), tmp)
        flyTo(tmp[0], tmp[1], mv.zoomLevelDouble.coerceAtLeast(7.5), ms = 700L)
    }

    fun setFilterTo(f: RadarFilter) {
        if (filter == f) return
        filter = f
        loading = true
        dirty = true
        lastFetch = 0L
    }

    fun search(q: String) {
        val t = q.trim()
        if (t.isEmpty()) { results = null; return }
        val local = planes.filter {
            it.callsign?.contains(t, true) == true || it.reg?.contains(t, true) == true || it.hex.contains(t, true)
        }
        results = local
        searching = true
        scope.launch {
            val remote = runCatching { RadarApi.search(t) }.getOrDefault(emptyList())
            val merged = LinkedHashMap<String, Plane>()
            local.forEach { merged[it.hex] = it }
            remote.forEach { merged[it.hex] = it }
            results = merged.values.toList()
            searching = false
        }
    }

    // ------------------------------------------------------------ layers & overlays

    private fun applyLayer() {
        val mv = map ?: return
        mv.setTileSource(layer.tileSource(dark ?: false))
        mv.maxZoomLevel = layer.maxZoom.toDouble()
        // Dark theme + a light basemap → invert the tiles into a night map
        // (key-free, and keeps OSM's deep zoom). Other layers keep true colour.
        val night = (dark == true) && layer.invertsWhenDark
        mv.overlayManager.tilesOverlay.setColorFilter(if (night) NIGHT_FILTER else null)
    }

    fun switchLayer(l: BaseLayer) {
        if (layer == l) return
        layer = l
        applyLayer()
        prefs.edit().putString("layer", l.id).apply()
    }

    fun toggleWeather() {
        weatherOn = !weatherOn
        if (weatherOn) scope.launch { refreshWeather() } else removeWeather()
    }

    private suspend fun refreshWeather() {
        val mv = map ?: return
        val now = System.currentTimeMillis()
        if (weatherTemplate == null || now - weatherFetchedAt > 5 * 60_000L) {
            weatherTemplate = runCatching { GeoApi.weatherTileTemplate() }.getOrNull()
            weatherFetchedAt = now
        }
        val tmpl = weatherTemplate ?: run { weatherOn = false; return }
        removeWeather()
        val provider = MapTileProviderBasic(app)
        provider.setTileSource(XYTileSource(
            "rainviewer", 1, 18, 256,
            tmpl.substringAfter("{z}/{x}/{y}"), arrayOf(tmpl.substringBefore("{z}/{x}/{y}")),
        ))
        val ov = TilesOverlay(provider, app)
        ov.loadingBackgroundColor = AndroidColor.TRANSPARENT
        weatherOverlay = ov
        mv.overlays.add(0, ov)
        mv.invalidate()
    }

    private fun removeWeather() {
        val mv = map ?: return
        weatherOverlay?.let { mv.overlays.remove(it); it.onDetach(mv) }
        weatherOverlay = null
        mv.invalidate()
    }

    fun toggleWiki() {
        wikiOn = !wikiOn
        if (wikiOn) { wikiAt = 0L; dirty = true } else { wikiFolder?.items?.clear(); map?.invalidate() }
    }

    private suspend fun refreshWiki(lang: String) {
        val mv = map ?: return
        val c = mv.mapCenter
        val bb = mv.boundingBox
        val radius = (distanceNm(bb.latNorth, bb.lonWest, bb.latSouth, bb.lonEast) * 1852.0 / 2.0)
            .toInt().coerceIn(500, 10000)
        val places = runCatching { GeoApi.wikiNearby(c.latitude, c.longitude, radius, lang) }.getOrNull() ?: return
        val folder = wikiFolder ?: return
        folder.items.clear()
        for (w in places) {
            val m = Marker(mv)
            m.position = GeoPoint(w.lat, w.lon)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            m.title = w.title
            m.subDescription = GeoApi.wikiArticleUrl(w)
            m.setOnMarkerClickListener { mk, _ -> openUrl(mk.subDescription); true }
            folder.add(m)
        }
        mv.invalidate()
    }

    fun toggleEvents() {
        eventsOn = !eventsOn
        if (eventsOn) { eventsAt = 0L; dirty = true } else {
            eventsFolder?.items?.clear(); selectedEvent = null; map?.invalidate()
        }
    }

    private suspend fun refreshEvents(lang: String) {
        val mv = map ?: return
        val bb = mv.boundingBox
        val list = runCatching {
            GeoApi.events(bb.latNorth, bb.latSouth, bb.lonEast, bb.lonWest, lang)
        }.getOrNull() ?: return
        val folder = eventsFolder ?: return
        folder.items.clear()
        for (e in list) {
            val m = Marker(mv)
            m.position = GeoPoint(e.lat, e.lon)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            val sizeDp = if (e.kind == MapEvent.Kind.Quake) (9f + 2.4f * (e.mag ?: 3.0)).toFloat().coerceIn(10f, 30f) else 13f
            m.icon = dot(if (e.kind == MapEvent.Kind.Quake) 0xFFE5484D.toInt() else 0xFFF0A84C.toInt(), sizeDp)
            m.relatedObject = e
            m.setOnMarkerClickListener { mk, _ ->
                select(null); clearPin()
                selectedEvent = mk.relatedObject as? MapEvent
                mk.position?.let { flyTo(it.latitude, it.longitude) }
                true
            }
            folder.add(m)
        }
        mv.invalidate()
    }

    /** A small filled circle used as an event marker. */
    private fun dot(color: Int, sizeDp: Float = 13f): ShapeDrawable {
        val px = (app.resources.displayMetrics.density * sizeDp).toInt()
        return ShapeDrawable(OvalShape()).apply {
            intrinsicWidth = px; intrinsicHeight = px
            paint.color = color
            paint.setShadowLayer(px * 0.25f, 0f, 0f, 0x80000000.toInt())
        }
    }

    fun toggleTerminator() {
        terminatorOn = !terminatorOn
        if (terminatorOn) { terminatorAt = 0L; refreshTerminator() } else removeTerminator()
    }

    private fun refreshTerminator() {
        val mv = map ?: return
        terminatorAt = System.currentTimeMillis()
        val pts = SunCalc.nightPolygon().map { GeoPoint(it[0], it[1]) }
        removeTerminator()
        if (pts.isEmpty()) return
        val poly = Polygon()
        poly.points = pts
        poly.fillPaint.color = 0x40000B1E
        poly.outlinePaint.color = 0x55386A8C
        poly.outlinePaint.strokeWidth = 1.5f
        terminator = poly
        mv.overlays.add(0, poly)
        mv.invalidate()
    }

    private fun removeTerminator() {
        val mv = map ?: return
        terminator?.let { mv.overlays.remove(it) }
        terminator = null
        mv.invalidate()
    }

    fun openUrl(url: String?) {
        url ?: return
        runCatching {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ------------------------------------------------------------ pin

    fun dropPin(lat: Double, lon: Double, lang: String = "en") {
        select(null)
        pin = doubleArrayOf(lat, lon)
        pinPlace = null
        pinLoading = true
        val mv = map ?: return
        val folder = pinFolder ?: return
        folder.items.clear()
        val m = Marker(mv)
        m.position = GeoPoint(lat, lon)
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        folder.add(m)
        mv.invalidate()
        scope.launch {
            val place = runCatching { GeoApi.reverse(lat, lon, lang) }.getOrNull()
            if (pin?.get(0) == lat && pin?.get(1) == lon) { pinPlace = place; pinLoading = false }
        }
    }

    fun clearPin() {
        pin = null; pinPlace = null; pinLoading = false
        pinFolder?.items?.clear(); map?.invalidate()
    }

    // ------------------------------------------------------------ measure

    fun toggleMeasure() {
        measuring = !measuring
        if (!measuring) clearMeasure()
    }

    fun clearMeasure() {
        measurePts.clear(); measureTotalNm = 0.0
        measureFolder?.items?.clear(); measureLine = null; map?.invalidate()
    }

    private fun addMeasurePoint(pt: GeoPoint) {
        val mv = map ?: return
        val folder = measureFolder ?: return
        measurePts.add(pt)
        val dot = Marker(mv)
        dot.position = pt
        dot.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        folder.add(dot)
        if (measurePts.size >= 2) {
            val line = Polyline()
            line.setPoints(ArrayList(measurePts))
            line.outlinePaint.color = AndroidColor.rgb(0xF0, 0xA8, 0x4C)
            line.outlinePaint.strokeWidth = 5f
            measureFolder?.let { f -> measureLine?.let { f.remove(it) } }
            measureLine = line
            folder.add(line)
            var total = 0.0
            for (i in 1 until measurePts.size) {
                total += distanceNm(measurePts[i - 1].latitude, measurePts[i - 1].longitude,
                    measurePts[i].latitude, measurePts[i].longitude)
            }
            measureTotalNm = total
        }
        mv.invalidate()
    }

    fun resetNorth() {
        if (map == null) return
        syncCam()
        flyTo(cam.lat, cam.lon, bearing = 0.0, ms = 380L)
    }

    // ------------------------------------------------------------ camera + gestures

    /** The single source of truth for where the map looks; pushed into osmdroid. */
    val cam = MapCamera(mapSize = { z -> TileSystem.MapSize(z) })
    var touching = false
        private set

    // fling (screen px/s), zoom fling (levels/s), discrete zoom, fly-to
    private var flVx = 0f; private var flVy = 0f
    private var zfV = 0.0; private var zfX = 0f; private var zfY = 0f
    private var zaFrom = 0.0; private var zaTo = 0.0; private var zaX = 0f; private var zaY = 0f
    private var zaStart = 0L; private var zaOn = false
    private class Fly(
        val lat0: Double, val lon0: Double, val z0: Double, val b0: Double,
        val lat1: Double, val dLon: Double, val z1: Double, val dB: Double,
        val arc: Double, val start: Long, val dur: Long,
    )
    private var fly: Fly? = null
    private var lastStep = 0L

    fun syncCam() {
        val mv = map ?: return
        if (mv.width > 0) { cam.width = mv.width.toFloat(); cam.height = mv.height.toFloat() }
        cam.minZoom = mv.minZoomLevel
        cam.maxZoom = mv.maxZoomLevel
        val c = mv.mapCenter
        cam.set(c.latitude, c.longitude, mv.zoomLevelDouble, mv.mapOrientation.toDouble())
    }

    private fun pushCam() {
        val mv = map ?: return
        val b = cam.bearing.toFloat()
        if (mv.mapOrientation != b) mv.setMapOrientation(b, false)
        if (kotlin.math.abs(mv.zoomLevelDouble - cam.zoom) > 1e-9) mv.setZoomLevel(cam.zoom)
        mv.setExpectedCenter(GeoPoint(cam.lat, cam.lon))
        mv.invalidate()
    }

    private fun stopCamAnim() {
        flVx = 0f; flVy = 0f; zfV = 0.0; zaOn = false; fly = null
        map?.controller?.stopAnimation(false)
    }

    /** Smooth camera flight; long hops zoom out a little mid-way, like Google Maps. */
    fun flyTo(lat: Double, lon: Double, zoom: Double? = null, bearing: Double? = null, ms: Long = 700L) {
        val mv = map ?: return
        stopCamAnim()
        syncCam()
        if (mv.width == 0) {   // not laid out yet: jump
            cam.set(lat, lon, zoom ?: cam.zoom, bearing ?: cam.bearing); pushCam(); return
        }
        val z1 = (zoom ?: cam.zoom).coerceIn(cam.minZoom, cam.maxZoom)
        val dLon = MapCamera.angleDelta(cam.lon, lon)
        val s = TileSystem.MapSize(minOf(cam.zoom, z1))
        val dx = dLon / 360.0 * s
        val dy = MapCamera.mercY(lat, s) - MapCamera.mercY(cam.lat, s)
        val screens = kotlin.math.hypot(dx, dy) / maxOf(cam.width, cam.height)
        val arc = if (screens > 1.5) (kotlin.math.ln(screens) / kotlin.math.ln(2.0)).coerceIn(0.0, 3.0) else 0.0
        fly = Fly(cam.lat, cam.lon, cam.zoom, cam.bearing, lat, dLon, z1,
            MapCamera.angleDelta(cam.bearing, bearing ?: cam.bearing), arc,
            System.nanoTime(), (ms + (arc * 120).toLong()) * 1_000_000L)
    }

    private fun stepCamera(now: Long) {
        val dt = if (lastStep == 0L) 0.016 else ((now - lastStep) / 1e9).coerceIn(0.0, 0.05)
        lastStep = now
        var moved = false
        fly?.let { f ->
            val p = ((now - f.start).toDouble() / f.dur).coerceIn(0.0, 1.0)
            val e = if (p < 0.5) 4 * p * p * p else 1 - Math.pow(-2 * p + 2, 3.0) / 2
            val z = f.z0 + (f.z1 - f.z0) * e - f.arc * kotlin.math.sin(Math.PI * e)
            cam.set(f.lat0 + (f.lat1 - f.lat0) * e, f.lon0 + f.dLon * e, z, f.b0 + f.dB * e)
            moved = true
            if (p >= 1.0) fly = null
        }
        if (flVx != 0f || flVy != 0f) {
            val k = kotlin.math.exp(-dt / FLING_TAU)
            val dist = FLING_TAU * (1 - k)
            cam.pan((flVx * dist).toFloat(), (flVy * dist).toFloat())
            flVx = (flVx * k).toFloat(); flVy = (flVy * k).toFloat()
            if (kotlin.math.hypot(flVx, flVy) < 12f) { flVx = 0f; flVy = 0f }
            moved = true
        }
        if (zfV != 0.0) {
            val k = kotlin.math.exp(-dt / ZOOM_FLING_TAU)
            cam.zoomAround(zfV * ZOOM_FLING_TAU * (1 - k), zfX, zfY)
            zfV *= k
            if (kotlin.math.abs(zfV) < 0.05) zfV = 0.0
            moved = true
        }
        if (zaOn) {
            val p = ((now - zaStart) / 1e9 / ZOOM_ANIM_S).coerceIn(0.0, 1.0)
            val e = 1 - (1 - p) * (1 - p) * (1 - p)
            cam.zoomAround(zaFrom + (zaTo - zaFrom) * e - cam.zoom, zaX, zaY)
            if (p >= 1.0) zaOn = false
            moved = true
        }
        if (moved) pushCam()
    }

    /** Taps go to osmdroid overlays exactly as its own detector would send them. */
    private fun dispatchTap(x: Float, y: Float, long: Boolean) {
        val mv = map ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val e = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_UP, x, y, 0)
        if (mv.mapOrientation != 0f) e.transform(mv.projection.invertedScaleRotateCanvasMatrix)
        if (long) mv.overlayManager.onLongPress(e, mv) else mv.overlayManager.onSingleTapConfirmed(e, mv)
        e.recycle()
    }

    val gestures = object : MapGestureSink {
        override fun gestureStart() { touching = true; stopCamAnim(); syncCam() }
        override fun userMoved() { if (following) following = false }
        override fun pan(dx: Float, dy: Float) { cam.pan(dx, dy); pushCam() }
        override fun zoom(dz: Double, fx: Float, fy: Float) { cam.zoomAround(dz, fx, fy); pushCam() }
        override fun rotate(deg: Double, fx: Float, fy: Float) { cam.rotateAround(deg, fx, fy); pushCam() }
        override fun fling(vx: Float, vy: Float) { flVx = vx; flVy = vy; lastStep = 0L }
        override fun zoomFling(vz: Double, fx: Float, fy: Float) { zfV = vz; zfX = fx; zfY = fy; lastStep = 0L }
        override fun animateZoom(dz: Double, fx: Float, fy: Float) {
            syncCam()
            zaFrom = cam.zoom; zaTo = (cam.zoom + dz).coerceIn(cam.minZoom, cam.maxZoom)
            zaX = fx; zaY = fy; zaStart = System.nanoTime(); zaOn = true
        }
        override fun tap(x: Float, y: Float) = dispatchTap(x, y, long = false)
        override fun longPress(x: Float, y: Float) {
            map?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            dispatchTap(x, y, long = true)
        }
    }

    fun touchEnded() { touching = false }

    // ------------------------------------------------------------ place search

    fun searchPlaces(query: String, lang: String) {
        val t = query.trim()
        if (t.isEmpty()) { placeResults = null; return }
        placeResults = emptyList()
        searching = true
        scope.launch {
            placeResults = runCatching { GeoApi.searchPlaces(t, lang) }.getOrDefault(emptyList())
            searching = false
        }
    }

    /** If [q] is a coordinate, jump+pin and return true; else let the caller search. */
    fun goToQuery(q: String, lang: String): Boolean {
        val c = parseLatLon(q) ?: return false
        placeResults = null
        flyTo(c.lat, c.lon, 13.0, ms = 800L)
        dropPin(c.lat, c.lon, lang)
        return true
    }

    fun focusPlace(place: Place, lang: String) {
        placeResults = null
        flyTo(place.lat, place.lon, 12.0, ms = 800L)
        dropPin(place.lat, place.lon, lang)
    }

    // ------------------------------------------------------------ location

    fun locate(): Boolean {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        val fine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return false
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER); add(LocationManager.NETWORK_PROVIDER); add(LocationManager.PASSIVE_PROVIDER)
        }
        val best = providers.mapNotNull { pr ->
            try { lm.getLastKnownLocation(pr) } catch (_: Exception) { null }
        }.maxByOrNull { it.time }
        if (best != null) { showMe(best); return true }
        // No cached fix: ask for a fresh one.
        val pr = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(pr, null, ContextCompat.getMainExecutor(app)) { loc -> if (loc != null) showMe(loc) }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(pr, { loc: Location -> showMe(loc) }, android.os.Looper.getMainLooper())
            }
        } catch (_: Exception) { return false }
        return true
    }

    private fun showMe(loc: Location) {
        val here = doubleArrayOf(loc.latitude, loc.longitude)
        me = here
        overlay?.me = here
        following = false
        flyTo(here[0], here[1], 9.0, ms = 800L)
    }
}

// ============================================================ screen

@Composable
fun RadarScreen(palette: AbyssPalette) {
    val S = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val ctl = remember { RadarController(context.applicationContext, scope) }
    ctl.currentLang = S.code
    val clipboard = LocalClipboardManager.current

    // The map view is created once the tool's slide-in has finished, so opening
    // the radar never drops frames.
    var mapReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(340); mapReady = true }

    DisposableEffect(Unit) {
        onDispose {
            ctl.saveCamera()
            ctl.map?.onPause()
            ctl.map?.onDetach()
        }
    }

    // Both loops pause while the Tools tab is hidden (the tab stays alive).
    val active = LocalTabActive.current
    // Data loop: every 6 s, or soon after the map stops moving.
    LaunchedEffect(mapReady, active) {
        if (!mapReady || !active) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            val settled = ctl.dirty && now - ctl.movedAt > 650 && now - ctl.lastFetch > 1200
            if (settled || now - ctl.lastFetch > 6000) {
                ctl.dirty = false
                ctl.refresh()
            }
            delay(250)
        }
    }
    // Frame loop: aircraft glide at the display's refresh rate.
    LaunchedEffect(mapReady, active) {
        if (!mapReady || !active) return@LaunchedEffect
        while (true) withFrameNanos { ctl.frame() }
    }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            ctl.locationDenied = false
            ctl.locate()
        } else {
            ctl.locationDenied = true
        }
    }

    BackHandler(enabled = active && (ctl.selected != null || ctl.selectedEvent != null || ctl.results != null || ctl.placeResults != null || ctl.pin != null || ctl.sheetOpen || ctl.measuring)) {
        when {
            ctl.sheetOpen -> ctl.sheetOpen = false
            ctl.results != null -> ctl.results = null
            ctl.placeResults != null -> ctl.placeResults = null
            ctl.measuring -> ctl.toggleMeasure()
            ctl.selectedEvent != null -> ctl.selectedEvent = null
            ctl.pin != null -> ctl.clearPin()
            else -> ctl.select(null)
        }
    }

    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(palette.background)) {
        if (mapReady) {
            AndroidView(
                factory = { ctx -> ctl.createMap(ctx, palette) },
                update = { ctl.applyPalette(palette) },
                modifier = Modifier.fillMaxSize(),
            )
            MapGestureLayer(ctl)
        }

        // ---------------------------------------------------- top: search + filters
        var query by remember { mutableStateOf("") }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(palette.surface.copy(alpha = 0.94f))
                    .border(1.dp, palette.outline, RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = palette.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                val places = ctl.searchMode == SearchMode.Places
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(if (places) S.radarPlacesHint else S.radarSearchHint,
                        color = palette.onSurface.copy(alpha = 0.42f), fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; if (it.isBlank()) { ctl.results = null; ctl.placeResults = null } },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.onSurface, fontSize = 15.sp),
                        cursorBrush = SolidColor(palette.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (places) { if (!ctl.goToQuery(query, S.code)) ctl.searchPlaces(query, S.code) }
                            else ctl.search(query)
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Filled.Close, S.close, tint = palette.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp).clip(CircleShape).clickable {
                            query = ""; ctl.results = null; ctl.placeResults = null; focusManager.clearFocus()
                        })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(S.radarSearchPlanes, ctl.searchMode == SearchMode.Planes, palette) {
                    ctl.searchMode = SearchMode.Planes; ctl.placeResults = null
                }
                FilterChip(S.radarSearchPlaces, ctl.searchMode == SearchMode.Places, palette) {
                    ctl.searchMode = SearchMode.Places; ctl.results = null
                }
                Spacer(Modifier.width(4.dp))
                if (ctl.searchMode == SearchMode.Planes) {
                    FilterChip(S.radarFilterAll, ctl.filter == RadarFilter.All, palette) { ctl.setFilterTo(RadarFilter.All) }
                    FilterChip(S.radarFilterAirborne, ctl.filter == RadarFilter.Airborne, palette) { ctl.setFilterTo(RadarFilter.Airborne) }
                    FilterChip(S.radarFilterMilitary, ctl.filter == RadarFilter.Military, palette) { ctl.setFilterTo(RadarFilter.Military) }
                    FilterChip(S.radarFilterEmergency, ctl.filter == RadarFilter.Emergency, palette) { ctl.setFilterTo(RadarFilter.Emergency) }
                }
            }
            val res = ctl.results
            AnimatedVisibility(res != null && ctl.searchMode == SearchMode.Planes, enter = fadeIn() + slideInVertically { -it / 6 }, exit = fadeOut()) {
                SearchResults(res ?: emptyList(), ctl.searching, palette, S) { p ->
                    ctl.results = null; focusManager.clearFocus(); ctl.focus(p)
                }
            }
            val pres = ctl.placeResults
            AnimatedVisibility(pres != null && ctl.searchMode == SearchMode.Places, enter = fadeIn() + slideInVertically { -it / 6 }, exit = fadeOut()) {
                PlaceResults(pres ?: emptyList(), ctl.searching, palette, S) { pl ->
                    focusManager.clearFocus(); ctl.focusPlace(pl, S.code)
                }
            }
        }

        // ---------------------------------------------------- right: controls
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ctl.orientation != 0f) RoundButton(Icons.Filled.Explore, S.radarResetNorth, palette) { ctl.resetNorth() }
            RoundButton(Icons.Filled.Layers, S.radarLayers, palette, active = ctl.sheetOpen) { ctl.sheetOpen = true }
            RoundButton(Icons.Filled.MyLocation, S.radarMyLocation, palette) {
                if (!ctl.locate()) locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            RoundButton(Icons.Filled.Timeline, S.radarMeasure, palette, active = ctl.measuring) { ctl.toggleMeasure() }
            RoundButton(Icons.Filled.Straighten, if (ctl.metric) S.radarUnitsMetric else S.radarUnitsImperial, palette) {
                ctl.metric = !ctl.metric
            }
        }

        // ---------------------------------------------------- bottom: status / details
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
            AnimatedVisibility(ctl.measuring, enter = fadeIn(), exit = fadeOut()) {
                MeasureBar(ctl.measureTotalNm, ctl.metric, palette, S) { ctl.clearMeasure() }
            }
            val sel = ctl.selected
            AnimatedVisibility(sel != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                if (sel != null) PlaneCard(sel, ctl.route, ctl.following, ctl.metric, ctl.photoBitmap, ctl.photo, ctl.info, ctl.flight, palette, S,
                    onOpenPhoto = { ctl.openUrl(ctl.photo?.link) },
                    onFollow = { ctl.following = !ctl.following },
                    onClose = { ctl.select(null) })
            }
            val ev = ctl.selectedEvent
            AnimatedVisibility(ev != null && ctl.selected == null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                if (ev != null) EventCard(ev, palette, S, onOpen = { ctl.openUrl(ev.url) }, onClose = { ctl.selectedEvent = null })
            }
            val showPin = ctl.pin != null && ctl.selected == null && ctl.selectedEvent == null
            AnimatedVisibility(showPin, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                ctl.pin?.let { pt ->
                    PinCard(pt, ctl.pinPlace, ctl.pinLoading, ctl.me, ctl.metric, palette, S,
                        onCopy = { clipboard.setText(AnnotatedString("%.5f, %.5f".format(java.util.Locale.US, pt[0], pt[1]))) },
                        onShare = { ctl.openUrl("https://www.openstreetmap.org/?mlat=%.5f&mlon=%.5f#map=15/%.5f/%.5f".format(java.util.Locale.US, pt[0], pt[1], pt[0], pt[1])) },
                        onClose = { ctl.clearPin() })
                }
            }
            Spacer(Modifier.height(8.dp))
            StatusPill(ctl, palette, S)
            Text(S.radarCredit, color = palette.onSurface.copy(alpha = 0.45f), fontSize = 9.5.sp,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        }

        // Dim scrim + bottom sheet for map layers & overlays.
        AnimatedVisibility(ctl.sheetOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { ctl.sheetOpen = false })
        }
        AnimatedVisibility(
            ctl.sheetOpen, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            LayerSheet(ctl, palette, S)
        }

        // Long-press hint, shown briefly when the map is idle and empty-handed.
        if (!ctl.sheetOpen && ctl.selected == null && ctl.pin == null && ctl.selectedEvent == null && !ctl.measuring) {
            Text(S.radarDropPinHint, color = palette.onSurface.copy(alpha = 0.4f), fontSize = 10.5.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 108.dp))
        }
    }
}

@Composable
private fun LayerSheet(ctl: RadarController, palette: AbyssPalette, S: Strings) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(palette.surface)
            .border(1.dp, palette.outline, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(18.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 34.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp)).background(palette.outline))
        Spacer(Modifier.height(14.dp))
        Text(S.radarLayers, color = palette.onBackground, fontFamily = AbyssFonts.Display, fontSize = 19.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LayerChip(S.radarLayerAbyss, ctl.layer == BaseLayer.Abyss, palette) { ctl.switchLayer(BaseLayer.Abyss) }
            LayerChip(S.radarLayerStreets, ctl.layer == BaseLayer.Streets, palette) { ctl.switchLayer(BaseLayer.Streets) }
            LayerChip(S.radarLayerSatellite, ctl.layer == BaseLayer.Satellite, palette) { ctl.switchLayer(BaseLayer.Satellite) }
            LayerChip(S.radarLayerTerrain, ctl.layer == BaseLayer.Terrain, palette) { ctl.switchLayer(BaseLayer.Terrain) }
            LayerChip(S.radarLayerTransport, ctl.layer == BaseLayer.Transport, palette) { ctl.switchLayer(BaseLayer.Transport) }
        }
        Spacer(Modifier.height(18.dp))
        Text(S.radarOverlays, color = palette.onBackground, fontFamily = AbyssFonts.Display, fontSize = 19.sp)
        Spacer(Modifier.height(10.dp))
        ToggleRow(Icons.Filled.Flight, S.radarOverlayPlanes, ctl.planesOn, palette) {
            ctl.planesOn = !ctl.planesOn; ctl.overlay?.isEnabled = ctl.planesOn; ctl.map?.invalidate()
        }
        ToggleRow(Icons.Filled.MenuBook, S.radarOverlayWiki, ctl.wikiOn, palette) { ctl.toggleWiki() }
        ToggleRow(Icons.Filled.Bolt, S.radarOverlayEvents, ctl.eventsOn, palette) { ctl.toggleEvents() }
        ToggleRow(Icons.Filled.DarkMode, S.radarOverlayTerminator, ctl.terminatorOn, palette) { ctl.toggleTerminator() }
        Spacer(Modifier.height(18.dp))
        Text(S.radarSources, color = palette.onBackground, fontFamily = AbyssFonts.Display, fontSize = 19.sp)
        Spacer(Modifier.height(6.dp))
        for (src in RadarSource.entries) {
            val on = src in ctl.sources
            val n = ctl.sourceCounts[src]
            val okNow = src in ctl.sourceOk
            val label = if (on && n != null) "${src.label} · $n" else src.label
            ToggleRow(if (on && okNow) Icons.Filled.GpsFixed else Icons.Filled.Flight, label, on, palette) {
                ctl.toggleSource(src)
            }
        }
        ToggleRow(Icons.Filled.Umbrella, S.radarOverlayWeather, ctl.weatherOn, palette) { ctl.toggleWeather() }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun LayerChip(label: String, selected: Boolean, palette: AbyssPalette, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.primary else palette.surfaceVariant)
            .border(1.dp, if (selected) palette.primary else palette.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (selected) palette.onPrimary else palette.onSurface, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, label: String, on: Boolean, palette: AbyssPalette, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) palette.primary else palette.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = palette.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(width = 42.dp, height = 24.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) palette.primary else palette.outline)) {
            Box(Modifier.size(18.dp).padding(0.dp).align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 3.dp).clip(CircleShape).background(palette.onPrimary))
        }
    }
}

@Composable
private fun PlaceResults(list: List<Place>, searching: Boolean, palette: AbyssPalette, S: Strings, onPick: (Place) -> Unit) {
    Column(
        Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = 320.dp)
            .clip(RoundedCornerShape(18.dp)).background(palette.surface.copy(alpha = 0.97f))
            .border(1.dp, palette.outline, RoundedCornerShape(18.dp)),
    ) {
        if (list.isEmpty()) {
            Text(if (searching) S.radarSearching else S.radarNoResults, color = palette.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(list) { pl ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(pl) }.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Place, null, tint = palette.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, color = palette.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (pl.detail != null) Text(pl.detail, color = palette.onSurface.copy(alpha = 0.55f),
                                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasureBar(totalNm: Double, metric: Boolean, palette: AbyssPalette, S: Strings, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface.copy(alpha = 0.95f))
            .border(1.dp, palette.outline, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Timeline, null, tint = palette.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (totalNm > 0) S.radarTotal(dist(totalNm, metric, S)) else S.radarMeasureHint,
                color = palette.onSurface, fontSize = 13.sp)
        }
        Text(S.radarClear, color = palette.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClear).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun PinCard(
    pt: DoubleArray, place: Place?, loading: Boolean, me: DoubleArray?, metric: Boolean,
    palette: AbyssPalette, S: Strings, onCopy: () -> Unit, onShare: () -> Unit, onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(palette.surface.copy(alpha = 0.97f))
            .border(1.dp, palette.outline, RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, null, tint = palette.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(place?.name ?: (if (loading) S.radarSearching else S.radarPoint),
                    color = palette.onBackground, fontFamily = AbyssFonts.Display, fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (place?.detail != null) Text(place.detail, color = palette.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, S.close, tint = palette.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("${S.radarCoordinates}:  %.5f, %.5f".format(java.util.Locale.US, pt[0], pt[1]),
            color = palette.onSurface, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        if (me != null) {
            Spacer(Modifier.height(4.dp))
            Text(S.radarFromMe(dist(distanceNm(me[0], me[1], pt[0], pt[1]), metric, S)),
                color = palette.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PinAction(S.copy, palette, Modifier.weight(1f), onCopy)
            PinAction(S.radarShare, palette, Modifier.weight(1f), onShare)
        }
    }
}

@Composable
private fun PinAction(label: String, palette: AbyssPalette, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(palette.surfaceVariant).clickable(onClick = onClick)
        .padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
        Text(label, color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ============================================================ pieces

@Composable
private fun FilterChip(label: String, selected: Boolean, palette: AbyssPalette, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) palette.primary else palette.surface.copy(alpha = 0.92f))
            .border(1.dp, if (selected) palette.primary else palette.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (selected) palette.onPrimary else palette.onSurface, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, desc: String, palette: AbyssPalette, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape)
            .background(if (active) palette.primary else palette.surface.copy(alpha = 0.94f))
            .border(1.dp, if (active) palette.primary else palette.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (active) palette.onPrimary else palette.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(21.dp)) }
}

@Composable
private fun StatusPill(ctl: RadarController, palette: AbyssPalette, S: Strings) {
    // Ticks once a second so "updated N s ago" stays honest.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val text = when {
        ctl.loading && ctl.planes.isEmpty() -> S.radarLoading
        ctl.offline && ctl.planes.isEmpty() -> S.radarOffline
        else -> buildString {
            append(S.radarCount(ctl.planes.size))
            if (ctl.sourceOk.isNotEmpty()) { append(" · "); append(S.radarSourcesActive(ctl.sourceOk.size, ctl.sources.size)) }
            if (ctl.lastOk > 0) { append(" · "); append(S.radarUpdatedAgo(((now - ctl.lastOk) / 1000).toInt().coerceAtLeast(0))) }
            if (ctl.tooWide) { append(" · "); append(S.radarZoomIn) }
            if (ctl.locationDenied) { append(" · "); append(S.radarLocationDenied) }
        }
    }
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(palette.surface.copy(alpha = 0.9f))
            .border(1.dp, palette.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(
            if (ctl.offline) palette.error else if (ctl.loading) palette.secondary else Color(0xFF5DBB7B)))
        Spacer(Modifier.width(8.dp))
        Text(text, color = palette.onSurface.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchResults(list: List<Plane>, searching: Boolean, palette: AbyssPalette, S: Strings, onPick: (Plane) -> Unit) {
    Column(
        Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = 320.dp)
            .clip(RoundedCornerShape(18.dp)).background(palette.surface.copy(alpha = 0.97f))
            .border(1.dp, palette.outline, RoundedCornerShape(18.dp)),
    ) {
        if (list.isEmpty()) {
            Text(if (searching) S.radarSearching else S.radarNoResults, color = palette.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(list, key = { it.hex }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(p) }.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Flight, null, tint = palette.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.callsign ?: p.reg ?: p.hex.uppercase(), color = palette.onSurface,
                                fontFamily = AbyssFonts.Code, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(listOfNotNull(p.reg, p.type, p.operator).joinToString(" · "),
                                color = palette.onSurface.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (searching) item {
                    Text(S.radarSearching, color = palette.onSurface.copy(alpha = 0.5f), fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaneCard(
    p: Plane, route: Route?, following: Boolean, metric: Boolean,
    photoBitmap: ImageBitmap?, photo: PlanePhoto?, info: AircraftInfo?, flight: FlightInfo?,
    palette: AbyssPalette, S: Strings, onOpenPhoto: () -> Unit, onFollow: () -> Unit, onClose: () -> Unit,
) {
    // Re-evaluated every second so the live position / progress move.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.hex) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val pos = remember { DoubleArray(2) }
    p.positionAt(now, pos)
    val loc = S.locale

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(palette.surface.copy(alpha = 0.97f))
            .border(1.dp, palette.outline, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        if (photoBitmap != null) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenPhoto)) {
                Image(photoBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (photo?.photographer != null) Text(
                    "© ${photo.photographer} · Planespotters",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.callsign ?: p.reg ?: p.hex.uppercase(), color = palette.onBackground,
                        fontFamily = AbyssFonts.Display, fontSize = 24.sp)
                    if (p.military) {
                        Spacer(Modifier.width(8.dp))
                        Tag(S.radarMilitaryTag, Color(0xFF8FBF6A), palette)
                    }
                }
                val model = listOfNotNull(info?.manufacturer, info?.type).joinToString(" ").takeIf { it.isNotBlank() }
                val sub = listOfNotNull(flight?.airline ?: p.operator, model ?: p.desc ?: p.type).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = palette.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, S.close, tint = palette.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }

        // Emergency banner
        val emergency = when (p.squawk) {
            "7500" -> S.radarSq7500
            "7600" -> S.radarSq7600
            "7700" -> S.radarSq7700
            else -> if (p.isEmergency) S.radarEmergencyGeneric else null
        }
        if (emergency != null) {
            Spacer(Modifier.height(10.dp))
            Text("⚠ $emergency", color = palette.error, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(palette.error.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp))
        }

        // Route + progress
        Spacer(Modifier.height(12.dp))
        val o = route?.origin
        val d = route?.destination
        if (o != null || d != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirportLabel(o?.iata ?: o?.icao ?: "—", o?.city ?: o?.name, palette, Modifier.weight(1f), Alignment.Start)
                Icon(Icons.Filled.Flight, null, tint = palette.primary,
                    modifier = Modifier.size(20.dp).rotate(90f))
                AirportLabel(d?.iata ?: d?.icao ?: "—", d?.city ?: d?.name, palette, Modifier.weight(1f), Alignment.End)
            }
            if (o != null && d != null) {
                val flown = distanceNm(o.lat, o.lon, pos[0], pos[1])
                val left = distanceNm(pos[0], pos[1], d.lat, d.lon)
                val frac = (flown / (flown + left).coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(palette.outline)) {
                    Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(2.dp)).background(palette.primary))
                }
                Spacer(Modifier.height(6.dp))
                Text(S.radarProgress(dist(flown, metric, S), dist(left, metric, S)),
                    color = palette.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        } else {
            Text(S.radarRouteUnknown, color = palette.onSurface.copy(alpha = 0.5f), fontSize = 13.sp)
        }

        // Stats grid
        Spacer(Modifier.height(14.dp))
        val seen = ((now - p.fetchedAt) / 1000.0 + p.seenPos).roundToInt()
        val cells = listOf(
            S.radarAltitude to altitude(p, metric, S),
            S.radarSpeed to speed(p.gsKt, metric, S),
            S.radarVSpeed to vspeed(p.vrateFpm, metric, S),
            S.radarHeading to (p.track?.let { "%03d°".format(loc, it.roundToInt() % 360) } ?: "—"),
            S.radarSquawk to (p.squawk ?: "—"),
            S.radarType to (p.type ?: "—"),
            S.radarReg to (p.reg ?: "—"),
            S.radarHex to p.hex.uppercase(),
            S.radarSeen to "$seen ${S.unitSec}",
        )
        cells.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                row.forEach { (k, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(k, color = palette.onSurface.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(v, color = palette.onSurface, fontSize = 15.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
            }
        }

        // Registry + provenance
        val owner = info?.owner
        val country = info?.ownerCountry ?: p.country
        if (owner != null || country != null) {
            Spacer(Modifier.height(6.dp))
            Text("${S.radarOwner}: " + listOfNotNull(owner, country).joinToString(" · "),
                color = palette.onSurface.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (p.sources.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("${S.radarSeenBy}: " + p.sources.sorted().joinToString(" · "),
                color = palette.onSurface.copy(alpha = 0.45f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // Follow
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (following) palette.primary else palette.surfaceVariant)
                .clickable(onClick = onFollow).padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.GpsFixed, null, tint = if (following) palette.onPrimary else palette.onSurface,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (following) S.radarFollowing else S.radarFollow,
                color = if (following) palette.onPrimary else palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AirportLabel(code: String, city: String?, palette: AbyssPalette, modifier: Modifier, align: Alignment.Horizontal) {
    Column(modifier, horizontalAlignment = align) {
        Text(code, color = palette.onBackground, fontFamily = AbyssFonts.Code, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        if (city != null) Text(city, color = palette.onSurface.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Tag(text: String, color: Color, palette: AbyssPalette) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp))
}

// ============================================================ formatting

private fun altitude(p: Plane, metric: Boolean, S: Strings): String {
    if (p.onGround) return S.radarOnGround
    val ft = p.altFt ?: return "—"
    return if (metric) "%,d %s".format(S.locale, (ft * 0.3048).roundToInt(), S.unitM)
    else "%,d %s".format(S.locale, ft, S.unitFt)
}

private fun speed(kt: Double?, metric: Boolean, S: Strings): String {
    kt ?: return "—"
    return if (metric) "%d %s".format(S.locale, (kt * 1.852).roundToInt(), S.unitKmh)
    else "%d %s".format(S.locale, kt.roundToInt(), S.unitKt)
}

private fun vspeed(fpm: Int?, metric: Boolean, S: Strings): String {
    fpm ?: return "—"
    return if (metric) "%+.1f %s".format(S.locale, fpm * 0.00508, S.unitMs)
    else "%+,d %s".format(S.locale, fpm, S.unitFpm)
}

private fun dist(nm: Double, metric: Boolean, S: Strings): String =
    if (metric) "%,d %s".format(S.locale, (nm * 1.852).roundToInt(), S.unitKm)
    else "%,d %s".format(S.locale, nm.roundToInt(), S.unitNm)

@Composable
private fun EventCard(e: MapEvent, palette: AbyssPalette, S: Strings, onOpen: () -> Unit, onClose: () -> Unit) {
    val quake = e.kind == MapEvent.Kind.Quake
    val tagColor = if (quake) palette.error else Color(0xFFF0A84C)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(palette.surface.copy(alpha = 0.97f))
            .border(1.dp, palette.outline, RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, null, tint = tagColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Tag(if (quake) S.radarEventQuake else S.radarEventNews, tagColor, palette)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, S.close, tint = palette.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(e.title, color = palette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (!e.description.isNullOrBlank() && e.description != e.title) {
            Spacer(Modifier.height(4.dp))
            Text(e.description, color = palette.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (e.url != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.primary)
                    .clickable(onClick = onOpen).padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MenuBook, null, tint = palette.onPrimary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(S.radarOpenArticle, color = palette.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Transparent layer over the map that turns raw pointers into camera moves via
 * [MapGestureEngine]: drag + fling, pinch-zoom + rotate together, double tap,
 * double-tap-drag, two-finger tap, long press, mouse wheel. UI drawn after it
 * (buttons, cards, sheets) sits above and keeps its own touches.
 */
@Composable
private fun MapGestureLayer(ctl: RadarController) {
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxSize().pointerInput(ctl) {
        val eng = MapGestureEngine(density, ctl.gestures)
        val mid = LongArray(10)
        val mx = FloatArray(10)
        val my = FloatArray(10)
        awaitPointerEventScope {
            while (true) {
                val dl = eng.nextDeadline()
                val ev: PointerEvent? = if (dl == Long.MAX_VALUE) awaitPointerEvent() else {
                    val wait = dl - android.os.SystemClock.uptimeMillis()
                    if (wait <= 0L) null else withTimeoutOrNull(wait) { awaitPointerEvent() }
                }
                if (ev == null) { eng.tick(android.os.SystemClock.uptimeMillis()); continue }
                if (ev.type == PointerEventType.Scroll) {
                    val c = ev.changes.first()
                    val dy = c.scrollDelta.y
                    if (dy != 0f) {
                        ctl.gestures.gestureStart()
                        ctl.gestures.animateZoom(if (dy < 0f) 1.0 else -1.0, c.position.x, c.position.y)
                        ctl.touchEnded()
                    }
                    ev.changes.forEach { it.consume() }
                    continue
                }
                var t = 0L
                for (c in ev.changes) {
                    if (c.uptimeMillis > t) t = c.uptimeMillis
                    if (c.pressed && !c.previousPressed) eng.onDown(c.id.value, c.position.x, c.position.y, c.uptimeMillis)
                }
                var k = 0
                for (c in ev.changes) {
                    if (c.pressed && c.previousPressed && k < mid.size) {
                        mid[k] = c.id.value; mx[k] = c.position.x; my[k] = c.position.y; k++
                    }
                }
                if (k > 0) eng.onMove(t, mid, mx, my, k)
                for (c in ev.changes) {
                    if (!c.pressed && c.previousPressed) eng.onUp(c.id.value, c.position.x, c.position.y, c.uptimeMillis)
                }
                ev.changes.forEach { it.consume() }
                if (!eng.isActive) ctl.touchEnded()
                eng.tick(t)
            }
        }
    })
}
