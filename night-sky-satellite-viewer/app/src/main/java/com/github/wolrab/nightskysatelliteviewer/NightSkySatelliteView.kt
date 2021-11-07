package com.github.wolrab.nightskysatelliteviewer

import android.app.Application
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

class NightSkyViewModel(application: Application) : AndroidViewModel(application) {
    // TODO: Mutex for more intricate managment
    private var displayedSatellites: MutableList<Feature> = mutableListOf()
    private val filters: HashMap<String, Filter> = hashMapOf()

    fun getDisplayedSatellites(): MutableList<Feature> {
        return displayedSatellites
    }

    fun bufferDisplayedSatellites(satellites: ArrayList<Feature>) {
        displayedSatellites.addAll(satellites)
    }

    fun clearBufferedSatellites() {
        displayedSatellites = mutableListOf()
    }

    fun addFilter(id: String, filter: Filter) {
        if (filters[id] == null) filters[id] = filter
    }

    fun getFilter(id: String): Filter? {
        return filters[id]
    }

    /**
     * Asynchronously generates all the satellite
     * features and buffers the satellites features
     * into the ViewModel afterwards.*/
    fun requestSatelliteUpdateAsync(iterator: Iterator<Satellite>): Deferred<Any> {
        return viewModelScope.async {
            val displayedSatsBuffer = arrayListOf<Feature>()

            var satellites = iterator
            for ((_,filter) in filters) {
                satellites = satellites.asSequence().filter { filter.filter(it) }.iterator()
            }

            for (satellite in satellites) {
                val pair = TLEConversion.satelliteToLatLng(satellite)
                if (pair != null) {
                    val lat = pair.first
                    val lng = pair.second

                    val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                    feature.addStringProperty(getApplication<Application>().getString(R.string.feature_name), satellite.name)
                    feature.addStringProperty(getApplication<Application>().getString(R.string.feature_id), satellite.celestID)
                    feature.addStringProperty(getApplication<Application>().getString(R.string.feature_tle), satellite.tleString)
                    feature.addBooleanProperty(getApplication<Application>().getString(R.string.feature_is_favorite), satellite.isFavorite)
                    // TODO: More satellite properties can be cached by adding them to the feature

                    displayedSatsBuffer.add(feature)
                }

            }
            bufferDisplayedSatellites(displayedSatsBuffer)
        }
    }
}