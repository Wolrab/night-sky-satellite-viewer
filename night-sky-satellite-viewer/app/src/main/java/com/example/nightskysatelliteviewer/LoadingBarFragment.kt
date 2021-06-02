package com.example.nightskysatelliteviewer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView

class LoadingBarFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_loading_bar, container, false)
    }

    fun setTitle(title: String) {
        val titleText = activity?.findViewById<TextView>(R.id.loading_title)
        titleText?.text = title
    }

    fun setProgress(progress: Int) {
        val progressBar = activity?.findViewById<ProgressBar>(R.id.progress_bar)
        progressBar?.progress = progress
    }
}