package com.example.nightskysatelliteviewer

import TLEConversion
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.Layer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

const val tleUrlText = "http://www.celestrak.com/NORAD/elements/gp.php?GROUP=active&FORMAT=tle"
const val tleFileName = "gp.txt"

const val funnyDeathBlobToggle = false

class MainActivity : AppCompatActivity(), OnMapReadyCallback, SatelliteUpdateListener {

    // MapBox related attributes
    private var mapView: MapView? = null
    private lateinit var map: MapboxMap
    private lateinit var mapStyle: Style
    private var displayedSats = arrayListOf<Feature>()
    private var labelsize: Float = 15.0F

    val UNCLUSTERED_LAYER_ID = "SAT_LAYER"
    val UNCLUSTERED_ICON_ID = "SAT_ICON"
    val CLUSTERED_ICON_ID = "CLUSTER_ICON"
    val CLUSTER_LAYERS = arrayOf("CLUSTER_1" to 150, "CLUSTER_2" to 20, "CLUSTER_3" to 0)
    val CLUSTER_COUNT_LAYER = "COUNT"
    val CLUSTER_POINT_COUNT = "point_count"
    val SOURCE_ID = "SAT_SOURCE"

    // Satellite data pipeline
    private var conversionScopeOld: CoroutineScope? = null
    private lateinit var tleConversion: TLEConversion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)

        mapView?.getMapAsync(this)

        val satelliteSearch = findViewById<EditText>(R.id.editTextSearch)
        satelliteSearch.addTextChangedListener(SatelliteFilter)
        SatelliteFilter.addSatelliteUpdateListener(this)

        tleConversion = TLEConversion(this.filesDir, tleFileName, tleUrlText)

        SatelliteManager.onDbUpdateComplete = {
            runOnUiThread(kotlinx.coroutines.Runnable() {
                toggleWaitNotifier(false, "")
                showToast("Done updating database!")
            });
            requestSatelliteUpdate()
        }

        SatelliteManager.initialize(applicationContext, this)
        SatelliteManager.onDbUpdateStart = {
            runOnUiThread(kotlinx.coroutines.Runnable() {
                toggleWaitNotifier(true, "Updating database...")
            })
        }

        val refreshButton: FloatingActionButton = findViewById(R.id.reload)
        refreshButton.setOnClickListener {
            if (SatelliteManager.initialized && !SatelliteManager.waiting) {
                requestSatelliteUpdate()
                mapView!!.refreshDrawableState()
            }
        }
    }

    /**
     * Launch both the producer and consumer of satellite data with respect
     *   to current filter settings.
     */
    override fun requestSatelliteUpdate() {
        conversionScopeOld?.cancel()

        val conversionScope = CoroutineScope(Job() + Dispatchers.IO)
        conversionScopeOld = conversionScope
        val conversionPipe = Channel<DisplaySatellite>()

        toggleWaitNotifier(true, "Updating satellite positions...")

        conversionScope.launch {
            tleConversion.initConversionPipelineAsync(conversionPipe, conversionScope)
        }

        conversionScope.launch {
            val displayedSatsPipe = conversionPipe.iterator()
            val displayedSatsBuffer = arrayListOf<Feature>()
            while (isActive && displayedSatsPipe.hasNext()) {
                val sat = displayedSatsPipe.next()

                val feature = Feature.fromGeometry(Point.fromLngLat(sat.loc.longitude, sat.loc.latitude))
                feature.addStringProperty("name", sat.name)

                displayedSatsBuffer.add(feature)
            }
            withContext(NonCancellable) {
                displayedSats = displayedSatsBuffer

                runOnUiThread(kotlinx.coroutines.Runnable() {
                    updateMap()
                    mapView!!.refreshDrawableState()
                    showToast("Done updating positions!")
                    toggleWaitNotifier(false, "")
                })
            }
        }
    }

    private fun showToast(displayText: String) {
        val toastLen = Toast.LENGTH_SHORT
        val finishedToast = Toast.makeText(applicationContext, displayText, toastLen)
        finishedToast.show()
    }

    private fun toggleWaitNotifier(shown: Boolean, displayText: String) {
        runOnUiThread(kotlinx.coroutines.Runnable() {
            val waitNotifier: LinearLayout = findViewById<LinearLayout>(R.id.waitNotification)
            if (shown) {
                waitNotifier.visibility = View.VISIBLE
                val waitText = findViewById<TextView>(R.id.waitText)
                waitText.text = displayText
            } else {
                waitNotifier.visibility = View.INVISIBLE
            }
        })
    }

    private fun updateMap() {

        val unclusteredLayer: SymbolLayer = SymbolLayer(UNCLUSTERED_LAYER_ID, SOURCE_ID)
                .withProperties(
                        iconImage(UNCLUSTERED_ICON_ID),
                        iconSize(0.5F),
                        iconAllowOverlap(false),
                        iconAllowOverlap(false),
                        textField(Expression.get("name")),
                        textRadialOffset(2.0F),
                        textAnchor(Property.TEXT_ANCHOR_BOTTOM),
                        textAllowOverlap(false),
                        textSize(labelsize),
                        textColor(Color.WHITE)
                )
        val clusteredLayer = SymbolLayer(CLUSTER_COUNT_LAYER, SOURCE_ID)
                .withProperties(
                    textField(Expression.toString(Expression.get(CLUSTER_POINT_COUNT))),
                    textSize(12.0F),
                    textColor(Color.WHITE),
                    textAnchor(Property.TEXT_ANCHOR_TOP_RIGHT),
                    textOffset(arrayOf(3f, -2f)),
                    textIgnorePlacement(true),
                    textAllowOverlap(true)
                )

        map.setStyle(Style.Builder().fromUri(Style.DARK)
                .withImage(UNCLUSTERED_ICON_ID, BitmapFactory.decodeResource(resources, R.drawable.sat), false)
                .withImage(CLUSTERED_ICON_ID, BitmapFactory.decodeResource(resources, R.drawable.sat_cluster), false)
                .withSource( GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(displayedSats), GeoJsonOptions()
                        .withCluster(true)
                        .withClusterMaxZoom(14)
                        .withClusterRadius(50)))
                .withLayer(unclusteredLayer))
        { style ->
            for (i in CLUSTER_LAYERS.indices) {
                val id = CLUSTER_LAYERS[i].first
                val clusterNum = CLUSTER_LAYERS[i].second

                val layer = SymbolLayer(id, SOURCE_ID)
                        .withProperties(iconImage(CLUSTERED_ICON_ID))

                val pointCount = Expression.toNumber(Expression.get(CLUSTER_POINT_COUNT))
                if (i == 0) {
                    layer.setFilter(
                            Expression.all(
                            Expression.has(CLUSTER_POINT_COUNT),
                            Expression.gte(pointCount, Expression.literal(clusterNum))
                    ))
                }
                else {
                    val prevClusterNum = CLUSTER_LAYERS[i-1].second
                    layer.setFilter(
                            Expression.all(
                            Expression.has(CLUSTER_POINT_COUNT),
                            Expression.gte(pointCount, Expression.literal(clusterNum)),
                            Expression.lt(pointCount, Expression.literal(prevClusterNum))
                    ))
                }

                style.addLayer(layer)
            }
            style.addLayer(clusteredLayer)
        }
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        map = mapboxMap
        updateMap()
    }

    public override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    public override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    inline fun Context.toast(message: () -> String) {
        Toast.makeText(this, message(), Toast.LENGTH_LONG).show()
    }
}