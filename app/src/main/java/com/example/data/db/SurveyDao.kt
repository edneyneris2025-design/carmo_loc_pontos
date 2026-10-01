package com.example.data.db

import androidx.room.*
import com.example.data.model.SurveyPoint
import com.example.data.model.SurveyProject
import com.example.data.model.TrackPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyDao {

    // Projects
    @Query("SELECT * FROM survey_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<SurveyProject>>

    @Query("SELECT * FROM survey_projects WHERE id = :id")
    fun getProjectById(id: Long): Flow<SurveyProject?>

    @Query("SELECT * FROM survey_projects WHERE id = :id")
    suspend fun getProjectByIdSync(id: Long): SurveyProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: SurveyProject): Long

    @Update
    suspend fun updateProject(project: SurveyProject)

    @Delete
    suspend fun deleteProject(project: SurveyProject)

    @Query("SELECT COUNT(*) FROM survey_projects WHERE isSynced = 0")
    fun getUnsyncedProjectCount(): Flow<Int>

    @Query("UPDATE survey_projects SET isSynced = 1, lastSyncedAt = :timestamp WHERE id = :projectId")
    suspend fun markProjectSynced(projectId: Long, timestamp: Long = System.currentTimeMillis())

    // Survey Points
    @Query("SELECT * FROM survey_points WHERE projectId = :projectId ORDER BY sequenceNumber ASC")
    fun getPointsForProject(projectId: Long): Flow<List<SurveyPoint>>

    @Query("SELECT * FROM survey_points WHERE projectId = :projectId ORDER BY sequenceNumber ASC")
    suspend fun getPointsForProjectSync(projectId: Long): List<SurveyPoint>

    @Query("SELECT MAX(sequenceNumber) FROM survey_points WHERE projectId = :projectId")
    suspend fun getMaxSequenceNumber(projectId: Long): Int?

    @Query("SELECT COUNT(*) FROM survey_points WHERE projectId = :projectId")
    fun getPointCountForProject(projectId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: SurveyPoint): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<SurveyPoint>)

    @Update
    suspend fun updatePoint(point: SurveyPoint)

    @Delete
    suspend fun deletePoint(point: SurveyPoint)

    @Query("DELETE FROM survey_points WHERE id = :id")
    suspend fun deletePointById(id: Long)

    @Query("DELETE FROM survey_points WHERE projectId = :projectId")
    suspend fun clearPointsForProject(projectId: Long)

    // Track Points (Trajectory)
    @Query("SELECT * FROM track_points WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun getTrackPointsForProject(projectId: Long): Flow<List<TrackPoint>>

    @Query("SELECT * FROM track_points WHERE projectId = :projectId ORDER BY timestamp ASC")
    suspend fun getTrackPointsForProjectSync(projectId: Long): List<TrackPoint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoint(trackPoint: TrackPoint): Long

    @Query("DELETE FROM track_points WHERE projectId = :projectId")
    suspend fun clearTrackForProject(projectId: Long)
}
