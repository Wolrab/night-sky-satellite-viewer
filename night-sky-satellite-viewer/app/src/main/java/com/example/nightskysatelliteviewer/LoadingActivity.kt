package com.example.nightskysatelliteviewer

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.viewModels
import com.bumptech.glide.Glide
import kotlinx.coroutines.*

const val DELAYTIMEMILLIS = 4000L

class LoadingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        val loadingAnimation: ImageView = findViewById(R.id.loading_animation)
        Glide.with(this).load(R.drawable.satellite_animation).into(loadingAnimation)

        val loadingFragment = supportFragmentManager.findFragmentById(R.id.loading_fragment)
        (loadingFragment as LoadingBarFragment).setTitle(resources.getString(R.string.first_time_setup))
    }

    override fun onStart() {
        super.onStart()
        val loadingScope = CoroutineScope(Job() + Dispatchers.IO)

        val loadingFragment = supportFragmentManager.findFragmentById(R.id.loading_fragment)
        loadingScope.launch {
            while (true) {
                delay(20)
                runOnUiThread(kotlinx.coroutines.Runnable() {
                    (loadingFragment as LoadingBarFragment).setProgress(SatelliteManager.percentLoaded)
                })
            }
        }

        SatelliteManager.onDbUpdateComplete = {
            loadingScope.launch {
                delay(DELAYTIMEMILLIS)
                launchMainActivity()
            }
        }

        SatelliteManager.initialize(applicationContext)
    }

    private fun launchMainActivity() {
        val mainIntent: Intent = Intent(this, MainActivity::class.java)
        startActivity(mainIntent)
    }
}