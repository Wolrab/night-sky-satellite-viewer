package com.example.nightskysatelliteviewer

import TLEConversion
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.URL
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PointF
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
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private var mapView: MapView? = null
    private lateinit var map: MapboxMap
    private lateinit var map_style: Style
    private var allSats = arrayListOf<mySatellite>()
    private var labelsize: Float = 15.0F
    private var stateLabelSymbolLayer: Layer? = null

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

        allSats.add(mySatellite("Bellingham", "bham_id", LatLng(48.747789, -122.479255)))
        allSats.add(mySatellite("Japan", "jpn_id", LatLng(35.478780, 137.472501)))

        val refresh_button: FloatingActionButton = findViewById(R.id.reload)
        refresh_button.setOnClickListener {
            mapView!!.refreshDrawableState()
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
                    stateLabelSymbolLayer = style.getLayer("state-label");

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

/***************************************************************8
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO: NO NETWORK SCHENANIGANS ON MAIN THREAD
        // TODO: REMOVE POLICY BELOW WHEN NETWORK ACCESS OUTSIDE OF MAIN THREAD
        //val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        //StrictMode.setThreadPolicy(policy)

        //connorsDumbTestingMethod()

        walkersSillyTestingMethod()
    }

    private fun walkersSillyTestingMethod() {
        SatelliteManager.initialize(applicationContext)
        Log.d("walker", "database created")
    }

    private fun connorsDumbTestingMethod() {
        val url = URL("http://www.celestrak.com/NORAD/elements/gp.php?GROUP=active&FORMAT=tle")
        val file = File(filesDir, "gp.txt")
        file.deleteOnExit()
        file.writeText(url.readText())

        val converter = TLEConversion(file.absolutePath)

        var testSat: String
        testSat = "NAVSTAR 77 (USA 289)"
        testSat = "DELLINGR (RBLE)"
        testSat = "NAVSTAR 56 (USA 180)"
        testSat = "NAVSTAR 58 (USA 190)"
        testSat = "G-SAT"
        testSat = "NAVSTAR 76 (USA 266)"
        Log.d("CONVERSION", "Satellite: $testSat")
        converter.getLatitude(testSat)
        converter.getLongitude(testSat)
    }
}
*/