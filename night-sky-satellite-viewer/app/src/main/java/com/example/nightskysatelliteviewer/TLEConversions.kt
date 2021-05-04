// Source for the math for the following function:
// https://en.wikipedia.org/wiki/Geographic_coordinate_conversion
// (basically the cliffnotes extracted from a few key papers on the subject)
fun getLongitude(pos: DoubleArray): Double {
    val longitude = Math.atan(pos[1] / pos[0])
    return Math.toDegrees(longitude)
}

fun getLatitude(pos: DoubleArray): Double {
    val thresh = 0.1
    var prev = 1.0 / (1 - Math.exp(2.0)) // Initial estimate
    var latitude = iterateLatitude(pos, prev)
    println("Prev: $prev")
    println("latitude: $latitude")
    println()
    while (Math.abs(latitude - prev) >= thresh) {
        prev = latitude
        latitude = iterateLatitude(pos, prev)
        println("Prev: $prev")
        println("latitude: $latitude")
        println()
    }
    return Math.toDegrees(latitude)
}

private fun iterateLatitude(pos: DoubleArray, prev: Double): Double {
    val a = 6378.0
    val b = 6357.0
    val p_squared = Math.pow(
        Math.sqrt(
            Math.pow(
                pos[0],
                2.0
            ) + Math.pow(pos[1], 2.0)
        ), 2.0
    )
    val ecc_squared = 1 - Math.pow(b / a, 2.0)
    var c_prev = Math.pow(
        p_squared + (1 - ecc_squared) * Math.pow(
            pos[2],
            2.0
        ) * Math.pow(prev, 2.0), 3.0 / 2.0
    )
    c_prev /= a * ecc_squared
    return 1 + (p_squared + (1 - ecc_squared) * Math.pow(
        pos[2],
        2.0
    ) * Math.pow(prev, 3.0)) / (c_prev - p_squared)
}