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
        User::class,
        Payment::class,
        RoomChangeRequest::class // <-- 新增 Entity
    ],
    version = 17, // <-- 版本升級
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // ... (其餘部分保持不變)
    abstract fun repairReportDao(): RepairReportDao
    abstract fun roomDao(): RoomDao
    abstract fun electricMeterDao(): ElectricMeterDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun userDao(): UserDao
    abstract fun paymentDao(): PaymentDao
    abstract fun roomChangeRequestDao(): RoomChangeRequestDao // <-- 新增 DAO


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