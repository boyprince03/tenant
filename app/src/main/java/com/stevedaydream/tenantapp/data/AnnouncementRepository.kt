package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AnnouncementRepository(private val announcementDao: AnnouncementDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val announcementsCollection = firestore.collection("announcements")

    fun getAnnouncements(landlordCode: String?): Flow<List<Announcement>> {
        val query = if (landlordCode == null) {
            announcementsCollection.orderBy("date", Query.Direction.DESCENDING)
        } else {
            // Firestore 不支援 OR 查詢，這裡我們先取得所有公告，在客戶端篩選
            // 對於大型應用，建議建立一個 cloud function 或改變資料結構
            announcementsCollection.orderBy("date", Query.Direction.DESCENDING)
        }

        query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AnnouncementRepo", "Listen failed.", error)
                return@addSnapshotListener
            }
            snapshot?.let {
                val announcements = it.toObjects<Announcement>()
                CoroutineScope(Dispatchers.IO).launch {
                    announcementDao.insertOrUpdateAll(announcements)
                }
            }
        }

        return if (landlordCode != null) {
            announcementDao.getGlobalAndByLandlordCode(landlordCode)
        } else {
            announcementDao.getAll()
        }
    }

    // 【*** 修正：優化 insert 邏輯 ***】
    suspend fun insert(announcement: Announcement) {
        val docRef = announcementsCollection.document()
        // 將 Firestore 產生的 ID 存入物件後再寫入
        docRef.set(announcement.copy(id = docRef.id)).await()
    }

    // 【*** 修正：在 Announcement.id 改為 String 後，此處邏輯即可正常運作 ***】
    suspend fun update(announcement: Announcement) {
        if (announcement.id.isBlank()) throw IllegalArgumentException("Announcement ID cannot be blank for update.")
        announcementsCollection.document(announcement.id).set(announcement).await()
    }

    // 【*** 修正：在 Announcement.id 改為 String 後，此處邏輯即可正常運作 ***】
    suspend fun delete(announcement: Announcement) {
        if (announcement.id.isBlank()) throw IllegalArgumentException("Announcement ID cannot be blank for delete.")
        announcementsCollection.document(announcement.id).delete().await()
    }
}