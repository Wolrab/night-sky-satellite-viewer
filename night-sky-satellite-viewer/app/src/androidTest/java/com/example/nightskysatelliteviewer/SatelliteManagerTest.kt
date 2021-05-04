package com.example.nightskysatelliteviewer

import junit.framework.TestCase
import org.junit.Test

class SatelliteManagerTest : TestCase() {
    @Test
    fun apiCall_working() {
        SatelliteManager.updateAll()
        assertEquals(SatelliteManager.satellites[0].name, "CALSPHERE 1")
    }
}