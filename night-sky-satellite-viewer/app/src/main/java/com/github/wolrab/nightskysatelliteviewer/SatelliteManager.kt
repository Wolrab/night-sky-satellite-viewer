package com.github.wolrab.nightskysatelliteviewer

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

private val DATABASE_NAME  = "satelliteDB"

private val COL_CELESTRAKID = "celestrakId"
private val COL_TLE = "tle"
private val COL_NAME = "name"
private val COL_IS_FAVORITE = "isFavorite"

class NoConnectivityException () : IOException()

object SatelliteManager {
    var numSatellites: Int = 0
    var percentLoaded: Int = 0

    lateinit var satelliteDB: SatelliteDB
    lateinit var satelliteDao: SatelliteDao

    var initialized = false
    var waiting = false

    private val dbScope = CoroutineScope(Job() + Dispatchers.IO)

    fun initialize(context: Context): Deferred<Unit?> {
        satelliteDB = Room.databaseBuilder(
            context,
            AppDatabase::class.java, DATABASE_NAME
        ).build()
        satelliteDao = satelliteDB.satelliteDao()


        return dbScope.async {
            val satellites = satelliteDao.getAll()
            if (satellites.size == 0)  { // TODO: Or TLE timeout
                waiting = true
                val satelliteXmlElements = getSatelliteNodeList()
                val tleStrings = getTleStrings()
                if (satelliteXmlElements != null) {
                    numSatellites = satelliteXmlElements.length
                    for (i in 0 until satelliteXmlElements.length) {
                        percentLoaded = ((i.toFloat() / numSatellites.toFloat()) * 100).toInt()
                        val sat = createSatellite(satelliteXmlElements[i], tleStrings[i])
                        satelliteDao.insertSatellite(sat)
                    }
                }
                waiting = false
            }
            else {
                numSatellites = satellites.size
                percentLoaded = 100
                waiting = false
            }
            initialized = true
        }
    }

    // Fetch raw string form url
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

    // Get TLE for each satellite
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

    // Get XML for each satellite
    private fun getSatelliteNodeList(): NodeList? {
        val xmlText = getUrlText(satelliteXmlUrlText)
        val xmlDoc = stringToXmlDoc(xmlText)
        return xmlDoc.getElementsByTagName("body")
    }

    private fun createSatellite(xmlElement: Element, tle: List<String>) {
        val id = getElementContent(xmlElement, "OBJECT_ID")
        val name = getElementContent(xmlElement, "OBJECT_NAME")
        val tle = tle[0] + "\n" + tle[1] + "\n" + tle[2]
        val isFavorite = false
        return Satellite(id, name, tle, isFavorite)
    }

    fun getSatellitesIterator(): Iterator<Satellite> {
        if (!initialized) { throw UninitializedPropertyAccessException("Database not initialized") }
        return satelliteDao.getAll().iterator()
    }

    fun getFavoriteSatellitesIterator(): Iterator<Satellite> {
        if (!initialized) { throw UninitializedPropertyAccessException("Database not initialized") }
        return // TODO
    }

    fun getMatchingNameSatellitesIterator(nameSearch: String): Iterator<Satellite>  {
        if (!initialized) { throw UninitializedPropertyAccessException("Database not initialized") }
        return satelliteDao.getByName(nameSearch).iterator()
    }

    fun toggleFavorite(celestrakId: String) {
        if (!initialized) { throw UninitializedPropertyAccessException("Database not initialized") }
        val sat = satelliteDao.getSatellite(celestrakId)
        val isFavorite = !sat.isFavorite
        satelliteDao.updateSatellite(Satellite(sat.celestID, sat.name, sat.tleString, isFavorite))
    }

    private fun stringToXmlDoc(text: String): Document {
        val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
        val builder: DocumentBuilder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(text)))
    }
}

@Entity
class Satellite(
    @PrimaryKey(name = COL_CELESTRAKID) val celestID: String,
    @ColumnInfo(name = COL_NAME) val name: String,  
    @ColumnInfo(name = COL_TLE) val tleString: String, 
    @ColumnInfo(name = COL_IS_FAVORITE) val isFavorite: Boolean
)

@Dao
interface SatelliteDao {
    @Query("SELECT * FROM satellite")
    fun getAll(): List<Satellite>

    @Query("SELECT * FROM satellite WHERE $COL_NAME LIKE :name")
    fun getByName(name: String): List<Satellite>

    @Query("SELECT * FROM satellite WHERE $COL_NAME LIKE :name")
    fun getByFavorite(name: String): List<Satellite>

    @Query("SELECT * FROM satellite WHERE $COL_CELESTRAKID = :id LIMIT 1")
    fun getSatellite(id: String): Satellite

    @Update
    fun updateSatellite(sat: Satellite)

    @Insert
    fun insertSatellite(sat: Satellite)

    @Delete
    fun deleteSatellite(sat: Satellite)
}

@Database(entities = [Satellite::class], version = 1)
abstract class SatelliteDB : RoomDatabase() {
    abstract fun satelliteDao(): SatelliteDao
}

/*
class SatelliteDBHelper() {

    private var CREATE_SATS_TABLE = "CREATE TABLE $ALL_SATS_TABLE_NAME " +
            "($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "$COL_CELESTRAKID VARCHAR(255), " +
            "$COL_NAME VARCHAR(255), " +
            "$COL_TLEONE VARCHAR(255), " +
            "$COL_TLETWO VARCHAR(255), " +
            "$COL_TLETHREE VARCHAR(255), " +
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
        Log.d("FAV", "We're toggling")
        val database = this.writableDatabase
        val contentValues = ContentValues()
        val satelliteQuery = "SELECT $COL_IS_FAVORITE FROM $ALL_SATS_TABLE_NAME WHERE $COL_CELESTRAKID = '$celestrakId'"
        val result = database.rawQuery(satelliteQuery, null)
        Log.d("FAV", "Query done columns ${result.columnCount}")
        result.moveToFirst()
        val oldFavoriteVal = result.getInt(result.getColumnIndex(COL_IS_FAVORITE))
        val newFavoriteVal = when (oldFavoriteVal) {
            0 -> 1; else -> 0
        }
        contentValues.put(COL_IS_FAVORITE, newFavoriteVal)
        Log.d("FAV", "Value added")
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
*/