package com.orderly.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromOrderStatus(status: OrderStatus): String = status.name

    @TypeConverter
    fun toOrderStatus(value: String): OrderStatus =
        runCatching { OrderStatus.valueOf(value) }.getOrDefault(OrderStatus.UNKNOWN)
}

@Database(
    entities = [OrderEntity::class, ProcessedMessageEntity::class, TrackingEventEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN shipFrom TEXT")
                db.execSQL("ALTER TABLE orders ADD COLUMN lastLocation TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tracking_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        orderId TEXT NOT NULL,
                        occurredAt INTEGER NOT NULL,
                        status TEXT,
                        location TEXT,
                        description TEXT NOT NULL,
                        source TEXT NOT NULL,
                        fingerprint TEXT NOT NULL,
                        messageId TEXT,
                        FOREIGN KEY(orderId) REFERENCES orders(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracking_events_orderId ON tracking_events(orderId)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tracking_events_orderId_fingerprint " +
                        "ON tracking_events(orderId, fingerprint)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_orders_trackingNumber ON orders(trackingNumber)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "orderly.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
