package com.example.nightskysatelliteviewer

import junit.framework.Assert.assertEquals
import org.junit.Test

class SatelliteManagerUnitTests {
    @Test
    fun satelliteCreation() {
        SatelliteManager.updateAll()
        assertEquals(SatelliteManager.satellites[0].name, "CALSPHERE 1")
        assertEquals(SatelliteManager.satellites[0].meanMotion, 13.73524110)

        assertEquals(SatelliteManager.satellites[1].name, "CALSPHERE 2")
        assertEquals(SatelliteManager.satellites[2].name, "LCS 1")
    }
}