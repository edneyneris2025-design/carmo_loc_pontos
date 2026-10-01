package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.example.util.CoordinateUtils
import com.example.util.UtmCoordinate
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class GpsStatus(
    val hasFix: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val bearing: Float = 0.0f,
    val speed: Float = 0.0f,
    val utm: UtmCoordinate = UtmCoordinate(23, 'S', 0.0, 0.0),
    val timestamp: Long = 0L,
    val isTrackingActive: Boolean = false,
    val totalTrackDistance: Double = 0.0,
    val trackPointCount: Int = 0,
    val provider: String = "GPS"
) {
    val accuracyQuality: String
        get() = when {
            accuracy <= 0.0f -> "Aguardando GPS..."
            accuracy < 3.0f -> "Excelente (±${String.format("%.1f", accuracy)}m)"
            accuracy < 6.0f -> "Boa (±${String.format("%.1f", accuracy)}m)"
            accuracy < 12.0f -> "Moderada (±${String.format("%.1f", accuracy)}m)"
            else -> "Baixa (±${String.format("%.1f", accuracy)}m)"
        }
}

data class AveragingSession(
    val isActive: Boolean = false,
    val targetSamples: Int = 10,
    val collectedSamples: Int = 0,
    val samples: List<Location> = emptyList(),
    val meanLatitude: Double = 0.0,
    val meanLongitude: Double = 0.0,
    val meanAltitude: Double = 0.0,
    val horizontalStdDev: Double = 0.0,
    val utm: UtmCoordinate = UtmCoordinate(23, 'S', 0.0, 0.0)
)

class GpsManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val systemLocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _gpsStatus = MutableStateFlow(GpsStatus())
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    private val _averagingSession = MutableStateFlow(AveragingSession())
    val averagingSession: StateFlow<AveragingSession> = _averagingSession.asStateFlow()

    private var fusedCallback: LocationCallback? = null
    private var systemListener: LocationListener? = null

    private var lastRecordedLocation: Location? = null
    private var totalDistance: Double = 0.0
    private var isRecordingTrajectory: Boolean = false
    private var trajectoryListener: ((Location, UtmCoordinate) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(onTrajectoryPoint: ((Location, UtmCoordinate) -> Unit)? = null) {
        trajectoryListener = onTrajectoryPoint

        // 1. Setup Fused Location Provider
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0.5f)
            .build()

        fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { handleNewLocation(it) }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, fusedCallback!!, Looper.getMainLooper())
        } catch (_: Exception) {
            // Fallback to system location manager
        }

        // 2. Setup System GPS Provider as resilient fallback for offline deep rural areas
        systemListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                handleNewLocation(loc)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            if (systemLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                systemLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.5f,
                    systemListener!!,
                    Looper.getMainLooper()
                )
            }
        } catch (_: Exception) {}
    }

    fun stopLocationUpdates() {
        fusedCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        systemListener?.let {
            try {
                systemLocationManager.removeUpdates(it)
            } catch (_: Exception) {}
        }
    }

    fun setTrajectoryRecording(enabled: Boolean) {
        isRecordingTrajectory = enabled
        _gpsStatus.value = _gpsStatus.value.copy(isTrackingActive = enabled)
    }

    fun resetTrajectoryStats() {
        totalDistance = 0.0
        lastRecordedLocation = null
        _gpsStatus.value = _gpsStatus.value.copy(totalTrackDistance = 0.0, trackPointCount = 0)
    }

    private fun handleNewLocation(location: Location) {
        val utm = CoordinateUtils.toUtm(location.latitude, location.longitude)

        // Trajectory distance computation & auto recording
        if (isRecordingTrajectory) {
            val lastLoc = lastRecordedLocation
            if (lastLoc != null) {
                val d = lastLoc.distanceTo(location).toDouble()
                if (d >= 1.5) { // Minimum 1.5m movement threshold for clean track
                    totalDistance += d
                    lastRecordedLocation = location
                    val currentCount = _gpsStatus.value.trackPointCount + 1
                    _gpsStatus.value = _gpsStatus.value.copy(
                        totalTrackDistance = totalDistance,
                        trackPointCount = currentCount
                    )
                    trajectoryListener?.invoke(location, utm)
                }
            } else {
                lastRecordedLocation = location
                val currentCount = _gpsStatus.value.trackPointCount + 1
                _gpsStatus.value = _gpsStatus.value.copy(
                    totalTrackDistance = totalDistance,
                    trackPointCount = currentCount
                )
                trajectoryListener?.invoke(location, utm)
            }
        }

        _gpsStatus.value = _gpsStatus.value.copy(
            hasFix = true,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            bearing = location.bearing,
            speed = location.speed,
            utm = utm,
            timestamp = location.time,
            provider = location.provider ?: "GPS"
        )

        // Handle averaging session if active
        val currentSession = _averagingSession.value
        if (currentSession.isActive && currentSession.collectedSamples < currentSession.targetSamples) {
            val newSamples = currentSession.samples + location
            val count = newSamples.size
            val meanLat = newSamples.map { it.latitude }.average()
            val meanLon = newSamples.map { it.longitude }.average()
            val meanAlt = newSamples.map { it.altitude }.average()

            // Calculate standard deviation in meters
            var sumDistSq = 0.0
            val meanLoc = Location("mean").apply {
                latitude = meanLat
                longitude = meanLon
            }
            for (s in newSamples) {
                val dist = s.distanceTo(meanLoc).toDouble()
                sumDistSq += dist * dist
            }
            val stdDev = if (count > 1) sqrt(sumDistSq / (count - 1)) else location.accuracy.toDouble()
            val avgUtm = CoordinateUtils.toUtm(meanLat, meanLon)

            _averagingSession.value = currentSession.copy(
                collectedSamples = count,
                samples = newSamples,
                meanLatitude = meanLat,
                meanLongitude = meanLon,
                meanAltitude = meanAlt,
                horizontalStdDev = stdDev,
                utm = avgUtm
            )
        }
    }

    fun startAveraging(targetSamples: Int = 15) {
        _averagingSession.value = AveragingSession(
            isActive = true,
            targetSamples = targetSamples,
            collectedSamples = 0,
            samples = emptyList()
        )
    }

    fun cancelAveraging() {
        _averagingSession.value = AveragingSession(isActive = false)
    }
}
