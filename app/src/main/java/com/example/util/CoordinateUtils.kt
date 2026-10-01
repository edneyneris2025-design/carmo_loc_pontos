package com.example.util

import java.util.Locale
import kotlin.math.*

data class UtmCoordinate(
    val zone: Int,
    val hemisphere: Char, // 'N' or 'S'
    val easting: Double,  // X (meters)
    val northing: Double, // Y (meters)
    val convergence: Double = 0.0, // Meridian convergence (degrees)
    val scaleFactor: Double = 0.9996
) {
    fun formattedEasting(): String = String.format(Locale.US, "%.3f m", easting)
    fun formattedNorthing(): String = String.format(Locale.US, "%.3f m", northing)
    fun shortZoneString(): String = "$zone$hemisphere"
    fun fullString(): String = "UTM $zone$hemisphere E: ${String.format(Locale.US, "%.2f", easting)} N: ${String.format(Locale.US, "%.2f", northing)}"
}

data class LatLon(
    val latitude: Double,
    val longitude: Double
) {
    fun toDmsLatitude(): String = formatDms(latitude, isLatitude = true)
    fun toDmsLongitude(): String = formatDms(longitude, isLatitude = false)

    private fun formatDms(deg: Double, isLatitude: Boolean): String {
        val hemisphere = if (isLatitude) {
            if (deg >= 0) "N" else "S"
        } else {
            if (deg >= 0) "E" else "W"
        }
        val absDeg = abs(deg)
        val d = absDeg.toInt()
        val m = ((absDeg - d) * 60).toInt()
        val s = (absDeg - d - m / 60.0) * 3600.0
        return String.format(Locale.US, "%d°%02d'%05.2f\" %s", d, m, s, hemisphere)
    }
}

object CoordinateUtils {

    // WGS-84 / SIRGAS 2000 ellipsoid constants
    private const val A = 6378137.0 // Semi-major axis (meters)
    private const val F = 1.0 / 298.257223563 // Flattening
    private const val B = A * (1.0 - F) // Semi-minor axis (~6356752.3142 m)
    private const val E2 = 2 * F - F * F // First eccentricity squared (~0.00669438)
    private const val E_PRIME2 = E2 / (1.0 - E2) // Second eccentricity squared (~0.00673950)
    private const val K0 = 0.9996 // Central meridian scale factor
    private const val FALSE_EASTING = 500000.0
    private const val FALSE_NORTHING_SOUTH = 10000000.0

    /**
     * Calculates the UTM zone from longitude in degrees (-180 to 180)
     */
    fun calculateZone(longitude: Double): Int {
        var lon = longitude
        if (lon == 180.0) lon = 179.999999
        return (floor((lon + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)
    }

    /**
     * Converts WGS84/SIRGAS2000 Latitude and Longitude to UTM
     */
    fun toUtm(latitude: Double, longitude: Double, forcedZone: Int? = null): UtmCoordinate {
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)

        val zone = forcedZone ?: calculateZone(longitude)
        val hemisphere = if (latitude >= 0) 'N' else 'S'

        // Central meridian in degrees and radians
        val centralMeridianDeg = (zone - 1) * 6 - 180 + 3
        val centralMeridianRad = Math.toRadians(centralMeridianDeg.toDouble())

        val deltaLon = lonRad - centralMeridianRad

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val tanLat = tan(latRad)

        // Radius of curvature in the prime vertical
        val n = A / sqrt(1.0 - E2 * sinLat * sinLat)
        val t = tanLat * tanLat
        val c = E_PRIME2 * cosLat * cosLat
        val aCoeff = cosLat * deltaLon

        // Meridional arc length M
        val m = A * (
                (1.0 - E2 / 4.0 - 3.0 * E2 * E2 / 64.0 - 5.0 * E2 * E2 * E2 / 256.0) * latRad
                        - (3.0 * E2 / 8.0 + 3.0 * E2 * E2 / 32.0 + 45.0 * E2 * E2 * E2 / 1024.0) * sin(2.0 * latRad)
                        + (15.0 * E2 * E2 / 256.0 + 45.0 * E2 * E2 * E2 / 1024.0) * sin(4.0 * latRad)
                        - (35.0 * E2 * E2 * E2 / 3072.0) * sin(6.0 * latRad)
                )

        // Easting calculation
        val a3 = aCoeff * aCoeff * aCoeff
        val a5 = a3 * aCoeff * aCoeff
        var easting = FALSE_EASTING + K0 * n * (
                aCoeff
                        + (1.0 - t + c) * a3 / 6.0
                        + (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * E_PRIME2) * a5 / 120.0
                )

        // Northing calculation
        val a2 = aCoeff * aCoeff
        val a4 = a2 * a2
        val a6 = a4 * a2
        var northing = K0 * (
                m + n * tanLat * (
                        a2 / 2.0
                                + (5.0 - t + 9.0 * c + 4.0 * c * c) * a4 / 24.0
                                + (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * E_PRIME2) * a6 / 720.0
                        )
                )

        if (hemisphere == 'S') {
            northing += FALSE_NORTHING_SOUTH
        }

        // Meridian convergence in degrees
        val convergenceRad = deltaLon * sinLat
        val convergenceDeg = Math.toDegrees(convergenceRad)

        // Point scale factor
        val scaleFactor = K0 * (1.0 + (1.0 + c) * a2 / 2.0 + (5.0 - 4.0 * t + 42.0 * c + 13.0 * c * c - 28.0 * E_PRIME2) * a4 / 24.0)

        return UtmCoordinate(
            zone = zone,
            hemisphere = hemisphere,
            easting = easting,
            northing = northing,
            convergence = convergenceDeg,
            scaleFactor = scaleFactor
        )
    }

    /**
     * Converts UTM coordinates back to Latitude and Longitude
     */
    fun toLatLon(utm: UtmCoordinate): LatLon {
        var x = utm.easting - FALSE_EASTING
        var y = utm.northing
        if (utm.hemisphere == 'S') {
            y -= FALSE_NORTHING_SOUTH
        }

        val e1 = (1.0 - sqrt(1.0 - E2)) / (1.0 + sqrt(1.0 - E2))
        val m = y / K0
        val mu = m / (A * (1.0 - E2 / 4.0 - 3.0 * E2 * E2 / 64.0 - 5.0 * E2 * E2 * E2 / 256.0))

        val phi1 = mu +
                (3.0 * e1 / 2.0 - 27.0 * e1 * e1 * e1 / 32.0) * sin(2.0 * mu) +
                (21.0 * e1 * e1 / 16.0 - 55.0 * e1 * e1 * e1 * e1 / 32.0) * sin(4.0 * mu) +
                (151.0 * e1 * e1 * e1 / 96.0) * sin(6.0 * mu) +
                (1097.0 * e1 * e1 * e1 * e1 / 512.0) * sin(8.0 * mu)

        val sinPhi1 = sin(phi1)
        val cosPhi1 = cos(phi1)
        val tanPhi1 = tan(phi1)

        val c1 = E_PRIME2 * cosPhi1 * cosPhi1
        val t1 = tanPhi1 * tanPhi1
        val n1 = A / sqrt(1.0 - E2 * sinPhi1 * sinPhi1)
        val r1 = A * (1.0 - E2) / ((1.0 - E2 * sinPhi1 * sinPhi1).pow(1.5))
        val d = x / (n1 * K0)

        val d2 = d * d
        val d3 = d2 * d
        val d4 = d2 * d2
        val d5 = d4 * d
        val d6 = d3 * d3

        val latRad = phi1 - (n1 * tanPhi1 / r1) * (
                d2 / 2.0 -
                        (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * E_PRIME2) * d4 / 24.0 +
                        (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * E_PRIME2 - 3.0 * c1 * c1) * d6 / 720.0
                )

        val lonRad = (
                d -
                        (1.0 + 2.0 * t1 + c1) * d3 / 6.0 +
                        (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * E_PRIME2 + 24.0 * t1 * t1) * d5 / 120.0
                ) / cosPhi1

        val centralMeridianDeg = (utm.zone - 1) * 6 - 180 + 3
        val latDeg = Math.toDegrees(latRad)
        val lonDeg = centralMeridianDeg + Math.toDegrees(lonRad)

        return LatLon(latDeg, lonDeg)
    }

    /**
     * Distance in meters between two UTM points
     */
    fun distance(e1: Double, n1: Double, e2: Double, n2: Double): Double {
        val dx = e2 - e1
        val dy = n2 - n1
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Azimuth in degrees from point 1 to point 2 (0° North, 90° East)
     */
    fun azimuth(e1: Double, n1: Double, e2: Double, n2: Double): Double {
        val dx = e2 - e1
        val dy = n2 - n1
        val rad = atan2(dx, dy)
        var deg = Math.toDegrees(rad)
        if (deg < 0) deg += 360.0
        return deg
    }

    /**
     * Calculates polygon area in square meters using Shoelace formula on UTM coordinates
     */
    fun calculatePolygonArea(points: List<Pair<Double, Double>>): Double {
        if (points.size < 3) return 0.0
        var area = 0.0
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[i].first * points[j].second
            area -= points[j].first * points[i].second
        }
        return abs(area) / 2.0
    }

    /**
     * Calculates polygon perimeter in meters
     */
    fun calculatePolygonPerimeter(points: List<Pair<Double, Double>>): Double {
        if (points.size < 2) return 0.0
        var perimeter = 0.0
        for (i in 0 until points.size - 1) {
            perimeter += distance(points[i].first, points[i].second, points[i + 1].first, points[i + 1].second)
        }
        // if closed:
        if (points.size >= 3) {
            perimeter += distance(points.last().first, points.last().second, points.first().first, points.first().second)
        }
        return perimeter
    }

    /**
     * Formats area in m² and Hectares
     */
    fun formatArea(areaM2: Double): String {
        return if (areaM2 >= 10000.0) {
            val ha = areaM2 / 10000.0
            String.format(Locale.US, "%.2f ha (%.1f m²)", ha, areaM2)
        } else {
            String.format(Locale.US, "%.2f m²", areaM2)
        }
    }
}
