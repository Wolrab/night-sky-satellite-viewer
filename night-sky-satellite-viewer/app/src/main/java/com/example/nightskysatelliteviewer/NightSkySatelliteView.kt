package com.example.nightskysatelliteviewer

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

    fun getDisplayedSatellites(): MutableList<Feature> {
        return displayedSatellites
    }

    fun bufferDisplayedSatellites(satellites: ArrayList<Feature>) {
        displayedSatellites.addAll(satellites)
    }

    fun clearBufferedSatellites() {
        displayedSatellites = mutableListOf()
    }


    /**
     * Asynchronously generates all the satellite
     * features and buffers the satellites features
     * into the ViewModel afterwards.*/
    fun requestSatelliteUpdateAsync(iterator: Iterator<Satellite>, satelliteFilter: (Satellite) -> Boolean = {_: Satellite -> true}): Deferred<Any> {
//        Log.d("DEBUG", "==============STARTING REQUEST===============")
        return viewModelScope.async {
            val displayedSatsBuffer = arrayListOf<Feature>()

            val satellites = iterator.asSequence().filter { satelliteFilter(it) }

            for (satellite in satellites) {
//                Log.d("DEBUG", "Satellite ${satellite.name} in contextIterator")
                val pair = TLEConversion.satelliteToLatLng(satellite)
                if (pair != null) {
                    val lat = pair.first
                    val lng = pair.second

                    val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                    feature.addStringProperty(getApplication<Application>().getString(R.string.feature_name), satellite.name)
                    feature.addStringProperty(getApplication<Application>().getString(R.string.feature_id), satellite.id)
                    feature.addStringProperty(getApplication<Application>().getString(R.string.feature_tle), satellite.tleString)
                    feature.addBooleanProperty(getApplication<Application>().getString(R.string.feature_is_favorite), satellite.isFavorite)
                    // TODO: More satellite properties can be cached by adding them to the feature

                    displayedSatsBuffer.add(feature)
                }
            }
            bufferDisplayedSatellites(displayedSatsBuffer)
//            Log.d("DEBUG", "==============ENDING REQUEST===============")
        }
    }
}