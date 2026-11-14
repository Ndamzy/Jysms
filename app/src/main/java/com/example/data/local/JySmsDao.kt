package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JySmsDao {
    @Query("SELECT * FROM cached_courses WHERE isArchived = 0 ORDER BY code ASC")
    fun observeCourses(): Flow<List<CachedCourseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourses(items: List<CachedCourseEntity>)

    @Query("SELECT * FROM cached_enrolments WHERE userId = :uid")
    fun observeEnrolmentsForUser(uid: String): Flow<List<CachedEnrolmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEnrolments(items: List<CachedEnrolmentEntity>)

    @Query("SELECT * FROM cached_attendance WHERE userId = :uid ORDER BY dateIso DESC")
    fun observeAttendanceForUser(uid: String): Flow<List<CachedAttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendance(items: List<CachedAttendanceEntity>)

    @Query("SELECT * FROM cached_results WHERE userId = :uid")
    fun observeResultsForUser(uid: String): Flow<List<CachedResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResults(items: List<CachedResultEntity>)

    @Query("SELECT * FROM cached_announcements WHERE isPublished = 1")
    fun observeAnnouncements(): Flow<List<CachedAnnouncementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnouncements(items: List<CachedAnnouncementEntity>)
}
