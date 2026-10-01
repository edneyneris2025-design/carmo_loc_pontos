package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "survey_projects")
data class SurveyProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val utmZone: Int = 23,
    val hemisphere: Char = 'S',
    val datum: String = "SIRGAS 2000 / WGS 84",
    val isSynced: Boolean = false,
    val lastSyncedAt: Long? = null
)

@Entity(
    tableName = "survey_points",
    foreignKeys = [
        ForeignKey(
            entity = SurveyProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index("sequenceNumber")
    ]
)
data class SurveyPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val sequenceNumber: Int,
    val pointName: String,
    val code: String = "Vértice",
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val utmZone: Int,
    val hemisphere: Char,
    val easting: Double,
    val northing: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = SurveyProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val easting: Double,
    val northing: Double,
    val timestamp: Long = System.currentTimeMillis()
)
