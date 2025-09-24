package com.stevedaydream.tenantapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters // <<< ---【*** 新增 import ***】---

@Database(
    entities = [
        RepairReport::class,
        RoomEntity::class,
        ElectricMeterRecord::class,
        Announcement::class,
        User::class,
        Payment::class,
        RoomChangeRequest::class
    ],
    version = 31,
    exportSchema = false
)
@TypeConverters(Converters::class) // <<< ---【*** 在此處加上這行註解 ***】---
abstract class AppDatabase : RoomDatabase() {
    abstract fun repairReportDao(): RepairReportDao
    abstract fun roomDao(): RoomDao
    abstract fun electricMeterDao(): ElectricMeterDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun userDao(): UserDao
    abstract fun paymentDao(): PaymentDao
    abstract fun roomChangeRequestDao(): RoomChangeRequestDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tenant_app_db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}