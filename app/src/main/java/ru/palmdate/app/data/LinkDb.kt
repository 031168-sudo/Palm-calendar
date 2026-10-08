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

/** Итог конкретного раза события (у повторяющихся — у каждого раза свой). */
@Entity(tableName = "outcomes", primaryKeys = ["eventId", "instanceStart"])
data class OutcomeRow(
    val eventId: Long,
    val instanceStart: Long,
    val status: String,
    val note: String?,
)

@Dao
interface LinkDao {
    // Для резервной копии
    @Query("SELECT * FROM links") suspend fun allLinks(): List<EventLink>
    @Query("SELECT * FROM outcomes") suspend fun allOutcomes(): List<OutcomeRow>
    @Query("SELECT * FROM contact_phone") suspend fun allPhones(): List<ContactPhone>

    @Query("SELECT * FROM outcomes WHERE eventId IN (:ids)")
    suspend fun outcomes(ids: List<Long>): List<OutcomeRow>

    @Upsert
    suspend fun setOutcome(o: OutcomeRow)

    @Query("DELETE FROM outcomes WHERE eventId = :eventId AND instanceStart = :instanceStart")
    suspend fun clearOutcome(eventId: Long, instanceStart: Long)

    @Query("DELETE FROM outcomes WHERE eventId = :eventId")
    suspend fun clearOutcomes(eventId: Long)

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

/** 2 → 3: добавилась таблица итогов; связи и выбранные номера сохраняются. */
private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `outcomes` (`eventId` INTEGER NOT NULL, `instanceStart` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, `note` TEXT, PRIMARY KEY(`eventId`, `instanceStart`))",
        )
    }
}

@Database(entities = [EventLink::class, ContactPhone::class, OutcomeRow::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun links(): LinkDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "palmdate.db")
                // Связи восстанавливаются из меток в описании событий, поэтому при смене схемы можно пересоздать
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
