package com.abyss.app.ui.feature.radar

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
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
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Explore
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
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
import com.abyss.app.feature.radar.BaseLayer
import com.abyss.app.feature.radar.GeoApi
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
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class RadarFilter { All, Military, Emergency, Airborne }
private enum class SearchMode { Planes, Places }

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

    // ------------------------------------------------------------ map

    fun createMap(ctx: Context, palette: AbyssPalette): MapView {
        val cfg = Configuration.getInstance()
        cfg.load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        cfg.userAgentValue = "Abyss/1.0 (Android; com.abyss.app)"
        cfg.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
        cfg.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")

        val mv = MapView(ctx)
        mv.setMultiTouchControls(true)
        mv.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mv.isTilesScaledToDpi = true
        mv.minZoomLevel = 2.5
        mv.maxZoomLevel = 18.0
        mv.isVerticalMapRepetitionEnabled = false
        mv.setScrollableAreaLimitLatitude(85.0, -85.0, 0)
        // Bottom → top: map tiles, weather, wiki pins, measure line, dropped pin,
        // aircraft (topmost so plane taps win), map-events (empty-map taps).
        val events = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(pt: GeoPoint): Boolean {
                if (measuring) { addMeasurePoint(pt); return true }
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

        val ov = PlaneOverlay(ctx.resources.displayMetrics.density)
        ov.onTap = { p -> select(p) }
        ov.onUserGesture = { if (following) following = false }
        mv.overlays.add(ov)
        map = mv; overlay = ov

        val rotation = RotationGestureOverlay(mv)
        rotation.isEnabled = true
        mv.overlays.add(rotation)

        layer = BaseLayer.entries.firstOrNull { it.id == prefs.getString("layer", "abyss") } ?: BaseLayer.Abyss
        dark = null   // force first tile source
        applyLayer()
        applyPalette(palette)

        mv.controller.setZoom(prefs.getFloat("z", 6.5f).toDouble())
        mv.controller.setCenter(GeoPoint(prefs.getFloat("lat", 55.75f).toDouble(), prefs.getFloat("lon", 37.62f).toDouble()))
        mv.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { moved(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { moved(); return false }
        })
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
        val s = selected
        if (following && s != null) {
            s.positionAt(System.currentTimeMillis(), tmp)
            mv.controller.setCenter(GeoPoint(tmp[0], tmp[1]))
        }
        val o = mv.mapOrientation
        if (kotlin.math.abs(o - orientation) > 0.4f) orientation = o
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
            val list = when (f) {
                RadarFilter.All -> RadarApi.around(c.latitude, c.longitude, radius)
                RadarFilter.Airborne -> RadarApi.around(c.latitude, c.longitude, radius).filter { !it.onGround }
                RadarFilter.Military -> RadarApi.military()
                RadarFilter.Emergency -> {
                    val all = LinkedHashMap<String, Plane>()
                    for (sq in listOf("7700", "7600", "7500")) {
                        runCatching { RadarApi.bySquawk(sq) }.getOrNull()?.forEach { all[it.hex] = it }
                    }
                    runCatching { RadarApi.around(c.latitude, c.longitude, radius) }.getOrNull()
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
                    ?: runCatching { RadarApi.byHex(s.hex).firstOrNull() }.getOrNull()
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
    }

    /** UI language tag, set by the screen so background lookups match it. */
    var currentLang: String = "en"


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
        selected = p
        route = null
        if (p == null) following = false
        push()
        val cs = p?.callsign ?: return
        if (routeCache.containsKey(cs)) { route = routeCache[cs]; push(); return }
        scope.launch {
            val r = runCatching { RadarApi.route(cs, p.lat, p.lon) }.getOrNull()
            routeCache[cs] = r
            if (selected?.hex == p.hex) { route = r; push() }
        }
    }

    fun focus(p: Plane) {
        select(p)
        val mv = map ?: return
        p.positionAt(System.currentTimeMillis(), tmp)
        mv.controller.animateTo(GeoPoint(tmp[0], tmp[1]), mv.zoomLevelDouble.coerceAtLeast(7.5), 700L)
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
        map?.let { it.mapOrientation = 0f; orientation = 0f; it.invalidate() }
    }

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

    fun focusPlace(place: Place, lang: String) {
        placeResults = null
        map?.controller?.animateTo(GeoPoint(place.lat, place.lon), 12.0, 800L)
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
        map?.controller?.animateTo(GeoPoint(here[0], here[1]), 9.0, 800L)
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

    BackHandler(enabled = active && (ctl.selected != null || ctl.results != null || ctl.placeResults != null || ctl.pin != null || ctl.sheetOpen || ctl.measuring)) {
        when {
            ctl.sheetOpen -> ctl.sheetOpen = false
            ctl.results != null -> ctl.results = null
            ctl.placeResults != null -> ctl.placeResults = null
            ctl.measuring -> ctl.toggleMeasure()
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
                            if (places) ctl.searchPlaces(query, S.code) else ctl.search(query)
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
                if (sel != null) PlaneCard(sel, ctl.route, ctl.following, ctl.metric, palette, S,
                    onFollow = { ctl.following = !ctl.following },
                    onClose = { ctl.select(null) })
            }
            val showPin = ctl.pin != null && ctl.selected == null
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
        if (!ctl.sheetOpen && ctl.selected == null && ctl.pin == null && !ctl.measuring) {
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
    palette: AbyssPalette, S: Strings, onFollow: () -> Unit, onClose: () -> Unit,
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
                val sub = listOfNotNull(p.operator, p.desc ?: p.type).joinToString(" · ")
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
