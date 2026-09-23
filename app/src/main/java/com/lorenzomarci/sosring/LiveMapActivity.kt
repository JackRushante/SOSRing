package com.lorenzomarci.sosring

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.net.toUri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.lorenzomarci.sosring.databinding.ActivityLiveMapBinding
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

class LiveMapActivity : BaseActivity() {

    private lateinit var binding: ActivityLiveMapBinding
    private lateinit var database: SosRingDatabase
    private lateinit var sessionId: String
    private lateinit var contactName: String
    private var isLive: Boolean = false
    private var map: MapLibreMap? = null
    private var styleLoaded = false
    private var lastRenderedPointId: Long? = null
    private var hadPoints = false
    private var sessionEnded = false
    private var followEnabled = true
    private var hasCentered = false

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshMap()
            refreshHandler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        binding = ActivityLiveMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = SosRingDatabase.getInstance(this)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME).orEmpty().ifBlank {
            getString(R.string.live_map_title)
        }
        isLive = intent.getBooleanExtra(EXTRA_IS_LIVE, false)

        if (sessionId.isBlank()) {
            Toast.makeText(this, getString(R.string.live_track_no_session), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvContactName.text = contactName
        binding.tvStatus.text = resolveStatusText(LiveMapStateFactory.fromPoints(emptyList(), isLive, System.currentTimeMillis()))
        binding.btnBack.setOnClickListener { finish() }
        binding.btnRecenter.setOnClickListener {
            setFollowEnabled(true)
            recenterOnLatestPoint(database.getPointsForSession(sessionId), force = true)
        }
        binding.btnOpenMaps.setOnClickListener { openInMaps() }

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { loadedMap ->
            map = loadedMap
            loadedMap.uiSettings.isLogoEnabled = false
            loadedMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    setFollowEnabled(false)
                }
            }
            loadedMap.setStyle(Style.Builder().fromJson(OSM_RASTER_STYLE_JSON)) { style ->
                setupLiveLayers(style)
                styleLoaded = true
                refreshMap()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        binding.mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        binding.mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    private fun setupLiveLayers(style: Style) {
        val pathColor = Color.parseColor(PATH_COLOR)
        style.addSource(GeoJsonSource(ACCURACY_SOURCE_ID, emptyFeatureCollection()))
        style.addSource(GeoJsonSource(PATH_SOURCE_ID, emptyFeatureCollection()))
        style.addSource(GeoJsonSource(START_SOURCE_ID, emptyFeatureCollection()))
        style.addSource(GeoJsonSource(LATEST_SOURCE_ID, emptyFeatureCollection()))
        style.addLayer(
            FillLayer(ACCURACY_FILL_LAYER_ID, ACCURACY_SOURCE_ID).withProperties(
                fillColor(pathColor),
                fillOpacity(0.15f)
            )
        )
        style.addLayer(
            LineLayer(ACCURACY_LINE_LAYER_ID, ACCURACY_SOURCE_ID).withProperties(
                lineColor(pathColor),
                lineOpacity(0.45f),
                lineWidth(1f)
            )
        )
        style.addLayer(
            LineLayer(PATH_LAYER_ID, PATH_SOURCE_ID).withProperties(
                lineColor(pathColor),
                lineWidth(5f)
            )
        )
        style.addLayer(
            CircleLayer(START_LAYER_ID, START_SOURCE_ID).withProperties(
                circleRadius(5f),
                circleColor(Color.WHITE),
                circleStrokeColor(pathColor),
                circleStrokeWidth(3f)
            )
        )
        style.addLayer(
            CircleLayer(LATEST_LAYER_ID, LATEST_SOURCE_ID).withProperties(
                circleRadius(8f),
                circleColor(Color.parseColor("#FF6D00")),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(3f)
            )
        )
    }

    private fun refreshMap() {
        if (!styleLoaded || sessionEnded) return
        val now = System.currentTimeMillis()
        // enforcement opportunistico: se la deadline è passata mentre il timer
        // uptime dormiva, è questo refresh a chiudere davvero la sessione
        val engine = Push.liveEngine()
        engine?.heartbeat(now)
        if (isLive && engine?.getLiveSessionId() != sessionId) {
            endSession(now)
            return
        }
        val points = database.getPointsForSession(sessionId)
        if (points.isEmpty() && hadPoints) {
            endSession(now)
            return
        }
        if (points.isNotEmpty()) hadPoints = true
        val state = LiveMapStateFactory.fromPoints(points, isLive, now)
        binding.tvStatus.text = resolveStatusText(state)
        renderStats(points, includeRecent = state.status == LiveMapStatus.LIVE)
        updateSources(points)
        recenterOnLatestPoint(points, force = false)
    }

    private fun endSession(nowMs: Long) {
        sessionEnded = true
        isLive = false
        val points = database.getPointsForSession(sessionId)
        binding.tvStatus.text = resolveStatusText(
            LiveMapStateFactory.fromPoints(points, isLive = false, nowMs = nowMs, sessionEnded = true)
        )
        renderStats(points, includeRecent = false)
        // il percorso già disegnato resta visibile anche a sessione conclusa
        if (points.isNotEmpty()) updateSources(points)
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun renderStats(points: List<LocationPoint>, includeRecent: Boolean) {
        binding.btnOpenMaps.visibility = if (points.isEmpty()) View.GONE else View.VISIBLE
        val stats = LiveTrackStatsPolicy.compute(points, includeRecent)
        if (stats == null) {
            binding.tvStats.visibility = View.GONE
            return
        }
        val duration = formatDuration(stats.durationMs)
        val distance = formatDistance(stats.distanceMeters)
        val average = formatSpeed(stats.averageKmh)
        val recent = stats.recentKmh
        binding.tvStats.text = if (recent != null) {
            getString(R.string.live_map_stats_recent, duration, distance, average, formatSpeed(recent))
        } else {
            getString(R.string.live_map_stats, duration, distance, average)
        }
        binding.tvStats.visibility = View.VISIBLE
    }

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = ((durationMs + 30_000L) / 60_000L).coerceAtLeast(1L)
        return if (totalMinutes < 60L) {
            getString(R.string.live_map_duration_min, totalMinutes.toInt())
        } else {
            getString(R.string.live_map_duration_h, (totalMinutes / 60L).toInt(), (totalMinutes % 60L).toInt())
        }
    }

    private fun formatDistance(meters: Double): String {
        return if (meters < 1_000.0) {
            getString(R.string.live_map_distance_m, meters.toInt())
        } else {
            getString(R.string.live_map_distance_km, meters / 1_000.0)
        }
    }

    private fun formatSpeed(kmh: Double): String = getString(R.string.live_map_speed, kmh)

    private fun updateSources(points: List<LocationPoint>) {
        val currentStyle = map?.style ?: return
        currentStyle.getSourceAs<GeoJsonSource>(PATH_SOURCE_ID)
            ?.setGeoJson(pathFeatureCollection(points))
        currentStyle.getSourceAs<GeoJsonSource>(LATEST_SOURCE_ID)
            ?.setGeoJson(pointFeatureCollection(points.lastOrNull()))
        currentStyle.getSourceAs<GeoJsonSource>(START_SOURCE_ID)
            ?.setGeoJson(pointFeatureCollection(if (points.size >= 2) points.first() else null))
        currentStyle.getSourceAs<GeoJsonSource>(ACCURACY_SOURCE_ID)
            ?.setGeoJson(accuracyFeatureCollection(points.lastOrNull()))
    }

    private fun setFollowEnabled(enabled: Boolean) {
        if (followEnabled == enabled) return
        followEnabled = enabled
        binding.btnRecenter.setBackgroundResource(
            if (enabled) R.drawable.bg_badge_soft else R.drawable.live_map_panel_bg
        )
    }

    private fun recenterOnLatestPoint(points: List<LocationPoint>, force: Boolean) {
        val latest = points.lastOrNull() ?: return
        if (!force && (!followEnabled || latest.id == lastRenderedPointId)) return
        val currentMap = map ?: return
        lastRenderedPointId = latest.id
        val zoom = if (hasCentered) currentMap.cameraPosition.zoom else FIRST_FIX_ZOOM
        hasCentered = true
        currentMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(latest.lat, latest.lon))
                    .zoom(zoom)
                    .build()
            ),
            650
        )
    }

    private fun openInMaps() {
        val uri = LiveMapUriFactory.latestPointUri(database.getPointsForSession(sessionId), contactName)
        if (uri.isEmpty()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.live_map_no_maps_app), Toast.LENGTH_SHORT).show()
        }
    }

    private fun pathFeatureCollection(points: List<LocationPoint>): FeatureCollection {
        if (points.size < 2) return emptyFeatureCollection()
        val linePoints = points.map { Point.fromLngLat(it.lon, it.lat) }
        return FeatureCollection.fromFeature(
            Feature.fromGeometry(LineString.fromLngLats(linePoints))
        )
    }

    private fun pointFeatureCollection(point: LocationPoint?): FeatureCollection {
        if (point == null) return emptyFeatureCollection()
        return FeatureCollection.fromFeature(
            Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat))
        )
    }

    private fun accuracyFeatureCollection(point: LocationPoint?): FeatureCollection {
        if (point == null) return emptyFeatureCollection()
        val ring = AccuracyCircle.ring(point.lat, point.lon, point.accuracy.toDouble())
        if (ring.isEmpty()) return emptyFeatureCollection()
        val vertices = ring.map { (lon, lat) -> Point.fromLngLat(lon, lat) }
        return FeatureCollection.fromFeature(
            Feature.fromGeometry(Polygon.fromLngLats(listOf(vertices)))
        )
    }

    private fun emptyFeatureCollection(): FeatureCollection {
        return FeatureCollection.fromFeatures(emptyArray())
    }

    private fun resolveStatusText(state: LiveMapState): String {
        return when (state.status) {
            LiveMapStatus.WAITING_FIRST -> getString(R.string.live_map_status_waiting)
            LiveMapStatus.NO_POINTS -> getString(R.string.live_map_status_no_points)
            LiveMapStatus.ENDED -> getString(R.string.live_map_status_ended)
            LiveMapStatus.STALLED -> getString(R.string.live_map_status_stalled, state.ageSeconds)
            LiveMapStatus.LIVE -> {
                if (state.ageSeconds <= 5) {
                    getString(R.string.live_map_status_live_now)
                } else {
                    getString(R.string.live_map_status_live_ago, state.ageSeconds)
                }
            }
            LiveMapStatus.HISTORY -> {
                if (state.pointCount == 1) {
                    getString(R.string.live_map_status_history_one)
                } else {
                    getString(R.string.live_map_status_history_many, state.pointCount)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_CONTACT_NAME = "contact_name"
        private const val EXTRA_IS_LIVE = "is_live"
        private const val REFRESH_MS = 2_000L
        private const val FIRST_FIX_ZOOM = 16.0
        private const val PATH_COLOR = "#1565C0"
        private const val PATH_SOURCE_ID = "live-path-source"
        private const val PATH_LAYER_ID = "live-path-layer"
        private const val LATEST_SOURCE_ID = "live-latest-source"
        private const val LATEST_LAYER_ID = "live-latest-layer"
        private const val START_SOURCE_ID = "live-start-source"
        private const val START_LAYER_ID = "live-start-layer"
        private const val ACCURACY_SOURCE_ID = "live-accuracy-source"
        private const val ACCURACY_FILL_LAYER_ID = "live-accuracy-fill-layer"
        private const val ACCURACY_LINE_LAYER_ID = "live-accuracy-line-layer"
        private const val OSM_RASTER_STYLE_JSON = """
            {
              "version": 8,
              "sources": {
                "osm": {
                  "type": "raster",
                  "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                  "tileSize": 256,
                  "attribution": "© OpenStreetMap contributors"
                }
              },
              "layers": [
                {
                  "id": "osm",
                  "type": "raster",
                  "source": "osm"
                }
              ]
            }
        """

        fun intent(
            context: Context,
            sessionId: String,
            contactName: String,
            isLive: Boolean
        ): Intent {
            return Intent(context, LiveMapActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_IS_LIVE, isLive)
            }
        }
    }
}
