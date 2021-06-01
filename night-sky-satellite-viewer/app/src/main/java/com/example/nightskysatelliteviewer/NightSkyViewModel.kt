package com.example.nightskysatelliteviewer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class NightSkyViewModel: ViewModel(), SatelliteUpdateListener {
    private val displayedSatellites: MutableLiveData<ArrayList<Feature>> by lazy {
        MutableLiveData<ArrayList<Feature>>().also {
            requestSatelliteUpdate()
        }
    }

    fun getSatellites(): LiveData<ArrayList<Feature>> {
        return displayedSatellites
    }

    /**
     * Launch both the producer and consumer of satellite data with respect
     *   to current filter settings.
     */
    override fun requestSatelliteUpdate() {
        conversionScopeSave?.cancel()

        val conversionScope = CoroutineScope(Job() + Dispatchers.IO)
        conversionScopeSave = conversionScope
        val conversionPipe = Channel<DisplaySatellite>()

        toggleWaitNotifier(true, message)

        conversionScope.launch {
            tleConversion.initConversionPipelineAsync(conversionPipe, conversionScope)
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
                displayedSats = displayedSatsBuffer

                runOnUiThread(Runnable() {
                    updateMap()
                    mapView!!.refreshDrawableState()
                    toggleWaitNotifier(false, "")
                })
            }
        }
    }
}