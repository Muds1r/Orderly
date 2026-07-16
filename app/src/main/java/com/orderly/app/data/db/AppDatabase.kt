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
    entities = [
        OrderEntity::class,
        ProcessedMessageEntity::class,
        TrackingEventEntity::class,
        ExcludedOrderEntity::class
    ],
    version = 5,
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE orders ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN watched INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN lastLiveCheckAt INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_hidden ON orders(hidden)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_watched ON orders(watched)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS excluded_orders (
                        exclusionKey TEXT NOT NULL PRIMARY KEY,
                        excludedAt INTEGER NOT NULL,
                        label TEXT
                    )
                    """.trimIndent()
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
