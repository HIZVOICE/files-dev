package com.hyperfiles.manager.data

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

/**
 * Room database — the persistence foundation for the Compose/Room modernization.
 * Starts with a Favorites table; prefs-backed stores (resume, recycle-bin index,
 * secure folder) will migrate here screen by screen.
 */
@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val path: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun all(): Flow<List<Favorite>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    suspend fun isFavorite(path: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun remove(path: String)
}

/** Recycle-bin index (moved off SharedPreferences). Keyed by the file's name inside the trash dir. */
@Entity(tableName = "trash")
data class TrashEntry(
    @PrimaryKey val trashName: String,
    val originalPath: String,
    val deletedAt: Long
)

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash")
    fun all(): List<TrashEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: TrashEntry)

    @Query("DELETE FROM trash WHERE trashName = :name")
    fun delete(name: String)

    @Query("DELETE FROM trash")
    fun clear()
}

@Database(entities = [Favorite::class, TrashEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun trashDao(): TrashDao
}

object Db {
    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "filesdev.db"
            ).fallbackToDestructiveMigration()
                .allowMainThreadQueries()   // tables are tiny (trash index, favorites)
                .build().also { instance = it }
        }
}
