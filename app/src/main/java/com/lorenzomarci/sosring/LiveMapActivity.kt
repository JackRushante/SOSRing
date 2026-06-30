package com.lorenzomarci.sosring

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lorenzomarci.sosring.databinding.ActivityLiveMapBinding
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class LiveMapActivity : AppCompatActivity() {

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
        binding.btnRecenter.setOnClickListener { recenterOnLatestPoint(force = true) }

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { loadedMap ->
            map = loadedMap
            loadedMap.uiSettings.isLogoEnabled = false
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
        style.addSource(GeoJsonSource(PATH_SOURCE_ID, emptyFeatureCollection()))
        style.addSource(GeoJsonSource(LATEST_SOURCE_ID, emptyFeatureCollection()))
        style.addLayer(
            LineLayer(PATH_LAYER_ID, PATH_SOURCE_ID).withProperties(
                lineColor(Color.parseColor("#1565C0")),
                lineWidth(5f)
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
        val points = database.getPointsForSession(sessionId)
        val now = System.currentTimeMillis()
        if (points.isEmpty() && hadPoints) {
            sessionEnded = true
            binding.tvStatus.text = resolveStatusText(
                LiveMapStateFactory.fromPoints(emptyList(), isLive = false, nowMs = now, sessionEnded = true)
            )
            refreshHandler.removeCallbacks(refreshRunnable)
            return
        }
        if (points.isNotEmpty()) hadPoints = true
        val state = LiveMapStateFactory.fromPoints(points, isLive, now)
        binding.tvStatus.text = resolveStatusText(state)
        updateSources(points)
        recenterOnLatestPoint(force = false)
    }

    private fun updateSources(points: List<LocationPoint>) {
        val currentStyle = map?.style ?: return
        currentStyle.getSourceAs<GeoJsonSource>(PATH_SOURCE_ID)
            ?.setGeoJson(pathFeatureCollection(points))
        currentStyle.getSourceAs<GeoJsonSource>(LATEST_SOURCE_ID)
            ?.setGeoJson(latestFeatureCollection(points.lastOrNull()))
    }

    private fun recenterOnLatestPoint(force: Boolean) {
        val latest = database.getPointsForSession(sessionId).lastOrNull() ?: return
        if (!force && latest.id == lastRenderedPointId) return
        lastRenderedPointId = latest.id
        map?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(latest.lat, latest.lon))
                    .zoom(16.0)
                    .build()
            ),
            650
        )
    }

    private fun pathFeatureCollection(points: List<LocationPoint>): FeatureCollection {
        if (points.size < 2) return emptyFeatureCollection()
        val linePoints = points.map { Point.fromLngLat(it.lon, it.lat) }
        return FeatureCollection.fromFeature(
            Feature.fromGeometry(LineString.fromLngLats(linePoints))
        )
    }

    private fun latestFeatureCollection(point: LocationPoint?): FeatureCollection {
        if (point == null) return emptyFeatureCollection()
        return FeatureCollection.fromFeature(
            Feature.fromGeometry(Point.fromLngLat(point.lon, point.lat))
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
        private const val PATH_SOURCE_ID = "live-path-source"
        private const val PATH_LAYER_ID = "live-path-layer"
        private const val LATEST_SOURCE_ID = "live-latest-source"
        private const val LATEST_LAYER_ID = "live-latest-layer"
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
