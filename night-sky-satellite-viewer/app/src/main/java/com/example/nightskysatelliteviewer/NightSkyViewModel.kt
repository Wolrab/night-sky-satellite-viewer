package com.example.nightskysatelliteviewer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.maps.MapboxMap
import kotlinx.coroutines.*

class NightSkyViewModel(application: Application) : AndroidViewModel(application) {
    private val SAT_NAME = "sat_name"
    private val SAT_ID = "sat_id"
    private val SAT_TLE = "sat_tle"
    private val autoupdateWaitTime = 5000L

    //TODO: might need this
    //private val context = getApplication<Application>().applicationContext

    val displayedSatellites: MutableLiveData<ArrayList<Feature>> by lazy {
        MutableLiveData<ArrayList<Feature>>().also {
            startAutoUpdates()
        }
    }

    private fun startAutoUpdates() {
        viewModelScope.launch {
            Log.d("DEBUGGING", "STARTING SATELLITE POSITION UPDATES")
            requestSatelliteUpdateAsync().await()
            while (true) {
                delay(autoupdateWaitTime)
                requestSatelliteUpdateAsync().await()
            }
        }
    }

    /**
     * Launch both the producer and consumer of satellite data.
     * Return an asynchronous job to await full group completion.
     */
    private fun requestSatelliteUpdateAsync(): Deferred<Any> {
        return viewModelScope.async {
            val displayedSatsBuffer = arrayListOf<Feature>()
            Log.d("DEBUGGING", "LINE 46")
            for (satellite in SatelliteManager.getSatellitesIterator()) {
                Log.d("DEBUGGING", "PROCESSING SAT: ${satellite.name}")
                val pair = TLEConversion.satelliteToLatLng(satellite)

                if (pair != null) {
                    Log.d("DEBUGGING", "GOT A SAT")
                    val lat = pair.first
                    val lng = pair.second

                    val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                    feature.addStringProperty(SAT_NAME, satellite.name)
                    feature.addStringProperty(SAT_ID, satellite.id)
                    feature.addStringProperty(SAT_TLE, satellite.tleString)
                    // TODO: More satellite properties can be cached by adding them to the feature

                    displayedSatsBuffer.add(feature)
                }
            }
        }
    }
}