package com.example.nightskysatelliteviewer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

fun createTemporaryFileFromUrl(context: Context, fileName: String, urlText: String): File {
    val file = File(context.cacheDir, fileName)
    file.deleteOnExit()
    val urlReadJob = GlobalScope.launch {
        val url = URL(urlText)
        file.writeText(url.readText())
    }
    return file
}