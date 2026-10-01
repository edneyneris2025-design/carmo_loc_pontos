package com.example.data.repository

import com.example.data.db.SurveyDao
import com.example.data.model.SurveyPoint
import com.example.data.model.SurveyProject
import com.example.data.model.TrackPoint
import com.example.util.CoordinateUtils
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class SurveyRepository(private val surveyDao: SurveyDao) {

    val allProjects: Flow<List<SurveyProject>> = surveyDao.getAllProjects()
    val unsyncedCount: Flow<Int> = surveyDao.getUnsyncedProjectCount()

    fun getProject(projectId: Long): Flow<SurveyProject?> = surveyDao.getProjectById(projectId)

    suspend fun getProjectSync(projectId: Long): SurveyProject? = surveyDao.getProjectByIdSync(projectId)

    suspend fun createProject(name: String, description: String = "", utmZone: Int = 23, hemisphere: Char = 'S'): Long {
        val project = SurveyProject(
            name = name,
            description = description,
            utmZone = utmZone,
            hemisphere = hemisphere
        )
        return surveyDao.insertProject(project)
    }

    suspend fun updateProject(project: SurveyProject) {
        surveyDao.updateProject(project.copy(isSynced = false))
    }

    suspend fun deleteProject(project: SurveyProject) {
        surveyDao.deleteProject(project)
    }

    suspend fun markProjectSynced(projectId: Long) {
        surveyDao.markProjectSynced(projectId)
    }

    fun getPoints(projectId: Long): Flow<List<SurveyPoint>> = surveyDao.getPointsForProject(projectId)

    suspend fun getPointsSync(projectId: Long): List<SurveyPoint> = surveyDao.getPointsForProjectSync(projectId)

    suspend fun addPoint(
        projectId: Long,
        pointName: String?,
        code: String,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        accuracy: Float,
        notes: String = ""
    ): Long {
        val currentMax = surveyDao.getMaxSequenceNumber(projectId) ?: 0
        val nextSeq = currentMax + 1
        val finalName = if (!pointName.isNullOrBlank()) pointName else String.format(Locale.US, "P%03d", nextSeq)

        val project = surveyDao.getProjectByIdSync(projectId)
        val utm = CoordinateUtils.toUtm(latitude, longitude, forcedZone = project?.utmZone)

        val point = SurveyPoint(
            projectId = projectId,
            sequenceNumber = nextSeq,
            pointName = finalName,
            code = code,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy,
            utmZone = utm.zone,
            hemisphere = utm.hemisphere,
            easting = utm.easting,
            northing = utm.northing,
            notes = notes
        )
        return surveyDao.insertPoint(point)
    }

    suspend fun addManualPoint(
        projectId: Long,
        name: String,
        code: String,
        easting: Double,
        northing: Double,
        altitude: Double,
        utmZone: Int,
        hemisphere: Char,
        notes: String = ""
    ): Long {
        val currentMax = surveyDao.getMaxSequenceNumber(projectId) ?: 0
        val nextSeq = currentMax + 1
        val finalName = if (name.isNotBlank()) name else String.format(Locale.US, "P%03d", nextSeq)

        val utm = com.example.util.UtmCoordinate(
            zone = utmZone,
            hemisphere = hemisphere,
            easting = easting,
            northing = northing
        )
        val latLon = CoordinateUtils.toLatLon(utm)

        val point = SurveyPoint(
            projectId = projectId,
            sequenceNumber = nextSeq,
            pointName = finalName,
            code = code,
            latitude = latLon.latitude,
            longitude = latLon.longitude,
            altitude = altitude,
            accuracy = 0.0f,
            utmZone = utmZone,
            hemisphere = hemisphere,
            easting = easting,
            northing = northing,
            notes = notes
        )
        return surveyDao.insertPoint(point)
    }

    suspend fun updatePoint(point: SurveyPoint) {
        surveyDao.updatePoint(point)
    }

    suspend fun deletePoint(point: SurveyPoint) {
        surveyDao.deletePoint(point)
    }

    suspend fun clearPoints(projectId: Long) {
        surveyDao.clearPointsForProject(projectId)
    }

    fun getTrajectory(projectId: Long): Flow<List<TrackPoint>> = surveyDao.getTrackPointsForProject(projectId)

    suspend fun getTrajectorySync(projectId: Long): List<TrackPoint> = surveyDao.getTrackPointsForProjectSync(projectId)

    suspend fun addTrackPoint(
        projectId: Long,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        accuracy: Float
    ): Long {
        val project = surveyDao.getProjectByIdSync(projectId)
        val utm = CoordinateUtils.toUtm(latitude, longitude, forcedZone = project?.utmZone)
        val trackPoint = TrackPoint(
            projectId = projectId,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy,
            easting = utm.easting,
            northing = utm.northing
        )
        return surveyDao.insertTrackPoint(trackPoint)
    }

    suspend fun clearTrajectory(projectId: Long) {
        surveyDao.clearTrackForProject(projectId)
    }
}
