package ru.palmdate.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

/**
 * "Палмовская" часть события: тип и контакт.
 * Само событие живёт в системном календаре (и синхронизируется с Google),
 * а здесь — связь eventId → тип + контакт.
 */
@Entity(tableName = "links")
data class EventLink(
    @PrimaryKey val eventId: Long,
    val type: String,
    val lookupKey: String?,
)

@Dao
interface LinkDao {
    @Query("SELECT * FROM links WHERE eventId IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<EventLink>

    @Query("SELECT * FROM links WHERE lookupKey = :lookupKey")
    suspend fun byContact(lookupKey: String): List<EventLink>

    @Upsert
    suspend fun upsert(link: EventLink)

    @Query("DELETE FROM links WHERE eventId = :eventId")
    suspend fun delete(eventId: Long)
}

@Database(entities = [EventLink::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun links(): LinkDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "palmdate.db")
                .build().also { instance = it }
        }
    }
}
