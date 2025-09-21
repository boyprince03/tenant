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

/**
 * 用於執行管理員等級操作的 Repository。
 */
class AdminRepository(
    private val userDao: UserDao,
    private val roomDao: RoomDao,
    private val coroutineScope: CoroutineScope
) {

    private val firestore = FirebaseFirestore.getInstance()

    // --- New Methods for AdminHomeScreen ---

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

            // First, fetch the rooms to be updated
            for (roomId in roomIds) {
                val roomDoc = firestore.collection("rooms").document(roomId).get().await()
                val room = roomDoc.toObject(RoomEntity::class.java)
                if(room != null) {
                    updatedRooms.add(room.copy(landlordCode = landlordCode))
                }
            }

            // Now, update them in a batch
            for (room in updatedRooms) {
                val docRef = firestore.collection("rooms").document(room.id)
                batch.set(docRef, room) // Use set to overwrite the whole object
            }

            batch.commit().await()

            // After successful Firestore update, update local cache
            withContext(Dispatchers.IO) {
                roomDao.insertRooms(updatedRooms) // Assumes OnConflictStrategy.REPLACE
            }

            true
        } catch (e: Exception) {
            Log.e("AdminRepository", "Error assigning rooms to landlord $landlordCode", e)
            false
        }
    }


    suspend fun updateUser(user: User) {
        if (user.id.isBlank()) throw IllegalArgumentException("User ID cannot be blank for an update.")
        // 1. Update Firestore
        firestore.collection("users").document(user.id).set(user).await()
        // 2. Update local Room database
        withContext(Dispatchers.IO) {
            userDao.insert(user) // Assumes OnConflictStrategy.REPLACE
        }
    }

    suspend fun deleteUser(user: User) {
        if (user.id.isBlank()) throw IllegalArgumentException("User ID cannot be blank for deletion.")
        // 1. Delete from Firestore
        firestore.collection("users").document(user.id).delete().await()
        // 2. Delete from local Room database
        withContext(Dispatchers.IO) {
            userDao.delete(user)
        }
    }

    /**
     * 【警告】刪除 Firestore 資料庫中的所有資料！
     * @return Pair<Boolean, String> - first is success, second is message
     */
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
