package com.stevedaydream.tenantapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RepairReport::class,
        RoomEntity::class,
        ElectricMeterRecord::class,
        Announcement::class,
               ],
    version = 4, // 資料庫升級
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repairReportDao(): RepairReportDao
    abstract fun roomDao(): RoomDao
    abstract fun electricMeterDao(): ElectricMeterDao // 新增
    abstract fun announcementDao(): AnnouncementDao   // ←加這行

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "repair_report_db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}