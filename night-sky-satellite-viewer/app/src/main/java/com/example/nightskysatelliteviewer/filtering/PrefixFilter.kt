package com.example.nightskysatelliteviewer.filtering

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.viewModels
import com.example.nightskysatelliteviewer.MainActivity
import com.example.nightskysatelliteviewer.NightSkyViewModel
import com.example.nightskysatelliteviewer.Satellite
import com.example.nightskysatelliteviewer.SatelliteManager
import com.mapbox.geojson.Feature

class PrefixFilter(override val filterContext: Context): SatelliteFilter(filterContext), TextWatcher, Iterator<Satellite> {
    private var filterPattern: String = ""
    private var id = 1
    private var nextSat: Satellite? = null

    // TODO: Add some form of delay so this isn't triggered every single letter?
    override fun afterTextChanged(s: Editable?) {
        filterPattern = s?.toString() ?: ""
        val model: NightSkyViewModel by (filterContext as MainActivity).viewModels()
        // TODO: Generate features (?)
        val features = listOf<Feature>()
//        model.bufferSatellites(features)
//        model.pushSatellites()
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}


    override fun hasNext(): Boolean {
        var next = false

        if (id <= SatelliteManager.numSatellites) {
            var sat =
                SatelliteManager.getSatelliteByNumericId(
                    id
                )
            id += 1
            while (!matched(sat.name) && id <= SatelliteManager.numSatellites) {
                sat =
                    SatelliteManager.getSatelliteByNumericId(
                        id
                    )
                id += 1
            }
            if (id <= SatelliteManager.numSatellites) {
                nextSat = sat
            }
        }

        if (id <= SatelliteManager.numSatellites) {
            next = true
            id += 1
        }
        return next
    }

    override fun next(): Satellite {
        return nextSat!!
    }

    private fun matched(name: String): Boolean {
        var match = false
        val common = name.capitalize().commonPrefixWith(filterPattern)

        if (common.contentEquals(filterPattern)) match = true

        return match
    }

    override fun iterator(): Iterator<Satellite> {
        return this
    }

}
