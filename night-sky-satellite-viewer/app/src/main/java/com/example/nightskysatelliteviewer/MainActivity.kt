package com.example.nightskysatelliteviewer

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
//import com.example.nightskysatelliteviewer.filtering.PrefixFilter
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource

class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener {
    // MapBox related attributes
    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap

    private val SAT_NAME = "sat_name"
    private val SAT_ID = "sat_id"
    private val SAT_TLE = "sat_tle"
    private val UNCLUSTERED_LAYER_ID = "SAT_LAYER"
    private val UNCLUSTERED_ICON_ID = "SAT_ICON"
    private val CLUSTERED_ICON_ID = "CLUSTER_ICON"
    private val CLUSTER_LAYERS = arrayOf("CLUSTER_1" to 150, "CLUSTER_2" to 20, "CLUSTER_3" to 0)
    private val CLUSTER_COUNT_LAYER = "COUNT"
    private val CLUSTER_POINT_COUNT = "point_count"
    private val SOURCE_ID = "SAT_SOURCE"

    private val labelsize: Float = 15.0F

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
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
        model.displayedSatellites.observe(this, Observer<ArrayList<Feature>>{ satellites ->
            updateMap(satellites)
        })
    }

    private fun updateMap(satellites: ArrayList<Feature>) {
        val model: NightSkyViewModel by viewModels()
        val unclusteredLayer: SymbolLayer = SymbolLayer(UNCLUSTERED_LAYER_ID, SOURCE_ID)
            .withProperties(
                PropertyFactory.iconImage(UNCLUSTERED_ICON_ID),
                PropertyFactory.iconSize(0.5F),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.textField(Expression.get(SAT_NAME)),
                PropertyFactory.textRadialOffset(2.0F),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textSize(labelsize),
                PropertyFactory.textColor(Color.WHITE)
            )
        val clusteredLayer = SymbolLayer(CLUSTER_COUNT_LAYER, SOURCE_ID)
            .withProperties(
                PropertyFactory.textField(Expression.toString(Expression.get(CLUSTER_POINT_COUNT))),
                PropertyFactory.textSize(12.0F),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP_RIGHT),
                PropertyFactory.textOffset(arrayOf(3f, -2f)),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.textAllowOverlap(true)
            )

        map.setStyle(Style.Builder().fromUri(Style.DARK)
            .withImage(UNCLUSTERED_ICON_ID, BitmapFactory.decodeResource(resources, R.drawable.sat), false)
            .withImage(CLUSTERED_ICON_ID, BitmapFactory.decodeResource(resources, R.drawable.sat_cluster), false)
            .withSource( GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(satellites), GeoJsonOptions()
                .withCluster(true)
                .withClusterMaxZoom(14)
                .withClusterRadius(50))
            )
            .withLayer(unclusteredLayer))
        { style ->
            for (i in CLUSTER_LAYERS.indices) {
                val id = CLUSTER_LAYERS[i].first
                val clusterNum = CLUSTER_LAYERS[i].second

                val layer = SymbolLayer(id, SOURCE_ID)
                    .withProperties(PropertyFactory.iconImage(CLUSTERED_ICON_ID))

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
        Log.d("DEBUG", "successfully created map style and layers")
        map.addOnMapClickListener { point ->
            val pixel: PointF = map.projection?.toScreenLocation(point) ?: PointF(0F, 0F)

            val rect = 20F
            val features: List<Feature> = map.queryRenderedFeatures(RectF(pixel.x -rect,pixel.y -rect,pixel.x +rect,pixel.y +rect), UNCLUSTERED_LAYER_ID)!!

            for (feature in features) {
                if (feature.properties() != null) {
                    val name = feature.properties()!!.get(SAT_NAME)
                    val id = feature.properties()!!.get(SAT_ID)
                    if (name != null && id != null) {
                        Log.d("DEBUG", "FOUND SATELLITE $name WITH ID $id")

                    } else if (feature.properties()!!.get(CLUSTER_POINT_COUNT) != null) {
                        Log.d("DEBUG", "Cluster with ${feature.properties()!!.get(CLUSTER_POINT_COUNT)} satellites found")
                    } else {
                        Log.d("DEBUG", "Non-satellite found")
                    }
                } else {
                    Log.d("DEBUG", "Feature has no properties")
                }
            }
            return@addOnMapClickListener true
        }
        map.addOnMapLongClickListener { point ->
            val pixel: PointF = map.projection?.toScreenLocation(point) ?: PointF(0F, 0F)

            val rect = 20F
            val features: List<Feature> = map.queryRenderedFeatures(RectF(pixel.x -rect,pixel.y -rect,pixel.x +rect,pixel.y +rect), UNCLUSTERED_LAYER_ID)!!

            for (feature in features) {
                if (feature.properties() != null) {
                    val name = feature.properties()!!.get(SAT_NAME)
                    val id = feature.properties()!!.get(SAT_ID)
                    if (name != null && id != null) {
                        showToast("Favorited satellite $name WITH ID $id")
                    } else {
                        Log.d("DEBUG", "Non-satellite found")
                    }
                } else {
                    Log.d("DEBUG", "Feature has no properties")
                }
            }
            return@addOnMapLongClickListener true
        }

        Log.d("DEBUG", "done creating map")
        mapView.refreshDrawableState()
    }

    private fun showToast(displayText: String) {
        val toastLen = Toast.LENGTH_SHORT
        val finishedToast = Toast.makeText(getApplication(), displayText, toastLen)
        finishedToast.show()
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
        Log.d("DEBUG", "MAP IS READY FOOLSSSSSS")
        val model: NightSkyViewModel by viewModels()
        map = mapboxMap
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

            map.locationComponent.apply {
                activateLocationComponent(locationComponentActivationOptions)
                isLocationComponentEnabled = true
                cameraMode = CameraMode.TRACKING
                renderMode = RenderMode.COMPASS

                try {
                    var lastKnownLocation = map.getLocationComponent().getLastKnownLocation()
                    var camPosition = CameraPosition.Builder()
                        .target(LatLng(lastKnownLocation))
                        .zoom(6.0)
                        .tilt(20.0)
                        .build()
                    map.cameraPosition = camPosition
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
            enableLocationComponent(map.style!!)
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}