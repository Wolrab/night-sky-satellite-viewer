package com.example.nightskysatelliteviewer

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.openDatabase
import android.database.sqlite.SQLiteException
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.geometry.LatLng
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
private val DATABASE_Version: Int = 1

private val ALL_SATS_TABLE_NAME = "satellites"

private val COL_ID = "_id"
private val COL_CELESTRAKID = "celestrakId"
private val COL_NAME = "name"
private val COL_EPOCH = "epoch"
private val COL_TLE = "tle"

private val FAVORITES_TABLE_NAME = "satelliteFavoritesDB"

object SatelliteManager {

    var numSatellites: Int = 0

    lateinit var satelliteDbHelper: SatelliteDBHelper

    var initialized = false
    var waiting = false

    var onDbUpdateComplete: (()->Unit)? = null
    var onDbUpdateStart: (()->Unit)? = null

    private val dbScope = CoroutineScope(Job() + Dispatchers.IO)

    fun initialize(context: Context, activity: MainActivity) {
        createOrFindDb(context)
        initialized = true
    }

    // TODO: Replace with real later
    fun getSatellitesIterator(): Iterator<DisplaySatellite> {
        val list = listOf(DisplaySatellite("LEL", "1", LatLng(0.0,0.0)))
        return list.iterator()
    }


    private fun getSatelliteNodeList(): NodeList? {
        var satelliteXmlUrl = URL(satelliteXmlUrlText)
        val xmlText = satelliteXmlUrl.readText()
        val xmlDoc = stringToXmlDoc(xmlText)
        return xmlDoc.getElementsByTagName("body")
    }

    fun updateAllSatellites(){
        val updateJob = dbScope.launch {
            onDbUpdateStart?.invoke()
            waiting = true
            val satelliteXmlElements = getSatelliteNodeList()
            if (satelliteXmlElements != null) {
                for (i in 0 until satelliteXmlElements.length) {
                    satelliteDbHelper.updateSatelliteFromXml((satelliteXmlElements.item(i) as Element))
                }
            }
            waiting = false
            onDbUpdateComplete?.invoke()
        }
    }

    private fun createOrFindDb(context: Context){
        satelliteDbHelper = SatelliteDBHelper(context)
        if (satelliteDbHelper.checkDbInitialized(context)) {
            numSatellites = satelliteDbHelper.getNumSatellites()
            onDbUpdateComplete?.invoke()
            waiting = false
        } else {
            val updateJob = dbScope.launch {
            waiting = true
            onDbUpdateStart?.invoke()
            val satelliteXmlElements = getSatelliteNodeList()
            if (satelliteXmlElements != null) {
                numSatellites = satelliteXmlElements.length
                for (i in 0 until satelliteXmlElements.length) {
                    satelliteDbHelper.addSatelliteFromXml((satelliteXmlElements.item(i) as Element))
                }
            }
            waiting = false
            onDbUpdateComplete?.invoke()
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

    private var CREATE_SATS_TABLE = "CREATE TABLE $ALL_SATS_TABLE_NAME " +
            "($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "$COL_CELESTRAKID VARCHAR(255), " +
            "$COL_NAME VARCHAR(255), " +
            "$COL_EPOCH VARCHAR(255));"

    private var CREATE_FAVORITES_TABLE = "CREATE TABLE $FAVORITES_TABLE_NAME " +
            "($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "$COL_CELESTRAKID VARCHAR(255));"

    private var DROP_MAIN_TABLE = "DROP TABLE IF EXISTS $ALL_SATS_TABLE_NAME"

    private lateinit var satellitesDb: SQLiteDatabase

    override fun onCreate(db: SQLiteDatabase?) {
        try {
            db?.execSQL(CREATE_SATS_TABLE)
            db?.execSQL(CREATE_FAVORITES_TABLE)

        } catch (e: SQLiteException){
            throw Exception("Error creating database")
        }
        satellitesDb = db!!
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        throw Exception("Can't upgrade database")
    }

    fun checkDbInitialized(context: Context): Boolean {
        var r = true
        try {
            val databasePath = context.getDatabasePath(DATABASE_NAME)
            val tempDb = openDatabase(databasePath.absolutePath, null,
                    SQLiteDatabase.OPEN_READWRITE);
            r = true
            satellitesDb = tempDb
        } catch (e: SQLiteException){
            r = false
        }
        return r
    }

    fun getNumSatellites(): Int {
        satellitesDb
        return DatabaseUtils.queryNumEntries(satellitesDb, ALL_SATS_TABLE_NAME, null, null).toInt()
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
        val database = satellitesDb
        val satelliteQuery = "SELECT * FROM $ALL_SATS_TABLE_NAME WHERE $COL_ID = '$id'"
        val result = database.rawQuery(satelliteQuery, null)
        result.moveToFirst()
        val name = result.getString(result.getColumnIndex(COL_NAME))
        val celestrakId = result.getString(result.getColumnIndex(COL_CELESTRAKID))
        val epoch = result.getString(result.getColumnIndex(COL_EPOCH)).toLong()
        result.close()
        return Satellite(name, celestrakId, epoch)
    }

    fun getSatelliteByCelestrakId(celestrakId: String): Satellite {
        val database = satellitesDb
        val satelliteQuery = "SELECT * FROM $ALL_SATS_TABLE_NAME WHERE $COL_CELESTRAKID = '$celestrakId'"
        val result = database.rawQuery(satelliteQuery, null)
        result.moveToFirst()
        val name = result.getString(result.getColumnIndex(COL_NAME))
        val epoch = result.getString(result.getColumnIndex(COL_EPOCH)).toLong()
        result.close()
        return Satellite(name, celestrakId, epoch)
    }

    fun getSatelliteByName(name: String): Satellite {
        val database = satellitesDb
        val satelliteQuery = "SELECT * FROM $ALL_SATS_TABLE_NAME WHERE $COL_NAME = '$name'"
        val result = database.rawQuery(satelliteQuery, null)
        result.moveToFirst()
        val celestrakId = result.getString(result.getColumnIndex(COL_CELESTRAKID))
        val epoch = result.getString(result.getColumnIndex(COL_EPOCH)).toLong()
        result.close()
        return Satellite(name, celestrakId, epoch)
    }
}
