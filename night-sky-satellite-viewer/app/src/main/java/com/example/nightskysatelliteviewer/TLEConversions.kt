import com.example.nightskysatelliteviewer.sdp4.SDP4
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sqrt

class TLEConversion {
    private val a = 6378.0
    private val b = 6357.0
    private val ecc_squared = 1 - Math.pow(b / a, 2.0)

    private val sdp4 = SDP4()
    private val fileName: String

    constructor(fileName: String) {
        sdp4.Init()
        this.fileName = fileName
    }

    // Source for the math for the following functions:
    // https://en.wikipedia.org/wiki/Geographic_coordinate_conversion
    // (basically the cliffnotes extracted from a few key papers on the subject)
    fun getLongitude(satellite: String): Double {
        val pos = getSatellitePosition(satellite)
        val longitude = atan(pos[1] / pos[0])
        return Math.toDegrees(longitude)
    }

    fun getLatitude(satellite: String): Double {
        val pos = getSatellitePosition(satellite)
        val thresh = 0.1
        var prev = 1.0 / (1 - ecc_squared) // Initial estimate
        var latitude = iterateLatitude(pos, prev)
        while (Math.abs(latitude - prev) >= thresh) {
            prev = latitude
            latitude = iterateLatitude(pos, prev)
        }
        return Math.toDegrees(latitude)
    }

    private fun iterateLatitude(pos: DoubleArray, prev: Double): Double {
        val p_squared = sqrt(
            pos[0].pow(2.0) + pos[1].pow(2.0)
        ).pow(2.0)
        var c_prev =
            (p_squared + (1 - ecc_squared) * pos[2].pow(2.0) * prev.pow(2.0)).pow(3.0 / 2.0)
        c_prev /= a * ecc_squared
        return 1 + (p_squared + (1 - ecc_squared) * pos[2].pow(2.0) * prev.pow(3.0)) / (c_prev - p_squared)
    }

    private fun getSatellitePosition(satellite: String): DoubleArray {
        sdp4.NoradByName(fileName, satellite)
        sdp4.GetPosVel(getJulianDate())
        return sdp4.itsR
    }

    private fun getJulianDate(): Double {
        /* Get the milliseconds since UT 1970-01-01T00:00:00 from system clock.
         * Convert to days then to JD. */
        return System.currentTimeMillis().toDouble() / 86400000.0 + 587.5 - 10000.0
    }
}
