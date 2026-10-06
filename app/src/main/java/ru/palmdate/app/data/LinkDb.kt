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
 * "Палмовская" часть события: тип, контакт и выбранный номер.
 * Само событие живёт в системном календаре (и синхронизируется с Google),
 * а здесь — связь eventId → тип + контакт + номер.
 */
@Entity(tableName = "links")
data class EventLink(
    @PrimaryKey val eventId: Long,
    val type: String,
    val lookupKey: String?,
    val phone: String? = null,
)

/** Какой номер выбирали для контакта в прошлый раз. */
@Entity(tableName = "contact_phone")
data class ContactPhone(
    @PrimaryKey val lookupKey: String,
    val number: String,
)

@Dao
interface LinkDao {
    @Query("SELECT * FROM links WHERE eventId IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<EventLink>

    @Upsert
    suspend fun upsert(link: EventLink)

    @Query("SELECT * FROM links WHERE lookupKey = :lookupKey")
    suspend fun byContact(lookupKey: String): List<EventLink>

    @Query("DELETE FROM links WHERE eventId = :eventId")
    suspend fun delete(eventId: Long)

    @Query("SELECT number FROM contact_phone WHERE lookupKey = :lookupKey")
    suspend fun rememberedPhone(lookupKey: String): String?

    @Upsert
    suspend fun rememberPhone(p: ContactPhone)
}

@Database(entities = [EventLink::class, ContactPhone::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun links(): LinkDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "palmdate.db")
                // Связи восстанавливаются из меток в описании событий, поэтому при смене схемы можно пересоздать
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
