package com.borhji.mushaf.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** ملف تلاوة مخزَّن لسورة واحدة */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val surahNumber: Int,
    val uri: String,
    val displayName: String,
    val source: String,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val addedAt: Long = 0L,
)

/** موضع الاستماع المحفوظ لسورة */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val surahNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY surahNumber ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY surahNumber ASC")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE surahNumber = :surah LIMIT 1")
    suspend fun get(surah: Int): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE surahNumber = :surah")
    suspend fun delete(surah: Int)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress")
    suspend fun getAll(): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE surahNumber = :surah LIMIT 1")
    suspend fun get(surah: Int): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProgressEntity)

    @Query("DELETE FROM progress WHERE surahNumber = :surah")
    suspend fun delete(surah: Int)

    @Query("DELETE FROM progress")
    suspend fun clear()
}

@Database(
    entities = [TrackEntity::class, ProgressEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MushafDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun progressDao(): ProgressDao

    companion object {
        private const val DB_NAME = "mushaf.db"

        fun build(context: Context): MushafDatabase =
            Room.databaseBuilder(context.applicationContext, MushafDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
