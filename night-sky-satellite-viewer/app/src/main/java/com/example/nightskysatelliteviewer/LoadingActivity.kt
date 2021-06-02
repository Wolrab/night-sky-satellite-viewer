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

class LoadingActivity : AppCompatActivity() {
    val updateScope = CoroutineScope(Job() + Dispatchers.IO)

    private val SAT_NAME = "sat_name"
    private val SAT_ID = "sat_id"
    private val SAT_TLE = "sat_tle"

    private lateinit var calcJob: Deferred<Any>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        val loadingAnimation: ImageView = findViewById(R.id.loading_animation)
        Glide.with(this).load(R.drawable.satellite_animation).into(loadingAnimation)

        val loadingFragment = supportFragmentManager.findFragmentById(R.id.loading_fragment)
        (loadingFragment as LoadingBarFragment).setTitle(resources.getString(R.string.first_time_setup))

        val model: NightSkyViewModel by viewModels()
        model.setDetailsSatellite(arrayListOf<Feature>())
    }

    override fun onStart() {
        super.onStart()
        val loadingScope = CoroutineScope(Job() + Dispatchers.IO)
        val dbProp = 0.6

        SatelliteManager.initialize(applicationContext)

        val loadingFragment = supportFragmentManager.findFragmentById(R.id.loading_fragment)
        val job1 = loadingScope.async {
            delay(50)
            while (!SatelliteManager.initialized) {

                runOnUiThread(kotlinx.coroutines.Runnable() {
                    val dbLoaded = SatelliteManager.percentLoaded * dbProp
                    (loadingFragment as LoadingBarFragment).setProgress((dbLoaded).toInt())
                })
            }
        }

        val job2 = loadingScope.async {
            job1.await()
            calcJob = requestSatelliteUpdateAsync()
            runOnUiThread(kotlinx.coroutines.Runnable() {
                Log.d("DEBUG", "Gonna compute?")
                while (!calcJob.isCompleted) {
                    Log.d("DEBUG", "Computing!")
                    val calcProp = calculatedSatellites.toFloat() / SatelliteManager.numSatellites.toFloat()
                    val fullLoaded = (calcProp * (1-dbProp)) + dbProp

                    (loadingFragment as LoadingBarFragment).setProgress(fullLoaded.toInt())
                }
            })
        }

        loadingScope.launch {
            job2.await()
            Log.d("DEBUG", "Oh hi didn't see you there")
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
    private var calculatedSatellites = 0

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