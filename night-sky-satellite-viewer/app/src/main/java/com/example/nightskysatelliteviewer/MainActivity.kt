package com.example.nightskysatelliteviewer

import android.os.Bundle
import android.os.StrictMode
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO: NO NETWORK SCHENANIGANS ON MAIN THREAD
        // TODO: REMOVE POLICY BELOW WHEN NETWORK ACCESS OUTSIDE OF MAIN THREAD
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        SatelliteManager.updateAll()
    }
}