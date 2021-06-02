package com.example.nightskysatelliteviewer

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.viewModels
import com.bumptech.glide.Glide
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex

class LoadingActivity : AppCompatActivity() {
    val updateScope = CoroutineScope(Job() + Dispatchers.IO)

    private val SAT_NAME = "sat_name"
    private val SAT_ID = "sat_id"
    private val SAT_TLE = "sat_tle"

    private val dbProp = 0.6
    private val calcProp = 0.4

    private var calculatedSatellites = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        val loadingAnimation: ImageView = findViewById(R.id.loading_animation)
        Glide.with(this).load(R.drawable.satellite_animation).into(loadingAnimation)

        val loadingFragment = supportFragmentManager.findFragmentById(R.id.loading_fragment)
        (loadingFragment as LoadingBarFragment).setTitle(resources.getString(R.string.first_time_setup))

        val model: NightSkyViewModel by viewModels()
    }

    override fun onStart() {
        super.onStart()
        val loadingScope = CoroutineScope(Job() + Dispatchers.IO)

        val loadingFragment = supportFragmentManager.findFragmentById(R.id.loading_fragment)

        loadingScope.launch {
            while (true) {
                val dbLoaded = SatelliteManager.percentLoaded * dbProp
                val calcLoaded = (calculatedSatellites.toFloat() / (SatelliteManager.numSatellites).toFloat()) * calcProp
                (loadingFragment as LoadingBarFragment).setProgress(dbLoaded.toInt() + calcLoaded.toInt())
            }
        }

        val managerDone = Channel<Boolean>()
        val calcDone = Channel<Boolean>()
        loadingScope.launch {
//            Log.d("THREADS", "SHOULD RUN FIRST")
            SatelliteManager.initialize(applicationContext).await()
            managerDone.send(true)
        }
        loadingScope.launch {
            managerDone.receive()
//            Log.d("THREADS", "SHOULD RUN SECOND")
            requestSatelliteUpdateAsync().await()
            calcDone.send(true)
        }
        loadingScope.launch {
            calcDone.receive()
//            Log.d("THREADS", "SHOULD RUN THIRD")
            launchMainActivity()
        }

    }

    private fun launchMainActivity() {
        val mainIntent: Intent = Intent(this, MainActivity::class.java)
        startActivity(mainIntent)
    }

    /**
     * TODO: CODE COPYING NAUGHTY BOYS
     * Launch both the producer and consumer of satellite data.
     * Return an asynchronous job to await full group completion.
     */

    private fun requestSatelliteUpdateAsync(): Deferred<Any> {
        val model: NightSkyViewModel by viewModels()
        return updateScope.async {
            for (satellite in SatelliteManager.getSatellitesIterator()) {
                val pair = TLEConversion.satelliteToLatLng(satellite)
                if (pair != null) {
                    val lat = pair.first
                    val lng = pair.second

                    val feature = Feature.fromGeometry(Point.fromLngLat(lng, lat))
                    feature.addStringProperty(SAT_NAME, satellite.name)
                    feature.addStringProperty(SAT_ID, satellite.id)
                    feature.addStringProperty(SAT_TLE, satellite.tleString)
                    // TODO: More satellite properties can be cached by adding them to the feature

                    model.bufferDisplayedSatellites(arrayListOf(feature))
                    calculatedSatellites++
                }
            }
        }
    }
}