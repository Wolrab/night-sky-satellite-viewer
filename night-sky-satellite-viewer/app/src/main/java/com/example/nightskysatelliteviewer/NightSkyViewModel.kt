package com.example.nightskysatelliteviewer

import TLEConversion
import android.app.Activity
import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class NightSkyViewModel(application: Application) : AndroidViewModel(application) {
    // TODO: Move to strings.xml
    private val tleUrlText = "http://www.celestrak.com/NORAD/elements/gp.php?GROUP=active&FORMAT=tle"
    private val UNCLUSTERED_LAYER_ID = "SAT_LAYER"
    private val UNCLUSTERED_ICON_ID = "SAT_ICON"
    private val CLUSTERED_ICON_ID = "CLUSTER_ICON"
    private val CLUSTER_LAYERS = arrayOf("CLUSTER_1" to 150, "CLUSTER_2" to 20, "CLUSTER_3" to 0)
    private val CLUSTER_COUNT_LAYER = "COUNT"
    private val CLUSTER_POINT_COUNT = "point_count"
    private val SOURCE_ID = "SAT_SOURCE"
    private val SAT_NAME = "sat_name"
    private val SAT_ID = "sat_id"

    private val labelsize: Float = 15.0F

    private val context = getApplication<Application>().applicationContext
    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap

    private val displayedSatellites: MutableLiveData<ArrayList<Feature>> by lazy {
        MutableLiveData<ArrayList<Feature>>().also {
            requestSatelliteUpdate()
        }
    }

    private val updateScope: MutableLiveData<CoroutineScope> by lazy {
        MutableLiveData<CoroutineScope>().also {
            it.value = CoroutineScope(Job() + Dispatchers.IO)
            it.value!!.launch {
                delay(15000L)
                if (conversionScopeSave.value != null)
                    requestSatelliteUpdate()
            }

        }
    }

    private val conversionScopeSave: MutableLiveData<CoroutineScope> by lazy {
        MutableLiveData<CoroutineScope>().also {
            it.value = CoroutineScope(Job() + Dispatchers.IO)
        }
    }

    private val tleConversion: MutableLiveData<TLEConversion> by lazy {
        MutableLiveData<TLEConversion>().also {
            it.value = TLEConversion(tleUrlText)
        }
    }

    fun getMap(): MapboxMap {
        return map
    }

    fun getDisplayedSatellites(): LiveData<ArrayList<Feature>> {
        return displayedSatellites
    }

    fun getUpdateScope(): LiveData<CoroutineScope> {
        return updateScope
    }

    fun getConversionScopeSave(): LiveData<CoroutineScope> {
        return conversionScopeSave
    }

    fun setMapView(mapView: MapView) {
        this.mapView = mapView
    }

    fun setMap(map: MapboxMap) {
        this.map = map
    }

    fun bufferSatellites(satellites: List<Feature>) {

    }

    fun pushSatellites() {

    }

    /**
     * Launch both the producer and consumer of satellite data with respect
     *   to current filter settings.
     */
    fun requestSatelliteUpdate() {
        conversionScopeSave.value?.cancel()

        val conversionScope = CoroutineScope(Job() + Dispatchers.IO)
        conversionScopeSave.value = conversionScope
        val conversionPipe = Channel<DisplaySatellite>()

        toggleWaitNotifier(true, "Updating ")

        conversionScope.launch {
            tleConversion.value?.initConversionPipelineAsync(conversionPipe, conversionScope)
        }

        conversionScope.launch {
            val displayedSatsPipe = conversionPipe.iterator()
            val displayedSatsBuffer = arrayListOf<Feature>()
            while (isActive && displayedSatsPipe.hasNext()) {
                val sat = displayedSatsPipe.next()

                val feature = Feature.fromGeometry(Point.fromLngLat(sat.loc.longitude, sat.loc.latitude))
                feature.addStringProperty(SAT_NAME, sat.name)
                feature.addStringProperty(SAT_ID, sat.id)

                displayedSatsBuffer.add(feature)
            }
            withContext(NonCancellable) {
                displayedSatellites.value = displayedSatsBuffer

                (context as Activity).runOnUiThread(Runnable() {
                    updateMap()
                    mapView.refreshDrawableState()
                    toggleWaitNotifier(false, "")
                })
            }
        }
    }

    fun updateMap() {
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
                .withImage(UNCLUSTERED_ICON_ID, BitmapFactory.decodeResource(context.resources, R.drawable.sat), false)
                .withImage(CLUSTERED_ICON_ID, BitmapFactory.decodeResource(context.resources, R.drawable.sat_cluster), false)
                .withSource( GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(displayedSatellites.value!!), GeoJsonOptions()
                        .withCluster(true)
                        .withClusterMaxZoom(14)
                        .withClusterRadius(50)))
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
    }

    private fun showToast(displayText: String) {
        val toastLen = Toast.LENGTH_SHORT
        val finishedToast = Toast.makeText(getApplication(), displayText, toastLen)
        finishedToast.show()
    }

    private fun toggleWaitNotifier(shown: Boolean, displayText: String) {
        (context as Activity).runOnUiThread(kotlinx.coroutines.Runnable() {
        val waitNotifier: LinearLayout = context.findViewById<LinearLayout>(R.id.waitNotification)
        if (shown) {
            waitNotifier.visibility = View.VISIBLE
            val waitText = context.findViewById<TextView>(R.id.waitText)
            waitText.text = displayText
        } else {
            waitNotifier.visibility = View.INVISIBLE
        }
        })
    }
}