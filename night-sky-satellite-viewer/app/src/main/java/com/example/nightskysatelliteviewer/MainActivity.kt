package com.example.nightskysatelliteviewer

import TLEConversion
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.graphics.BitmapFactory
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

    val LAYER_ID = "SAT_LAYER"
    val SOURCE_ID = "SAT_SOURCE"
    val ICON_ID = "SAT_ICON"

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
                Log.d("DEBUG", "DisplayedSatsBuffer: ${displayedSatsBuffer.size}")
                Log.d("DEBUG", "DisplayedSats Before: ${displayedSats.size}")
                displayedSats = displayedSatsBuffer
                Log.d("DEBUG", "DisplayedSats After: ${displayedSats.size}")

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

    fun toggleWaitNotifier(shown: Boolean, displayText: String) {
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
        map.setStyle(Style.Builder().fromUri(Style.MAPBOX_STREETS)
                .withImage(ICON_ID, BitmapFactory.decodeResource(resources, R.drawable.sat))
                .withSource( GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(displayedSats)) )
                .withLayer(SymbolLayer(LAYER_ID, SOURCE_ID)
                        .withProperties(
                                iconImage(ICON_ID),
                                iconSize(0.5F),
                                iconAllowOverlap(false),
                                iconAllowOverlap(false),
                                textField(Expression.get("name")),
                                textRadialOffset(2.0F),
                                textAnchor(Property.TEXT_ANCHOR_BOTTOM),
                                textAllowOverlap(true),
                                textSize(labelsize)
                        )))
        { style ->
            val layer = style.getLayer(LAYER_ID)

//            mapStyle = style
//            val symbolLayer = SymbolLayer("unclustered-points", EARTHQUAKE_SOURCE_ID).withProperties(
//                    iconImage(ICON_ID),
//                    iconSize(0.5f)
//            )
//            style.removeLayer()
//
//            mapStyle.addSource()
//            val symbolManager = SymbolManager(mapView!!, map, style)
//            symbolManager.iconAllowOverlap = funnyDeathBlobToggle
//            symbolManager.textAllowOverlap = funnyDeathBlobToggle
//
//            for (item in displayedSats) {
//                var id = item.id
//                var loc = item.loc
//                var name = item.name
//
//                val symbol = symbolManager.create(SymbolOptions()
//                        .withLatLng(loc)
//                        .withIconImage(ICON_ID)
//                        .withIconSize(.50f))
//                symbol.textField = name
//                symbol.textSize = labelsize
//                symbol.textOffset = PointF(2f, 2f)
//                symbolManager.update(symbol)
//
//                symbolManager.addClickListener { symbol ->
//                    toast { "You clicked the " + symbol.textField + " satellite!" }
//                    true
//
//                }
//            }
//            stateLabelSymbolLayer!!.setProperties(
//                    textIgnorePlacement(true),
//                    textAllowOverlap(true),
//                    textAnchor(Property.TEXT_ANCHOR_BOTTOM))
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