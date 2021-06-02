package com.example.nightskysatelliteviewer

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.example.nightskysatelliteviewer.filtering.PrefixFilter
import com.mapbox.geojson.Feature
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    // MapBox related attributes
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model: NightSkyViewModel by viewModels()

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        model.setMapView(mapView)

        val menu_button: FloatingActionButton = findViewById(R.id.menubutton)
        menu_button.setOnClickListener {
            var popup = PopupMenu(this, menu_button)
            popup.menuInflater.inflate(R.menu.popup, popup.menu)
            popup.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item: MenuItem? ->

                when (item!!.itemId) {
                    R.id.about -> {
                        //Toast.makeText(this@MainActivity, item.title, Toast.LENGTH_SHORT).show()
                        //TODO: Inflate textview with credits
                        Log.d("DEBUG", "you clicked " + item.title)
                    }
                }
                true
            })
            popup.show()
        }

        // Callback for changes to satellites
        model.getDisplayedSatellites().observe(this, Observer<ArrayList<Feature>>{ satellites ->

        })
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        val model: NightSkyViewModel by viewModels()
        model.setMap(mapboxMap)
        model.updateMap()
    }
}