package com.example.nightskysatelliteviewer

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.example.nightskysatelliteviewer.filtering.PrefixFilter
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.Feature
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style

class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener {
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
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        val model: NightSkyViewModel by viewModels()

        val menu_button: FloatingActionButton = findViewById(R.id.menubutton)
        menu_button.setOnClickListener {
            var popup = PopupMenu(this, menu_button)
            popup.menuInflater.inflate(R.menu.popup, popup.menu)
            popup.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item: MenuItem? ->

                when (item!!.itemId) {
                    R.id.about -> {
                        //Toast.makeText(this@MainActivity, item.title, Toast.LENGTH_SHORT).show()
                        var popupWindow = PopupWindow(this)
                        var layout: View = layoutInflater.inflate(R.layout.credits, null)
                        popupWindow.contentView = layout
                        popupWindow.isOutsideTouchable = true
                        popupWindow.isFocusable = true
                        popupWindow.showAtLocation(layout, Gravity.CENTER, 0,0)
                    }
                }
                true
            })
            popup.show()
        }

        // Callback for changes to satellites
        model.getDisplayedSatellites().observe(this, Observer<ArrayList<Feature>>{ satellites ->

        })

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


    @SuppressLint("MissingPermission")
    fun enableLocationComponent(loadedMapStyle: Style) {
        val model: NightSkyViewModel by viewModels()

        // Request location permissions if not granted
        Log.d("DEBUG", "Hi")
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            Log.d("DEBUG", "Hello")
            val customLocationComponentOptions = LocationComponentOptions.builder(this)
                .trackingGesturesManagement(true)
                .accuracyColor(ContextCompat.getColor(this, R.color.mapbox_blue))
                .build()
            val locationComponentActivationOptions = LocationComponentActivationOptions.builder(this, loadedMapStyle)
                .locationComponentOptions(customLocationComponentOptions)
                .build()

            model.getMap().locationComponent.apply {
                activateLocationComponent(locationComponentActivationOptions)
                isLocationComponentEnabled = true
                cameraMode = CameraMode.TRACKING
                renderMode = RenderMode.COMPASS

                try {
                    var lastKnownLocation = model.getMap().getLocationComponent().getLastKnownLocation()
                    var camPosition = CameraPosition.Builder()
                        .target(LatLng(lastKnownLocation))
                        .zoom(6.0)
                        .tilt(20.0)
                        .build()
                    model.getMap().cameraPosition = camPosition
                } catch (e: Exception) {
                    Log.d("DEBUG", "Couldn't get GPS location")
                }
            }
        } else {
            val permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }

    override fun onExplanationNeeded(permissionsToExplain: List<String>) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        val model: NightSkyViewModel by viewModels()

        if (granted) {
            Toast.makeText(this, "Please restart the app for changes to take effect", Toast.LENGTH_LONG).show()
            enableLocationComponent(model.getMap().style!!)
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}