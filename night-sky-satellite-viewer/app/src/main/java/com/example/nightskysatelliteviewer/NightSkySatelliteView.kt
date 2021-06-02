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