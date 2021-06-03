package com.example.nightskysatelliteviewer

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.openDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
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

//TODO: allow user to configure this
const val updateIntervalMillis = 86400000

private val DATABASE_NAME  = "satelliteDB"
private val DATABASE_Version: Int = 1

private val ALL_SATS_TABLE_NAME = "satellites"

private val COL_ID = "_id"
private val COL_CELESTRAKID = "celestrakId"
private val COL_TLEONE = "tleOne"
private val COL_TLETWO = "tleTwo"
private val COL_TLETHREE = "tleThree"
private val COL_NAME = "name"
private val COL_IS_FAVORITE = "isFavorite"

private val APP_DATA_TABLE_NAME = "data"
private val COL_DATE_LAST_UPDATED = "date_last_updated"

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

    fun initialize(context: Context): Deferred<Unit?> {
        val job = createOrFindDbAsync(context)
        initialized = true
        return job
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

    private fun getTleStrings(): List<List<String>> {
        val tleText = getUrlText(satelliteTlelUrlText)
        val linesList: List<String> = tleText.lines()
        val tleList: MutableList<List<String>> = mutableListOf()
        for (i in 0 until (linesList.size-3) step 3) {
            val tle = mutableListOf<String>()
            for (j in 0 until 3) {
                tle.add(linesList[i + j])
            }
            tleList.add(tle)
        }
        return tleList
    }

    private fun getSatelliteNodeList(): NodeList? {
        val xmlText = getUrlText(satelliteXmlUrlText)
        val xmlDoc = stringToXmlDoc(xmlText)
        return xmlDoc.getElementsByTagName("body")
    }

    private fun updateDb() {
        waiting = true
        onDbUpdateStart?.invoke()
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
        numSatellites = satelliteDbHelper.getNumSatellites()
        percentLoaded = 100
        onDbUpdateComplete?.invoke()
    }

    private fun createDb() {
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
        satelliteDbHelper.recordUpdateDate()
        numSatellites = satelliteDbHelper.getNumSatellites()
        waiting = false
        onDbUpdateComplete?.invoke()
    }

    private fun createOrFindDbAsync(context: Context): Deferred<Unit?> {
        satelliteDbHelper = SatelliteDBHelper(context)

        return dbScope.async {
            if (satelliteDbHelper.checkDbInitialized(context)) {
                if (satelliteDbHelper.checkUpdateNeeded()) {
                    Log.d("DEBUG", "------updating db------")
                    updateDb()
                } else {
                    waiting = false
                    percentLoaded = 100
                    numSatellites = satelliteDbHelper.getNumSatellites()
                    onDbUpdateComplete?.invoke()
                }
            } else {
                createDb()
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
            "$COL_TLEONE VARCHAR(255), " +
            "$COL_TLETWO VARCHAR(255), " +
            "$COL_TLETHREE VARCHAR(255), " +
            "$COL_IS_FAVORITE INTEGER DEFAULT '0');"

    private val CREATE_DATA_TABLE = "CREATE TABLE $APP_DATA_TABLE_NAME" +
            "($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "$COL_DATE_LAST_UPDATED BIGINT);"

    private var DROP_MAIN_TABLE = "DROP TABLE IF EXISTS $ALL_SATS_TABLE_NAME"
    private var DROP_DATA_TABLE = "DROP TABLE IF EXISTS $APP_DATA_TABLE_NAME"

    private lateinit var satellitesDb: SQLiteDatabase

    override fun onCreate(db: SQLiteDatabase?) {
        try {
            db?.execSQL(CREATE_SATS_TABLE)
            db?.execSQL(CREATE_DATA_TABLE)

        } catch (e: SQLiteException){
            throw Exception("Error creating database")
        }
        satellitesDb = db!!
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        //TODO: handle upgrade
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

    fun checkUpdateNeeded(): Boolean {
        val database = this.readableDatabase
        val updateQuery = "SELECT $COL_DATE_LAST_UPDATED FROM $APP_DATA_TABLE_NAME"
        val cursor = database.rawQuery(updateQuery, null)
        cursor.moveToFirst()
        val lastUpdatedDate = cursor.getLong(cursor.getColumnIndex(COL_DATE_LAST_UPDATED))
        val currentDate = System.currentTimeMillis()
        val diff = currentDate - lastUpdatedDate
        cursor.close()
        return diff > updateIntervalMillis
    }

    fun recordUpdateDate() {
        val database = this.writableDatabase
        val date = System.currentTimeMillis()
        val q = "REPLACE INTO $APP_DATA_TABLE_NAME VALUES(1, '$date')"
        database.execSQL(q)
    }

    fun getNumSatellites(): Int {
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

    fun updateSatelliteFromXmlTle(xmlElement: Element, tle: List<String>) {
        val name = getElementContent(xmlElement, "OBJECT_NAME")
        val id = getElementContent(xmlElement, "OBJECT_ID")
        val database = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(COL_NAME, name)
        contentValues.put(COL_TLEONE, tle[0])
        contentValues.put(COL_TLETWO, tle[1])
        contentValues.put(COL_TLETHREE, tle[2])
        database.update(ALL_SATS_TABLE_NAME, contentValues, "$COL_CELESTRAKID=?", arrayOf(id))
    }

    fun addSatelliteFromXmlTle(xmlElement: Element, tle: List<String>) {
        val name = getElementContent(xmlElement, "OBJECT_NAME")
        val id = getElementContent(xmlElement, "OBJECT_ID")
        val database = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(COL_NAME, name)
        contentValues.put(COL_CELESTRAKID, id)
        contentValues.put(COL_TLEONE, tle[0])
        contentValues.put(COL_TLETWO, tle[1])
        contentValues.put(COL_TLETHREE, tle[2])
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
            var tleString = cursor.getString(cursor.getColumnIndex(COL_TLEONE))+ "\n" +
                    cursor.getString(cursor.getColumnIndex(COL_TLETWO)) + "\n" +
                    cursor.getString(cursor.getColumnIndex(COL_TLETHREE))
            val isFavorite = cursor.getInt(cursor.getColumnIndex(COL_IS_FAVORITE)) != 0
            return Satellite(name, celestrakId, tleString, isFavorite)
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
