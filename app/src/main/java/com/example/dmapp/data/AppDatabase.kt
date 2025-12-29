// ВРЕМЕННО УДАЛЕНО ДЛЯ СБРОСА ROOM
// package com.example.dmapp.data
// ... (весь остальной код закомментирован)

package com.example.dmapp.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDateTime

@Database(entities = [Order::class, StatisticsOrder::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun statisticsOrderDao(): StatisticsOrderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "courier_app_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE orders ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Создаем новую таблицу для статистики заказов
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS statistics_orders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        orderNumber INTEGER NOT NULL,
                        externalOrderNumber TEXT NOT NULL,
                        deliveryAddress TEXT NOT NULL,
                        clientName TEXT NOT NULL,
                        clientPhone TEXT NOT NULL,
                        deliveryTimeStart TEXT NOT NULL,
                        deliveryTimeEnd TEXT NOT NULL,
                        weight REAL NOT NULL,
                        orderAmount REAL NOT NULL,
                        completionDate TEXT NOT NULL
                    )
                """)
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE orders ADD COLUMN photoUri TEXT")
            }
        }
        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE orders ADD COLUMN photoDateTime TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE statistics_orders ADD COLUMN photoUri TEXT")
                database.execSQL("ALTER TABLE statistics_orders ADD COLUMN photoDateTime TEXT")
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): String? {
        return date?.toString()
    }

    @TypeConverter
    fun fromOrderStatus(value: OrderStatus): String {
        return value.name
    }

    @TypeConverter
    fun toOrderStatus(value: String): OrderStatus {
        return OrderStatus.valueOf(value)
    }
} 