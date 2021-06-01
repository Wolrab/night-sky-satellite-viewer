import android.util.Log
import com.example.nightskysatelliteviewer.DisplaySatellite
import com.example.nightskysatelliteviewer.filtering.PrefixFilter
import com.example.nightskysatelliteviewer.filtering.SatelliteFilter
import com.example.nightskysatelliteviewer.sdp4.SDP4
import com.example.nightskysatelliteviewer.sdp4.SDP4NoSatException
import com.mapbox.mapboxsdk.geometry.LatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.StringReader
import java.net.URL
import kotlin.math.*

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
class TLEConversion(val tleText: String) {
    // Parameters for the elipsoid that describes earth along
    //   with the square of the eccentricity for calculations later.
    private val a = 6378.0
    private val b = 6357.0
    private val eccSquared = 1 - (b / a).pow(2.0)

    private val maxUrlRetries = 5
    private lateinit var filter: PrefixFilter

    // SDP4 library
    private val sdp4 = SDP4()

    init {
        sdp4.Init()
    }

    private lateinit var tle: String

    suspend fun initConversionPipelineAsync(outPipe: Channel<DisplaySatellite>, scope: CoroutineScope) {
        if ( !this::tle.isInitialized ) {
            var retries = 0
            var done = false
            while (retries < maxUrlRetries && !done) {
                try {
                    val url = URL(tleText)
                    // TODO: Logic in case of connection failure?
                    // - Alternative smaller API's we call?
                    tle = url.readText()
                    done = true
                }
                catch (e: Exception) {
                    retries += 1
                    // TODO: Notify user?
                }
            }
            if (retries == maxUrlRetries) {
                // TODO: "Hey buddy, get some internet!"
            }
        }

        val epoch = getJulianDate()

        while (scope.isActive && satellites.hasNext()) {
            try {
                val sat = satellites.next()
                val latLng = getLatLng(sat.name, epoch)
                val satOut = DisplaySatellite(sat.name, sat.id, latLng)
                outPipe.send(satOut)
            } catch (e: SDP4NoSatException){
                Log.d("CONVERSION","Local database out of date")
                // TODO: Update database?
            }
        }
        outPipe.close()
    }

    private fun getLatLng(satellite: String, epoch: Double): LatLng {
        val pos = getSatellitePositionNormalized(satellite, epoch)
        val lat = calculateLatitude(pos)
        val lng = calculateLongitude(pos)
        return LatLng(lat, lng)
    }

    private fun calculateLongitude(pos: Array<Double>): Double {
        var longitude = atan2(pos[1], pos[0])

        longitude = Math.toDegrees(longitude)
        return longitude
    }

    private fun calculateLatitude(pos: Array<Double>): Double {
        val r = sqrt(pos[0].pow(2) + pos[1].pow(2))
        val er2 = (a.pow(2) - b.pow(2))/b.pow(2)
        val F = 54 * b.pow(2) * pos[2].pow(2)
        val G = r.pow(2) + (1-eccSquared)*pos[2].pow(2)-eccSquared*(a.pow(2) - b.pow(2))
        val c = (eccSquared.pow(2)*F*r.pow(2))/G.pow(3)
        val s = (1+c+sqrt(c.pow(2)+2*c)).pow(0.333333)
        val P = F/(3*(s+1+1/s)*G.pow(2))
        val Q = sqrt(1+2*eccSquared.pow(2)*P)
        val r0 = -(P*eccSquared*r)/(1+Q)+sqrt(0.5*a.pow(2)*(1+1/Q)-(P*(1-eccSquared)*pos[2])/(Q*(1+Q)-0.5*P*r.pow(2)))
        val U = sqrt((r-eccSquared*r0).pow(2)+pos[2].pow(2))
        val V = sqrt((r-eccSquared*r0).pow(2)+(1-eccSquared)*pos[2].pow(2))
        val z0 = (b.pow(2)*pos[2])/(a*V)

        val h = U*(1-b.pow(2)/(a*V))
        val ratio = (pos[2]+er2*z0)/r
        var latitude = atan(ratio)

        latitude = Math.toDegrees(latitude)

        return latitude
    }

    private fun getSatellitePositionNormalized(satellite: String, epoch: Double): Array<Double> {
        sdp4.NoradByName(StringReader(tle), satellite)
        sdp4.GetPosVel(epoch)

        // Set normalization factor to avoid disappearing to infinity in latitude calculations.
        var factor: Double = 1 / abs(sdp4.itsR.min()!!)

        // Increase minimum?
        factor *= 1000000

        // Flip the x and y axis as the library returns it backwards from the canonical ECEF system
        // The units are in Gm
        val xScale = -factor
        val yScale = -factor
        val zScale = factor

        val pos: Array<Double> = arrayOf(sdp4.itsR[0] * xScale, sdp4.itsR[1] * yScale, sdp4.itsR[2] * zScale)
        return pos
    }

    private fun getJulianDate(): Double {
        /* Get the milliseconds since UT 1970-01-01T00:00:00 from system clock.
         * Convert to days then to JD. */

        /* This was taken from the Times class, and manual checking against his comments
         * in that file verifies this is accurate (unless his comments aren't :/) */
        val julianDate = System.currentTimeMillis().toDouble() / 86400000.0 + 587.5 - 10000.0
        return julianDate
    }

}