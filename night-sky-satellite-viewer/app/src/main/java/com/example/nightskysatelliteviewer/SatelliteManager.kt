package com.example.nightskysatelliteviewer

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.openDatabase
import android.database.sqlite.SQLiteException
import kotlinx.coroutines.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.net.URL
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

const val satelliteXmlUrlText = "http://www.celestrak.com/NORAD/elements/gp.php?GROUP=active&FORMAT=xml"
const val satelliteTlelUrlText = "http://www.celestrak.com/NORAD/elements/gp.php?GROUP=active&FORMAT=tle"
const val maxUrlRetries = 5

private val DATABASE_NAME  = "satelliteDB"
private val DATABASE_Version: Int = 1

private val ALL_SATS_TABLE_NAME = "satellites"

private val COL_ID = "_id"
private val COL_CELESTRAKID = "celestrakId"
private val COL_TLE = "tle"
private val COL_NAME = "name"
private val COL_IS_FAVORITE = "isFavorite"

class NoConnectivityException () : IOException()

object SatelliteManager {

    var numSatellites: Int = 0

    var percentLoaded: Int = 0

    lateinit var satelliteDbHelper: SatelliteDBHelper

    var initialized = false
    var waiting = false

    var onDbUpdateComplete: (()->Unit)? = null
    var onDbUpdateStart: (()->Unit)? = null

    private val dbScope = CoroutineScope(Job() + Dispatchers.IO)

    fun initialize(context: Context) {
        createOrFindDb(context)
        initialized = true
    }

    private fun getUrlText(urlString: String): String {
        var urlText = ""
        var url = URL(urlString)
        var retries = 0
        var done = false
        while (retries < maxUrlRetries && !done) {
            try {
                val url = URL(urlString)
                urlText = url.readText()
                done = true
            }
            catch (e: Exception) {
                retries += 1
            }
        }
        if (retries == maxUrlRetries) {
            throw NoConnectivityException()
        }
        return urlText
    }

    private fun getTleStrings(): List<String> {
        val tleText = getUrlText(satelliteTlelUrlText).trim()
        val linesList: MutableList<String> = tleText.lines().toMutableList()
        val threeLinesList: MutableList<String> = mutableListOf<String>()
        while (!linesList.isEmpty()) {
            var threeLines = ""
            for (i in 0 until 3) {
                val nextLine = linesList.first()
                linesList.removeAt(0)
                threeLines += nextLine
            }
            threeLinesList.add(threeLines)
        }
        return threeLinesList
    }

    private fun getSatelliteNodeList(): NodeList? {
        val xmlText = getUrlText(satelliteXmlUrlText)
        val xmlDoc = stringToXmlDoc(xmlText)
        return xmlDoc.getElementsByTagName("body")
    }

    fun updateAllSatellites() {
        val updateJob = dbScope.launch {
            onDbUpdateStart?.invoke()
            waiting = true
            val satelliteXmlElements = getSatelliteNodeList()
            val tleStrings = getTleStrings()
            if (satelliteXmlElements != null) {
                for (i in 0 until satelliteXmlElements.length) {
                    percentLoaded = ((i.toFloat()/ numSatellites.toFloat()) * 100).toInt()
                    satelliteDbHelper.updateSatelliteFromXmlTle(
                            (satelliteXmlElements.item(i) as Element), tleStrings[i])
                }
            }
            waiting = false
            onDbUpdateComplete?.invoke()
        }
    }

    private fun createOrFindDb(context: Context) {
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
                val tleStrings = getTleStrings()
                if (satelliteXmlElements != null) {
                    numSatellites = satelliteXmlElements.length
                    for (i in 0 until satelliteXmlElements.length) {
                        percentLoaded = ((i.toFloat()/ numSatellites.toFloat()) * 100).toInt()
                        satelliteDbHelper.addSatelliteFromXmlTle(
                                (satelliteXmlElements.item(i) as Element), tleStrings[i])
                    }
                }
                waiting = false
                onDbUpdateComplete?.invoke()
            }
        }
    }

    fun getSatellitesIterator(): Iterator<Satellite> {
        if (!initialized) { throw UninitializedPropertyAccessException("Database not initialized") }
        return satelliteDbHelper.getSatellitesIterator()
    }

    fun getFavoriteSatellitesIterator(): Iterator<Satellite> {
        if (!initialized) { throw UninitializedPropertyAccessException("Database not initialized") }
        return satelliteDbHelper.getFavoriteSatellitesIterator()
    }

    fun getMatchingNameSatellitesIterator(nameSearch: String): Iterator<Satellite>  {
        if (!initialized) { throw UninitializedPropertyAccessException("Database not initialized") }
        return satelliteDbHelper.getMatchingNameSatellitesIterator(nameSearch)
    }

    fun toggleFavorite(celestrakId: String) {
        if (!initialized) { throw UninitializedPropertyAccessException("Database not initialized") }
        satelliteDbHelper.toggleFavorite(celestrakId)
    }

    private fun stringToXmlDoc(text: String): Document {
        val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
        val builder: DocumentBuilder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(text)))
    }
}

class SatelliteDBHelper(context: Context): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_Version) {

    private var CREATE_SATS_TABLE = "CREATE TABLE $ALL_SATS_TABLE_NAME " +
            "($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "$COL_CELESTRAKID VARCHAR(255), " +
            "$COL_NAME VARCHAR(255), " +
            "$COL_TLE VARCHAR(255), " +
            "$COL_IS_FAVORITE INTEGER DEFAULT '0');"

    private var DROP_MAIN_TABLE = "DROP TABLE IF EXISTS $ALL_SATS_TABLE_NAME"

    private lateinit var satellitesDb: SQLiteDatabase

    override fun onCreate(db: SQLiteDatabase?) {
        try {
            db?.execSQL(CREATE_SATS_TABLE)

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

    fun toggleFavorite(celestrakId: String) {
        val database = this.writableDatabase
        val contentValues = ContentValues()
        val satelliteQuery = "SELECT $COL_IS_FAVORITE FROM $ALL_SATS_TABLE_NAME WHERE $COL_CELESTRAKID = '$celestrakId'"
        val result = database.rawQuery(satelliteQuery, null)
        result.moveToFirst()
        val oldFavoriteVal = result.getInt(result.getColumnIndex(COL_IS_FAVORITE))
        val newFavoriteVal = when (oldFavoriteVal) {
            0 -> 1; else -> 0
        }
        contentValues.put(COL_IS_FAVORITE, newFavoriteVal)
        database.update(ALL_SATS_TABLE_NAME, contentValues, "$COL_CELESTRAKID=?", arrayOf(celestrakId))
        result.close()
    }

    fun updateSatelliteFromXmlTle(xmlElement: Element, tleString: String) {
        val name = getElementContent(xmlElement, "OBJECT_NAME")
        val id = getElementContent(xmlElement, "OBJECT_ID")
        val database = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(COL_NAME, name)
        contentValues.put(COL_TLE, tleString)
        database.update(ALL_SATS_TABLE_NAME, contentValues, "$COL_CELESTRAKID=?", arrayOf(id))
    }

    fun addSatelliteFromXmlTle(xmlElement: Element, tleString: String) {
        val name = getElementContent(xmlElement, "OBJECT_NAME")
        val id = getElementContent(xmlElement, "OBJECT_ID")
        val database = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(COL_NAME, name)
        contentValues.put(COL_CELESTRAKID, id)
        contentValues.put(COL_TLE, tleString)
        database.insert(ALL_SATS_TABLE_NAME, null, contentValues)
    }

    private fun getElementContent(element: Element, name: String): String {
        return element.getElementsByTagName(name).item(0).textContent
    }

    class SatelliteCursorIterator(val cursor: Cursor): Iterator<Satellite> {

        override fun hasNext(): Boolean {
            var r = true
            if (cursor.isAfterLast) {
                cursor.close()
                r = false
            }
            return r
        }
        override fun next(): Satellite {
            if (cursor.isBeforeFirst) { cursor.moveToFirst() }
            val sat = getSatelliteAtCursor(cursor)
            cursor.moveToNext()
            return sat
        }

        private fun getSatelliteAtCursor(cursor: Cursor): Satellite {
            val name = cursor.getString(cursor.getColumnIndex(COL_NAME))
            val celestrakId = cursor.getString(cursor.getColumnIndex(COL_CELESTRAKID))
            val tleString = cursor.getString(cursor.getColumnIndex(COL_TLE))
            return Satellite(name, celestrakId, tleString)
        }
    }

    fun getSatellitesIterator(): SatelliteCursorIterator {
        val database = satellitesDb
        val satelliteQuery = "SELECT * FROM $ALL_SATS_TABLE_NAME"
        val allSatsCursor = database.rawQuery(satelliteQuery, null)
        return SatelliteCursorIterator(allSatsCursor)
    }

    fun getFavoriteSatellitesIterator(): SatelliteCursorIterator {
        val database = satellitesDb
        val faveQuery = "SELECT * FROM $ALL_SATS_TABLE_NAME WHERE $COL_IS_FAVORITE = '1'"
        val allSatsCursor = database.rawQuery(faveQuery, null)
        return SatelliteCursorIterator(allSatsCursor)
    }

    fun getMatchingNameSatellitesIterator(nameSearch: String): SatelliteCursorIterator {
        val database = satellitesDb
        val faveQuery = "SELECT * FROM $ALL_SATS_TABLE_NAME WHERE $COL_NAME LIKE '$nameSearch'"
        val allSatsCursor = database.rawQuery(faveQuery, null)
        return SatelliteCursorIterator(allSatsCursor)
    }
}
