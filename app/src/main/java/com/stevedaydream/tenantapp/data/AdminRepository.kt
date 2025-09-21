package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 用於執行管理員等級操作的 Repository。
 */
class AdminRepository(
    private val userDao: UserDao,
    private val roomDao: RoomDao,
    private val repairReportDao: RepairReportDao,
    private val announcementDao: AnnouncementDao,
    private val paymentDao: PaymentDao, // Added
    private val electricMeterDao: ElectricMeterDao, // Added
    private val roomChangeRequestDao: RoomChangeRequestDao, // Added
    private val db: AppDatabase,
    private val coroutineScope: CoroutineScope
) {

    private val firestore = FirebaseFirestore.getInstance()

    /**
     * 【*** 新增此方法 ***】
     * 從 Firestore 拉取所有集合的資料，並完全覆寫本地 Room 資料庫。
     * @return Pair<Boolean, String> - first is success, second is message
     */
    suspend fun syncAllDataFromCloud(): Pair<Boolean, String> {
        return try {
            withContext(Dispatchers.IO) {
                // 1. 從 Firestore 獲取所有資料
                val users = firestore.collection("users").get().await().toObjects<User>()
                val rooms = firestore.collection("rooms").get().await().toObjects<RoomEntity>()
                val reports = firestore.collection("repair_reports").get().await().toObjects<RepairReport>()
                val announcements = firestore.collection("announcements").get().await().toObjects<Announcement>()
                val payments = firestore.collection("payments").get().await().toObjects<Payment>()
                val records = firestore.collection("electric_meter_records").get().await().toObjects<ElectricMeterRecord>()
                val requests = firestore.collection("room_change_requests").get().await().toObjects<RoomChangeRequest>()

                // 2. 清除本地所有資料表
                db.clearAllTables()

                // 3. 將從雲端獲取的資料寫入本地資料庫
                userDao.insertOrUpdateAll(users)
                roomDao.insertRooms(rooms)
                repairReportDao.insertOrUpdateAll(reports)
                announcementDao.insertOrUpdateAll(announcements)
                paymentDao.insertOrUpdateAll(payments)
                electricMeterDao.insertOrUpdateRecords(records)
                roomChangeRequestDao.insertOrUpdateAll(requests)
            }
            Pair(true, "成功將所有雲端資料同步至本地資料庫！")
        } catch (e: Exception) {
            Log.e("AdminRepository", "Failed to sync all data from cloud", e)
            Pair(false, "同步失敗: ${e.message}")
        }
    }


    suspend fun resetLocalDatabase(): Pair<Boolean, String> {
        return try {
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
            Pair(true, "本地資料庫已成功清空！")
        } catch (e: Exception) {
            Log.e("AdminRepository", "Failed to reset local database", e)
            Pair(false, "重置本地資料庫失敗: ${e.message}")
        }
    }


    private suspend fun <T : Any> batchInsert(collectionName: String, data: List<T>, localInsert: suspend (List<T>) -> Unit): Pair<Boolean, String> {
        return try {
            val collection = firestore.collection(collectionName)
            val batch = firestore.batch()
            data.forEach { item ->
                val id = when (item) {
                    is User -> item.id
                    is RoomEntity -> item.id
                    is Announcement -> item.id
                    is RepairReport -> item.id
                    else -> UUID.randomUUID().toString()
                }
                val docId = if (id.isNotBlank()) id else UUID.randomUUID().toString()
                batch.set(collection.document(docId), item)
            }
            batch.commit().await()
            withContext(Dispatchers.IO) {
                localInsert(data)
            }
            Pair(true, "成功新增 ${data.size} 筆 $collectionName 資料到雲端與本地。")
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error batch inserting to $collectionName", e)
            Pair(false, "新增 $collectionName 資料失敗: ${e.message}")
        }
    }

    suspend fun insertTestUsers(users: List<User>): Pair<Boolean, String> {
        return batchInsert("users", users) { userDao.insertOrUpdateAll(it) }
    }

    suspend fun insertTestRooms(rooms: List<RoomEntity>): Pair<Boolean, String> {
        return batchInsert("rooms", rooms) { roomDao.insertRooms(it) }
    }

    suspend fun insertTestAnnouncements(announcements: List<Announcement>): Pair<Boolean, String> {
        return batchInsert("announcements", announcements) { announcementDao.insertOrUpdateAll(it) }
    }

    suspend fun insertTestRepairReports(reports: List<RepairReport>): Pair<Boolean, String> {
        return batchInsert("repair_reports", reports) { repairReportDao.insertOrUpdateAll(it) }
    }


    suspend fun getAllUsers(limit: Int = 0): List<User> {
        return try {
            var query = firestore.collection("users").orderBy("username")
            if (limit > 0) {
                query = query.limit(limit.toLong())
            }
            query.get().await().toObjects(User::class.java)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error getting users", e)
            emptyList()
        }
    }

    suspend fun getAllRooms(limit: Int = 0): List<RoomEntity> {
        return try {
            var query = firestore.collection("rooms").orderBy("roomNumber")
            if (limit > 0) {
                query = query.limit(limit.toLong())
            }
            query.get().await().toObjects(RoomEntity::class.java)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error getting rooms", e)
            emptyList()
        }
    }

    suspend fun getAllRepairReports(limit: Int = 0): List<RepairReport> {
        return try {
            var query = firestore.collection("repair_reports").orderBy("date", Query.Direction.DESCENDING)
            if (limit > 0) {
                query = query.limit(limit.toLong())
            }
            query.get().await().toObjects(RepairReport::class.java)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error getting repair reports", e)
            emptyList()
        }
    }

    suspend fun getAllAnnouncements(limit: Int = 0): List<Announcement> {
        return try {
            var query = firestore.collection("announcements").orderBy("date", Query.Direction.DESCENDING)
            if (limit > 0) {
                query = query.limit(limit.toLong())
            }
            query.get().await().toObjects(Announcement::class.java)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error getting announcements", e)
            emptyList()
        }
    }

    suspend fun getAllRoomChangeRequests(limit: Int = 0): List<RoomChangeRequest> {
        return try {
            var query = firestore.collection("room_change_requests").orderBy("requestDate", Query.Direction.DESCENDING)
            if (limit > 0) {
                query = query.limit(limit.toLong())
            }
            query.get().await().toObjects(RoomChangeRequest::class.java)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error getting room change requests", e)
            emptyList()
        }
    }
    suspend fun getAllPayments(limit: Int = 0): List<Payment> {
        return try {
            var query = firestore.collection("payments").orderBy("recordMonth", Query.Direction.DESCENDING)
            if (limit > 0) {
                query = query.limit(limit.toLong())
            }
            query.get().await().toObjects(Payment::class.java)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error getting payments", e)
            emptyList()
        }
    }

    suspend fun getAllElectricMeterRecords(limit: Int = 0): List<ElectricMeterRecord> {
        return try {
            var query = firestore.collection("electric_meter_records").orderBy("recordMonth", Query.Direction.DESCENDING)
            if (limit > 0) {
                query = query.limit(limit.toLong())
            }
            query.get().await().toObjects(ElectricMeterRecord::class.java)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error getting electric meter records", e)
            emptyList()
        }
    }

    suspend fun getAllLandlords(): List<User> {
        return try {
            firestore.collection("users")
                .whereEqualTo("role", "landlord")
                .get().await().toObjects(User::class.java)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error getting landlords", e)
            emptyList()
        }
    }

    suspend fun getUnassignedRooms(): List<RoomEntity> {
        return try {
            firestore.collection("rooms")
                .whereEqualTo("landlordCode", null)
                .get().await().toObjects(RoomEntity::class.java)
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error getting unassigned rooms", e)
            emptyList()
        }
    }

    suspend fun assignRoomsToLandlord(roomIds: List<String>, landlordCode: String): Boolean {
        return try {
            val batch = firestore.batch()
            val updatedRooms = mutableListOf<RoomEntity>()

            for (roomId in roomIds) {
                val roomDoc = firestore.collection("rooms").document(roomId).get().await()
                val room = roomDoc.toObject(RoomEntity::class.java)
                if(room != null) {
                    updatedRooms.add(room.copy(landlordCode = landlordCode))
                }
            }

            for (room in updatedRooms) {
                val docRef = firestore.collection("rooms").document(room.id)
                batch.set(docRef, room)
            }

            batch.commit().await()

            withContext(Dispatchers.IO) {
                roomDao.insertRooms(updatedRooms)
            }

            true
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error assigning rooms to landlord $landlordCode", e)
            false
        }
    }


    suspend fun updateUser(user: User) {
        if (user.id.isBlank()) throw IllegalArgumentException("User ID cannot be blank for an update.")
        firestore.collection("users").document(user.id).set(user).await()
        withContext(Dispatchers.IO) {
            userDao.insert(user)
        }
    }

    suspend fun deleteUser(user: User) {
        if (user.id.isBlank()) throw IllegalArgumentException("User ID cannot be blank for deletion.")
        firestore.collection("users").document(user.id).delete().await()
        withContext(Dispatchers.IO) {
            userDao.delete(user)
        }
    }

    suspend fun resetEntireDatabase(): Pair<Boolean, String> {
        val collectionNames = listOf(
            "users", "rooms", "room_change_requests",
            "repair_reports", "announcements", "electric_meter_records", "payments"
        )
        return try {
            for (collectionName in collectionNames) {
                var query = firestore.collection(collectionName).limit(100)
                var snapshot = query.get().await()
                while(snapshot.size() > 0){
                    val batch = firestore.batch()
                    snapshot.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                    snapshot = query.get().await()
                }
                Log.d("AdminRepository", "Collection $collectionName cleared.")
            }
            Pair(true, "Firestore 資料庫已成功重置！")
        } catch (e: Exception) {
            Log.e("AdminRepository", "Failed to reset database", e)
            Pair(false, "重置失敗: ${e.message}")
        }
    }
}