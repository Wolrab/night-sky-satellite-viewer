package com.example.nightskysatelliteviewer

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import kotlinx.coroutines.*
import androidx.activity.viewModels

class MainActivity : AppCompatActivity() {
    // MapBox related attributes
    private var mapView: MapView? = null

    // Satellite data pipeline
    lateinit var model: NightSkyViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model: NightSkyViewModel by viewModels()
        this.model = model

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)

        mapView?.getMapAsync(this)

        val satelliteSearch = findViewById<EditText>(R.id.editTextSearch)
        satelliteSearch.addTextChangedListener(SatelliteFilter)
        SatelliteFilter.addSatelliteUpdateListener(this)

        val menu_button: FloatingActionButton = findViewById(R.id.menubutton)
        menu_button.setOnClickListener {
            var popup = PopupMenu(this, menu_button)
            popup.menuInflater.inflate(R.menu.popup, popup.menu)
            popup.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item: MenuItem? ->

                when (item!!.itemId) {
                    R.id.about -> {
                        //Toast.makeText(this@MainActivity, item.title, Toast.LENGTH_SHORT).show()
                        //TODO: Inflate textview with credits
                        Log.d("DEBUG", "you clicked " + item.title)
                    }
                }
                true
            })
            popup.show()
        }

        SatelliteManager.onDbUpdateComplete = {
            runOnUiThread(Runnable() {
                toggleWaitNotifier(false, "")
            })
            requestSatelliteUpdate()
        }

        SatelliteManager.initialize(applicationContext, this)
        SatelliteManager.onDbUpdateStart = {
            runOnUiThread(Runnable() {
                toggleWaitNotifier(true, "Updating database...")
            })
        }

        model.getUpdateScope().value?.launch {
            while (true) {
                kotlinx.coroutines.delay(15000L)
                if (conversionScopeSave != null)
                    requestSatelliteUpdate()
            }
        }
    }
   

    private fun showToast(displayText: String) {
        val toastLen = Toast.LENGTH_SHORT
        val finishedToast = Toast.makeText(applicationContext, displayText, toastLen)
        finishedToast.show()
    }

    fun toggleWaitNotifier(shown: Boolean, displayText: String) {
        runOnUiThread(kotlinx.coroutines.Runnable() {
            val waitNotifier: LinearLayout = findViewById<LinearLayout>(R.id.waitNotification)
            if (shown) {
                waitNotifier.visibility = View.VISIBLE
                val waitText = findViewById<TextView>(R.id.waitText)
                waitText.text = displayText
            } else {
                waitNotifier.visibility = View.INVISIBLE
            }
        })
    }

    public override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    public override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    inline fun Context.toast(message: () -> String) {
        Toast.makeText(this, message(), Toast.LENGTH_LONG).show()
    }
}