package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * 用於執行管理員等級操作的 Repository。
 * 警告：這裡的方法可能具有破壞性，僅供開發和測試使用。
 */
class AdminRepository {

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


    /**
     * 【警告】刪除 Firestore 資料庫中的所有資料！
     * 這個函數會迭代所有已知的集合並刪除其中的所有文件。
     * 這是一個緩慢且昂貴的操作，切勿在正式環境的客戶端上公開。
     *
     * @return 操作是否成功。
     */
    suspend fun resetEntireDatabase(): Boolean {
        // 列出您專案中所有的集合名稱
        val collectionNames = listOf(
            "users",
            "rooms",
            "room_change_requests",
            "notes", // 假設還有 notes 集合
            "repair_reports",
            "announcements"
            // ... 將您所有的集合名稱加到這裡
        )

        return try {
            for (collectionName in collectionNames) {
                Log.d("AdminRepository", "正在刪除集合: $collectionName...")
                val collection = firestore.collection(collectionName)
                val snapshot = collection.get().await()
                for (document in snapshot.documents) {
                    collection.document(document.id).delete().await()
                }
                Log.d("AdminRepository", "集合 $collectionName 已清空。")
            }
            true
        } catch (e: Exception) {
            Log.e("AdminRepository", "重置資料庫失敗", e)
            false
        }
    }

    /**
     * 【新增】更新 Firestore 中的使用者資料。
     * @param user 包含更新後資料的 User 物件。
     */
    suspend fun updateUser(user: User) {
        if (user.id.isBlank()) throw IllegalArgumentException("User ID cannot be blank for an update.")
        firestore.collection("users").document(user.id).set(user).await()
    }

    /**
     * 【新增】【警告】從 Firestore 中刪除使用者文件。
     * 注意：此操作 **不會** 從 Firebase Authentication 中刪除使用者的登入憑證。
     * 刪除 Auth 使用者需要 Admin SDK，通常在後端執行。
     * @param user 要刪除的 User 物件。
     */
    suspend fun deleteUser(user: User) {
        if (user.id.isBlank()) throw IllegalArgumentException("User ID cannot be blank for deletion.")
        firestore.collection("users").document(user.id).delete().await()
    }
}
