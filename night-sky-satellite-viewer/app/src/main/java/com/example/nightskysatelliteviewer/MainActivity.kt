package com.example.nightskysatelliteviewer

import TLEConversion
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import com.mapbox.mapboxsdk.style.layers.Layer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
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
    private var displayedSats = arrayListOf<DisplaySatellite>()
    private var labelsize: Float = 15.0F
    private var stateLabelSymbolLayer: Layer? = null

    val LAYER_ID = "MAIN"
    val SOURCE_ID = "SAT_DB"
    val ICON_ID = "SAT"

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
            val displayedSatsBuffer = arrayListOf<DisplaySatellite>()
            while (isActive && displayedSatsPipe.hasNext()) {
                displayedSatsBuffer.add(displayedSatsPipe.next())
            }
            withContext(NonCancellable) {
                displayedSats = displayedSatsBuffer // TODO: Feels gross (but most efficient?)
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
                .withImage(ICON_ID, BitmapFactory.decodeResource(
                        this.getResources(), R.drawable.sat)),
                object : Style.OnStyleLoaded {
                    override fun onStyleLoaded(@NonNull style: Style) {
                        mapStyle = style
                        stateLabelSymbolLayer = style.getLayer("state-label")

                        val symbolManager = SymbolManager(mapView!!, map, style)
                        symbolManager.setIconAllowOverlap(funnyDeathBlobToggle)
                        symbolManager.setTextAllowOverlap(funnyDeathBlobToggle)

                        var i = 0
                        while (i < displayedSats.size) {
                            val item = displayedSats[i]
                            var id = item.id
                            var loc = item.loc
                            var name = item.name

                            val symbol = symbolManager.create(SymbolOptions()
                                    .withLatLng(loc)
                                    .withIconImage(ICON_ID)
                                    .withIconSize(.50f))
                            symbol.textField = name
                            symbol.textSize = labelsize
                            symbol.textOffset = PointF(2f, 2f)
                            symbolManager.update(symbol)

                            symbolManager.addClickListener { symbol ->
                                toast { "You clicked the " + symbol.textField + " satellite!" }
                                true

                            }
                            i += 1
                        }
                        stateLabelSymbolLayer!!.setProperties(
                                textIgnorePlacement(true),
                                textAllowOverlap(true),
                                textAnchor(Property.TEXT_ANCHOR_BOTTOM))
                    }
                })
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