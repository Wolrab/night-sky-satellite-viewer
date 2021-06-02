package com.example.nightskysatelliteviewer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.mapbox.geojson.Feature

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
}