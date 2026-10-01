package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.SurveyPoint
import com.example.data.model.SurveyProject
import com.example.data.model.TrackPoint

@Database(
    entities = [
        SurveyProject::class,
        SurveyPoint::class,
        TrackPoint::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun surveyDao(): SurveyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "topoutm_survey_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
