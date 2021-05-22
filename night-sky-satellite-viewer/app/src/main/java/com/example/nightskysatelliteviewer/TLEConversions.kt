import android.util.Log
import com.example.nightskysatelliteviewer.sdp4.SDP4
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A class created to process TLE satellite data returned by our API
 * using an open source implementation of the SDP/SGP algorithms.
 *
 * Its primary functions is to handle an instance of an SDP4 object,
 * and for any given satellite to return useful latitude/longitude
 * information. The library returns coordinates encoded in the
 * Earth-Centered Earth-Fixed (ECEF) coordinate system so transformations
 * are necessary before passing them back.
 *
 * Notes on ECEF can be found here
 * https://en.wikipedia.org/wiki/ECEF
 *
 * The source for all the math used in conversions was found here.
 * https://en.wikipedia.org/wiki/Geographic_coordinate_conversion
 */
class TLEConversion {
    // Parameters for the elipsoid that describes earth along
    //   with the square of the eccentricity for calculations later.
    private val a = 6378.0
    private val b = 6357.0
    private val eccSquared = 1 - (b / a).pow(2.0)

    // SDP4 library
    private val sdp4 = SDP4()
    private val fileName: String

    constructor(fileName: String) {
        sdp4.Init()
        this.fileName = fileName
    }

    fun getLongitude(satellite: String): Double {
        val pos = getSatellitePosition(satellite)
        var longitude = atan(pos[1] / pos[0])
        Log.d("CONVERSION", "Longitude: $longitude")
        longitude = Math.toDegrees(longitude)
        Log.d("CONVERSION", "Longitude (Degrees): $longitude")
        return longitude
    }

    fun getLatitude(satellite: String): Double {
        // Paramenters for Newton's method
        val thresh = 0.01
        val maxIter = 10

        val pos = getSatellitePosition(satellite)
        var kPrev = 1.0 / (1 - eccSquared) // Initial estimate
        Log.d("CONVERSION", "Latitude: k Initial: $kPrev")

        var i = 0
        var k = iterateK(pos, kPrev)
        while (abs(k - kPrev) >= thresh && i < maxIter) {
            Log.d("CONVERSION", "Latitude: k Iter:  $k")

            kPrev = k
            k = iterateK(pos, kPrev)
            i += 1
        }
        Log.d("CONVERSION", "Latitude: k Final: $k")

        val p = pos[0].pow(2.0) + pos[1].pow(2.0)
        var latitude = (k*pos[2])/p
        latitude = atan(latitude)
        Log.d("CONVERSION", "Latitude Final: $latitude")

        latitude = Math.toDegrees(latitude)
        Log.d("CONVERSION", "Latitude Final (Degrees): $latitude")
        return latitude
    }

    private fun iterateK(pos: Array<Double>, prev: Double): Double {
        val pSquared = pos[0].pow(2.0) + pos[1].pow(2.0)
        var cPrev =
            (pSquared + (1 - eccSquared) * pos[2].pow(2.0) * prev.pow(2.0)).pow(3.0 / 2.0)
        cPrev /= a * eccSquared
        return 1 + (pSquared + (1 - eccSquared) * pos[2].pow(2.0) * prev.pow(3.0)) / (cPrev - pSquared)
    }

    private fun getSatellitePosition(satellite: String): Array<Double> {
        sdp4.NoradByName(fileName, satellite)
        sdp4.GetPosVel(getJulianDate())
        val pos: Array<Double> = arrayOf(sdp4.itsR[0] * 1000000, sdp4.itsR[1] * 1000000, sdp4.itsR[2] * 1000000)
        Log.d("CONVERSION", "Position: ${pos[0]}, ${pos[1]}, ${pos[2]}")
        return pos
    }

    private fun getJulianDate(): Double {
        /* Get the milliseconds since UT 1970-01-01T00:00:00 from system clock.
         * Convert to days then to JD. */

        /* This was taken from the Times class, and manual checking against his comments
         * in that file verifies this is accurate (unless his comments aren't :/) */
        val julianDate = System.currentTimeMillis().toDouble() / 86400000.0 + 587.5 - 10000.0
        Log.d("CONVERSION", "Julian Date: $julianDate")
        return julianDate
    }
}