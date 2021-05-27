package com.example.nightskysatelliteviewer

import com.mapbox.mapboxsdk.geometry.LatLng

class DisplaySatellite(val name: String, val id: String, val loc: LatLng)

class Satellite(val name: String, val id: String, val epoch: Long)