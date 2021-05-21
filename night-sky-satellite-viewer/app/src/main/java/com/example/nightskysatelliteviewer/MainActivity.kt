package com.example.nightskysatelliteviewer

import TLEConversion
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.URL


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO: NO NETWORK SCHENANIGANS ON MAIN THREAD
        // TODO: REMOVE POLICY BELOW WHEN NETWORK ACCESS OUTSIDE OF MAIN THREAD
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // TODO: TESTING: MOVE TO SatelliteManager
        val url = URL("http://www.celestrak.com/NORAD/elements/gp.php?GROUP=active&FORMAT=tle")
        val file = File(filesDir, "gp.txt")
        file.deleteOnExit()
        file.writeText(url.readText())

        val converter = TLEConversion(file.absolutePath)
        Log.d("TEST", converter.getLatitude("CALSPHERE 1").toString())
        Log.d("TEST", converter.getLongitude("CALSPHERE 1").toString())
    }
}