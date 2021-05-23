package com.example.nightskysatelliteviewer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
private val TABLE_NAME = "satellites"
private val DATABASE_Version: Int = 1

private val COL_ID ="_id"
private val COL_NAME = "name"
private val COL_EPOCH = "epoch"
private val COL_CELESTRAKID = "celestrakId"

object SatelliteManager {

    var xmlText = ""

    lateinit var satelliteDbHelper: SatelliteDBHelper

    private var initialized = false

    fun initialize(context: Context) {
        satelliteDbHelper = SatelliteDBHelper(context)
        addAllSatellitesFromCelestrak()
        initialized = true
    }

    fun updateSatellite(id: Int) {
        //TODO: update satellite with given id
    }

    fun addAllSatellitesFromCelestrak() {
        val getSatelliteXml = GlobalScope.launch {
            var satelliteXmlUrl = URL(satelliteXmlUrlText)
            xmlText = satelliteXmlUrl.readText()
        }

        runBlocking {
            getSatelliteXml.join()
            val xmlDoc = stringToXmlDoc(xmlText)
            val satelliteXmlElements: NodeList? = xmlDoc.getElementsByTagName("body")
            if (satelliteXmlElements != null) {
                for (i in 0 until satelliteXmlElements.length) {
                    satelliteDbHelper.addSatelliteToDB((satelliteXmlElements.item(i) as Element))
                }
            }
        }
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

    private var CREATE_TABLE = "CREATE TABLE $TABLE_NAME " +
            "($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "$COL_NAME VARCHAR(255) , " +
            "$COL_EPOCH VARCHAR(255), " +
            "$COL_CELESTRAKID VARCHAR(225));"
    private var DROP_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    fun addSatelliteToDB(xmlElement: Element) {
        val name = getElementContent(xmlElement, "OBJECT_NAME")
        val id = getElementContent(xmlElement, "OBJECT_ID")
        val epoch = parseDate(getElementContent(xmlElement, "EPOCH"))
        val database = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(COL_NAME, name)
        contentValues.put(COL_CELESTRAKID, id)
        contentValues.put(COL_EPOCH, epoch)
        val result = database.insert(TABLE_NAME, null, contentValues)
    }

    private fun parseDate(dateString: String): Long {
        return SimpleDateFormat(epochDateFormat, Locale.US).parse(dateString).getTime()
    }

    private fun getElementContent(element: Element, name: String): String {
        return element.getElementsByTagName(name).item(0).textContent
    }

    fun getSatelliteByCelestrakId(celestrakId: String): Satellite {
        val database = this.writableDatabase
        val satelliteQuery = "SELECT * FROM $DATABASE_NAME WHERE $COL_CELESTRAKID = '$celestrakId\'"
        val result = database.rawQuery(satelliteQuery, null)
        val name = result.getString(result.getColumnIndex(COL_NAME))
        val epoch = result.getString(result.getColumnIndex(COL_EPOCH)).toLong()
        return Satellite(name, celestrakId, epoch)
    }

    fun getSatelliteByName(name: String): Satellite {
        val database = this.writableDatabase
        val satelliteQuery = "SELECT * FROM $DATABASE_NAME WHERE $COL_NAME = '$name'"
        val result = database.rawQuery(satelliteQuery, null)
        val celestrakId = result.getString(result.getColumnIndex(COL_ID))
        val epoch = result.getString(result.getColumnIndex(COL_EPOCH)).toLong()
        return Satellite(name, celestrakId, epoch)
    }
}