package com.github.wolrab.nightskysatelliteviewer

import android.annotation.SuppressLint
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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
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
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener {
    // MapBox related attributes
    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap
    private lateinit var mapStyle: Style

    private val mapLock = Mutex(true)
    private val autoUpdateWaitTime = 6000L
    private val labelSize: Float = 15.0F
    private var onMapInitialized: (()->Unit)? = null
    private var preserveData = true // Instance variable that takes advantage of MainActivity/ViewModel coupling
    private val initializeScope = CoroutineScope(Job() + Dispatchers.IO)
    private val updateScope = CoroutineScope(Job() + Dispatchers.IO)

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

        var favoritesFilter = model.getFilter(getString(R.string.filter_favorites)) as FavoritesFilter?
        if (favoritesFilter == null) {
            favoritesFilter = FavoritesFilter()
            model.addFilter(getString(R.string.filter_favorites), favoritesFilter)
        }

        var searchFilter = model.getFilter(getString(R.string.filter_search)) as SearchFilter?
        if (searchFilter == null) {
            searchFilter = SearchFilter()
            model.addFilter(getString(R.string.filter_search), searchFilter)
        }

        onMapInitialized = {
            updateMap(model.getDisplayedSatellites())
        }

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
                    R.id.favorites_filter -> {
                        favoritesFilter.enabled = !favoritesFilter.enabled
                    }
                }
                true
            })
            popup.show()
        }

        val searchBar: EditText = findViewById(R.id.editTextSearch)
        searchBar.doOnTextChanged { text, _, _, _ ->
            searchFilter.cmp = text.toString()
        }

        startAutoUpdates()
    }

    private fun updateMap(satellites: MutableList<Feature>) {
        val model: NightSkyViewModel by viewModels()

        if (!preserveData) {
            model.clearBufferedSatellites()
        }
        else {
            preserveData = false
        }


        updateScope.launch {
            // Block until mapLock aquired
            initializeScope.async { mapLock.lock() }.await()

            // Begin MASSIVE Layer Creation Logic
            // TODO: cache these expensive objects and options
            runOnUiThread {
                val unclusteredLayer: SymbolLayer = SymbolLayer(getString(R.string.unclustered_layer_id), getString(R.string.source_id))
                        .withProperties(
                                PropertyFactory.iconImage(getString(R.string.unclustered_icon_id)),
                                PropertyFactory.iconSize(0.5F),
                                PropertyFactory.iconAllowOverlap(false),
                                PropertyFactory.iconAllowOverlap(false),
                                PropertyFactory.textField(Expression.get(getString(R.string.feature_name))),
                                PropertyFactory.textRadialOffset(2.0F),
                                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM),
                                PropertyFactory.textAllowOverlap(false),
                                PropertyFactory.textSize(labelSize),
                                PropertyFactory.textColor(Color.WHITE)
                        )
                val clusteredLayer = SymbolLayer(getString(R.string.cluster_count_layer_id), getString(R.string.source_id))
                        .withProperties(
                                PropertyFactory.textField(Expression.toString(Expression.get(getString(R.string.cluster_point_count)))),
                                PropertyFactory.textSize(12.0F),
                                PropertyFactory.textColor(Color.WHITE),
                                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP_RIGHT),
                                PropertyFactory.textOffset(arrayOf(3f, -2f)),
                                PropertyFactory.textIgnorePlacement(true),
                                PropertyFactory.textAllowOverlap(true)
                        )

                map.setStyle(
                        Style.Builder().fromUri(Style.DARK)
                                .withImage(
                                        getString(R.string.unclustered_icon_id),
                                        BitmapFactory.decodeResource(resources, R.drawable.sat),
                                        false
                                )
                                .withImage(
                                        getString(R.string.clustered_icon_id),
                                        BitmapFactory.decodeResource(resources, R.drawable.sat_cluster),
                                        false
                                )
                                .withSource(
                                        GeoJsonSource(
                                                getString(R.string.source_id), FeatureCollection.fromFeatures(satellites), GeoJsonOptions()
                                                .withCluster(true)
                                                .withClusterMaxZoom(14)
                                                .withClusterRadius(50)
                                        )
                                )
                                .withLayer(unclusteredLayer)
                )
                { style ->
                    val clusteredLayers = getStringIntMap(resources.getStringArray(R.array.clustered_layers_id))
                    var prev: String? = null
                    for (entry in clusteredLayers.entries) {
                        val id = entry.key
                        val clusterNum = entry.value

                        val layer = SymbolLayer(id, getString(R.string.source_id))
                                .withProperties(PropertyFactory.iconImage(getString(R.string.clustered_icon_id)))

                        val pointCount = Expression.toNumber(Expression.get(getString(R.string.cluster_point_count)))
                        if (prev == null) {
                            layer.setFilter(
                                    Expression.all(
                                            Expression.has(getString(R.string.cluster_point_count)),
                                            Expression.gte(pointCount, Expression.literal(clusterNum))
                                    )
                            )
                        } else {
                            val prevClusterNum = clusteredLayers[prev]
                            layer.setFilter(
                                    Expression.all(
                                            Expression.has(getString(R.string.cluster_point_count)),
                                            Expression.gte(pointCount, Expression.literal(clusterNum)),
                                            Expression.lt(pointCount, Expression.literal(prevClusterNum!!))
                                    )
                            )
                        }
                        style.addLayer(layer)
                        if (!nyoomCompleted) {
                            enableLocationComponent(style)
                            nyoomCompleted = true
                        }
                    }
                    style.addLayer(clusteredLayer)
                }
//            Log.d("DEBUG", "successfully created map style and layers")
                map.addOnMapClickListener { point ->
                    val pixel: PointF = map.projection?.toScreenLocation(point)
                            ?: PointF(0F, 0F)

                    val rect = 20F
                    val features: List<Feature> = map.queryRenderedFeatures(
                            RectF(
                                    pixel.x - rect,
                                    pixel.y - rect,
                                    pixel.x + rect,
                                    pixel.y + rect
                            ), getString(R.string.unclustered_layer_id)
                    )!!

                    for (feature in features) {
                        if (feature.properties() != null) {
                            val name = feature.properties()!!.get(getString(R.string.feature_name))
                            val id = feature.properties()!!.get(getString(R.string.feature_id))
                            if (name != null && id != null) {
//                            Log.d("DEBUG", "FOUND SATELLITE $name WITH ID $id")

                            } else if (feature.properties()!!.get(getString(R.string.cluster_point_count)) != null) {
//                            Log.d(
//                                "DEBUG",
//                                "Cluster with ${feature.properties()!!
//                                    .get(CLUSTER_POINT_COUNT)} satellites found"
//                            )
                            } else {
//                            Log.d("DEBUG", "Non-satellite found")
                            }
                        } else {
//                        Log.d("DEBUG", "Feature has no properties")
                        }
                    }
                    return@addOnMapClickListener true
                }
                map.addOnMapLongClickListener { point ->
                    val pixel: PointF = map.projection?.toScreenLocation(point)
                            ?: PointF(0F, 0F)

                    val rect = 20F
                    val features: List<Feature> = map.queryRenderedFeatures(
                            RectF(
                                    pixel.x - rect,
                                    pixel.y - rect,
                                    pixel.x + rect,
                                    pixel.y + rect
                            ), getString(R.string.unclustered_layer_id)
                    )!!

                    for (feature in features) {
                        if (feature.properties() != null) {
                            val name = feature.properties()?.get(getString(R.string.feature_name))
                            val id = feature.properties()?.get(getString(R.string.feature_id))
                            if (name != null && id != null) {
                                showToast("Favorited satellite $name WITH ID $id")
                                SatelliteManager.toggleFavorite(id.toString())
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
            initializeScope.async { mapLock.unlock() }.await()
        }

    }

    private fun showToast(displayText: String) {
        val toastLen = Toast.LENGTH_SHORT
        val finishedToast = Toast.makeText(application, displayText, toastLen)
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
        mapboxMap.setStyle(Style.Builder().fromUri(Style.DARK)
        ) { style ->
            mapStyle = style
        }
        onMapInitialized!!.invoke()

        initializeScope.launch {
            map = mapboxMap
            mapLock.unlock()
        }
    }

    @SuppressLint("MissingPermission")
    fun enableLocationComponent(loadedMapStyle: Style) {

        // Request location permissions if not granted
//        Log.d("DEBUG", "Hi")
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
//            Log.d("DEBUG", "Hello")
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
//                    Log.d("DEBUG", "Couldn't get GPS location")
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

        if (granted) {
            Toast.makeText(this, "Please restart the app for changes to take effect", Toast.LENGTH_LONG).show()
            enableLocationComponent(map.style!!)
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startAutoUpdates() {
        val model: NightSkyViewModel by viewModels()
        updateScope.launch {
            model.requestSatelliteUpdateAsync(SatelliteManager.getSatellitesIterator()).await()
            updateMap(model.getDisplayedSatellites())
            while (true) {
                delay(autoUpdateWaitTime)
                model.requestSatelliteUpdateAsync(SatelliteManager.getSatellitesIterator()).await()
                updateMap(model.getDisplayedSatellites())
            }
        }
    }

    fun getStringIntMap(stringArray: Array<String>): Map<String, Int> {
//        val stringArray: Array<String> = resources.getStringArray(stringArrayResourceId)
        val stringMap: MutableMap<String, Int> = mutableMapOf()

        for (item in stringArray) {
            val pair = item.split("|")
            val key = pair.get(0)
            val value = pair.get(1).toInt()
            stringMap.set(key, value)
        }
        return stringMap
    }

    companion object {
        var nyoomCompleted = false
    }

}
