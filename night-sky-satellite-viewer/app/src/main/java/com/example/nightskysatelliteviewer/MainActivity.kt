package com.example.nightskysatelliteviewer

import TLEConversion
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import com.mapbox.mapboxsdk.style.layers.Layer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

const val tleUrlText = "http://www.celestrak.com/NORAD/elements/gp.php?GROUP=active&FORMAT=tle"

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private var mapView: MapView? = null
    private lateinit var map: MapboxMap
    private lateinit var map_style: Style
    private var allSats = arrayListOf<DisplaySatellite>()
    private var labelsize: Float = 15.0F
    private var stateLabelSymbolLayer: Layer? = null

    val conversionPipe = Channel<DisplaySatellite>()

    val LAYER_ID = "MAIN"
    val SOURCE_ID = "SAT_DB"
    val ICON_ID = "SAT"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)

        mapView?.getMapAsync(this)

        val tleFileName = "gp.txt"
        val tleConversion = TLEConversion(this, tleFileName, tleUrlText)

        SatelliteManager.onDbUpdateComplete = {
            runOnUiThread(kotlinx.coroutines.Runnable() {
                toggleWaitNotifier(false, "")
                showToast("Done updating database!")
                configureSatellitePositions(tleConversion)
            });
        }

        SatelliteManager.initialize(applicationContext, this)
        SatelliteManager.onDbUpdateStart = {
            runOnUiThread(kotlinx.coroutines.Runnable() {
                toggleWaitNotifier(true, "Updating database...")
            })
        }

        //TODO: Once the real satellites are working in loop above, get rid of these fake ones
        allSats.add(DisplaySatellite("Bellingham", "bham_id", LatLng(48.747789, -122.479255)))
        allSats.add(DisplaySatellite("Japan", "jpn_id", LatLng(35.478780, 137.472501)))

        val refreshButton: FloatingActionButton = findViewById(R.id.reload)
        refreshButton.setOnClickListener {
            if (SatelliteManager.initialized && !SatelliteManager.waiting) {
                configureSatellitePositions(tleConversion)
                mapView!!.refreshDrawableState()
            }
        }
    }

    private fun configureSatellitePositions(tleConversion: TLEConversion) {
        toggleWaitNotifier(true, "Updating satellite positions...")
        tleConversion.initConversionPipeline(conversionPipe)
        SatelliteManager.conversionScope.launch {
            Log.d("BIGDUMB", "Converting?")
            for (sat in conversionPipe) {
                Log.d("BIGDUMB", "HERE HE IS, ${sat.name}")
                allSats.add(sat)
            }

            mapView!!.refreshDrawableState()

            runOnUiThread(kotlinx.coroutines.Runnable() {
                showToast("Done updating positions!")
                toggleWaitNotifier(false, "")
            })
        }
    }

    private fun showToast(displayText: String) {
        val toastLen = Toast.LENGTH_SHORT
        val finishedToast = Toast.makeText(applicationContext, displayText, toastLen)
        finishedToast.show()
    }

    fun toggleWaitNotifier(shown: Boolean, displayText: String) {
        val waitNotifier: LinearLayout = findViewById<LinearLayout>(R.id.waitNotification)
        if (shown) {
            waitNotifier.visibility = View.VISIBLE
            val waitText = findViewById<TextView>(R.id.waitText)
            waitText.text = displayText
        } else {
            waitNotifier.visibility = View.INVISIBLE
        }
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        map = mapboxMap
        mapboxMap.setStyle(Style.Builder().fromUri(Style.MAPBOX_STREETS)
            .withImage(ICON_ID, BitmapFactory.decodeResource(
                this.getResources(), R.drawable.sat)),
            object : Style.OnStyleLoaded {
                override fun onStyleLoaded(@NonNull style: Style) {
                    map_style = style
                    stateLabelSymbolLayer = style.getLayer("state-label")

                    val symbolManager = SymbolManager(mapView!!, mapboxMap, style)
                    symbolManager.setIconAllowOverlap(true)
                    symbolManager.setTextAllowOverlap(true)

                    for (item in allSats) {
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
                    }
                    stateLabelSymbolLayer!!.setProperties(
                        textIgnorePlacement(true),
                        textAllowOverlap(true),
                        textAnchor(Property.TEXT_ANCHOR_BOTTOM))
                }
            })
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