package com.stevedaydream.tenantapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 29, // <-- 版本升級以反映 RoomChangeRequest 的主鍵變更
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repairReportDao(): RepairReportDao
    abstract fun roomDao(): RoomDao
    abstract fun electricMeterDao(): ElectricMeterDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun userDao(): UserDao
    abstract fun paymentDao(): PaymentDao
    abstract fun roomChangeRequestDao(): RoomChangeRequestDao

    // 【*** 新增：RoomDatabase 會自動實作此方法 ***】
    // abstract fun clearAllTables() // RoomDatabase 已經內建了這個方法，我們可以直接呼叫

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tenant_app_db" // <-- 建議給資料庫一個更明確的名稱
                )
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}