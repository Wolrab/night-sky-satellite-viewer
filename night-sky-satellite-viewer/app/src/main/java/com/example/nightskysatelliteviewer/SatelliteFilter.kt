package com.example.nightskysatelliteviewer

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Display

interface SatelliteUpdateListener {
    fun requestSatelliteUpdate()
}

object SatelliteFilter: TextWatcher, Iterable<Satellite> {
    private var filterPattern: String = ""
    private val listeners = mutableListOf<SatelliteUpdateListener>()

    fun addSatelliteUpdateListener(l: SatelliteUpdateListener) {
        listeners.add(l)
    }

    // TODO: Add some form of delay so this isn't triggered every single letter?
    override fun afterTextChanged(s: Editable?) {
        filterPattern = s?.toString() ?: ""
        for (l in listeners) {
            l.requestSatelliteUpdate()
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun iterator(): Iterator<Satellite> {
        return object: Iterator<Satellite> {
            private val filterPattern = SatelliteFilter.filterPattern.capitalize()
            private var id = 1
            private var nextSat: Satellite? = null

            override fun hasNext(): Boolean {
                var next = false

                if (id <= SatelliteManager.numSatellites) {
                    var sat = SatelliteManager.getSatelliteByNumericId(id)
                    id += 1
                    while (!matched(sat.name) && id <= SatelliteManager.numSatellites) {
                        sat = SatelliteManager.getSatelliteByNumericId(id)
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
        }
    }
}
