package com.example.nightskysatelliteviewer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import kotlinx.coroutines.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

const val satelliteXmlUrlText = "http://www.celestrak.com/NORAD/elements/gp.php?GROUP=active&FORMAT=xml"
const val epochDateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"

private val DATABASE_NAME  = "satelliteDB"
private val ALL_SATS_TABLE_NAME = "satellites"
private val DATABASE_Version: Int = 1

private val COL_ID = "_id"
private val COL_CELESTRAKID = "celestrakId"
private val COL_NAME = "name"
private val COL_EPOCH = "epoch"

private val FAVORITES_TABLE_NAME = "satelliteFavoritesDB"

object SatelliteManager {
    var numSatellites: Int = 0

    lateinit var satelliteDbHelper: SatelliteDBHelper

    var initialized = false
    var waiting = false

    var onDbUpdateComplete: (()->Unit)? = null
    var onDbUpdateStart: (()->Unit)? = null

    val dbScope = CoroutineScope(Job() + Dispatchers.IO)

    fun initialize(context: Context, activity: MainActivity) {
        satelliteDbHelper = SatelliteDBHelper(context)
        addAllSatellitesFromCelestrakIfUninitialized(activity)
        initialized = true
    }

    private fun getSatelliteNodeList(): NodeList? {
        var satelliteXmlUrl = URL(satelliteXmlUrlText)
        val xmlText = satelliteXmlUrl.readText()
        val xmlDoc = stringToXmlDoc(xmlText)
        return xmlDoc.getElementsByTagName("body")
    }

    fun updateAllSatellites(activity: MainActivity){
        val updateJob = dbScope.launch {
            activity.runOnUiThread(Runnable() {
                onDbUpdateStart?.invoke()
            })
            waiting = true
            Log.d("DATABASE_DEBUG", "Starting database update")
            val satelliteXmlElements = getSatelliteNodeList()
            if (satelliteXmlElements != null) {
                for (i in 0 until satelliteXmlElements.length) {
                    satelliteDbHelper.updateSatelliteFromXml((satelliteXmlElements.item(i) as Element))
                }
            }
            Log.d("DATABASE_DEBUG", "Finished database update")
            waiting = false
            activity.runOnUiThread(Runnable() {
                onDbUpdateComplete?.invoke()
            })
        }
    }

    fun addAllSatellitesFromCelestrakIfUninitialized(activity: MainActivity){
        if (!satelliteDbHelper.checkDbInitialized()) {
            val updateJob = dbScope.launch {
                waiting = true
                activity.runOnUiThread(Runnable() {
                    onDbUpdateStart?.invoke()
                })
                Log.d("DATABASE_DEBUG", "Starting database creation")
                val satelliteXmlElements = getSatelliteNodeList()
                if (satelliteXmlElements != null) {
                    numSatellites = satelliteXmlElements.length
                    for (i in 0 until satelliteXmlElements.length) {
                        satelliteDbHelper.addSatelliteFromXml((satelliteXmlElements.item(i) as Element))
                    }
                }
                Log.d("DATABASE_DEBUG", "Finished database creation")
                waiting = false
                activity.runOnUiThread(Runnable() {
                    onDbUpdateComplete?.invoke()
                })
            }
        }
    }

    fun getSatelliteByNumericId(id: Int): Satellite {
        if (!initialized) { throw UninitializedPropertyAccessException() }
        return satelliteDbHelper.getSatelliteByNumericId(id.toString())
    }

    fun getSatelliteByCelestrakId(celestrakId: String): Satellite {
        if (!initialized) { throw UninitializedPropertyAccessException() }
        return satelliteDbHelper.getSatelliteByCelestrakId(celestrakId)
    }

    fun getSatelliteByName(name: String): Satellite {
        if (!initialized) { throw UninitializedPropertyAccessException() }
        return satelliteDbHelper.getSatelliteByName(name)
    }

    private fun stringToXmlDoc(text: String): Document {
        val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
        val builder: DocumentBuilder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(text)))
    }
}

class SatelliteDBHelper(context: Context): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_Version){

    private var CREATE_TABLE = "CREATE TABLE $ALL_SATS_TABLE_NAME " +
            "($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "$COL_CELESTRAKID VARCHAR(255), " +
            "$COL_NAME VARCHAR(255), " +
            "$COL_EPOCH VARCHAR(255));"

    private var DROP_MAIN_TABLE = "DROP TABLE IF EXISTS $ALL_SATS_TABLE_NAME"

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    fun checkDbInitialized(): Boolean {
        try {
            val database = this.writableDatabase
            val satelliteQuery = "SELECT * FROM $DATABASE_NAME " +
                    "WHERE NAME='$ALL_SATS_TABLE_NAME' AND TYPE='TABLE'"
            val cursor = database.rawQuery(satelliteQuery, null)
            return true
        } catch (e: SQLiteException){
            if (e.message!!.contains("no such table")){
                return false
            }
        }
        return false
    }

    fun updateSatelliteFromXml(xmlElement: Element) {
        val name = getElementContent(xmlElement, "OBJECT_NAME")
        val id = getElementContent(xmlElement, "OBJECT_ID")
        val epoch = parseDate(getElementContent(xmlElement, "EPOCH"))
        val database = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(COL_NAME, name)
        contentValues.put(COL_EPOCH, epoch)
        database.update(ALL_SATS_TABLE_NAME, contentValues, "$COL_CELESTRAKID=?", arrayOf(id))
    }

    fun addSatelliteFromXml(xmlElement: Element) {
        val name = getElementContent(xmlElement, "OBJECT_NAME")
        val id = getElementContent(xmlElement, "OBJECT_ID")
        val epoch = parseDate(getElementContent(xmlElement, "EPOCH"))
        val database = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(COL_NAME, name)
        contentValues.put(COL_CELESTRAKID, id)
        contentValues.put(COL_EPOCH, epoch)
        database.insert(ALL_SATS_TABLE_NAME, null, contentValues)
    }

    private fun parseDate(dateString: String): Long {
        return SimpleDateFormat(epochDateFormat, Locale.US).parse(dateString).getTime()
    }

    private fun getElementContent(element: Element, name: String): String {
        return element.getElementsByTagName(name).item(0).textContent
    }

    fun getSatelliteByNumericId(id: String): Satellite {
        val database = this.writableDatabase
        val satelliteQuery = "SELECT * FROM $DATABASE_NAME WHERE $COL_ID = '$id'"
        val result = database.rawQuery(satelliteQuery, null)
        val name = result.getString(result.getColumnIndex(COL_NAME))
        val celestrakId = result.getString(result.getColumnIndex(COL_CELESTRAKID))
        val epoch = result.getString(result.getColumnIndex(COL_EPOCH)).toLong()
        return Satellite(name, celestrakId, epoch)
    }

    fun getSatelliteByCelestrakId(celestrakId: String): Satellite {
        val database = this.writableDatabase
        val satelliteQuery = "SELECT * FROM $DATABASE_NAME WHERE $COL_CELESTRAKID = '$celestrakId'"
        val result = database.rawQuery(satelliteQuery, null)
        val name = result.getString(result.getColumnIndex(COL_NAME))
        val epoch = result.getString(result.getColumnIndex(COL_EPOCH)).toLong()
        return Satellite(name, celestrakId, epoch)
    }

    fun getSatelliteByName(name: String): Satellite {
        val database = this.writableDatabase
        val satelliteQuery = "SELECT * FROM $DATABASE_NAME WHERE $COL_NAME = '$name'"
        val result = database.rawQuery(satelliteQuery, null)
        val celestrakId = result.getString(result.getColumnIndex(COL_CELESTRAKID))
        val epoch = result.getString(result.getColumnIndex(COL_EPOCH)).toLong()
        return Satellite(name, celestrakId, epoch)
    }
}
