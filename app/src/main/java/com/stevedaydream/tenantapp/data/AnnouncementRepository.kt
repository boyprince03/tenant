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
import java.util.UUID

class AnnouncementRepository(private val announcementDao: AnnouncementDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val announcementsCollection = firestore.collection("announcements")

    /**
     * [讀取]
     * 取得公告資料流，並監聽雲端變化同步至本地。
     */
    fun getAnnouncements(landlordCode: String?): Flow<List<Announcement>> {
        // ... (讀取邏輯不變，已符合要求)
        val query = announcementsCollection.orderBy("date", Query.Direction.DESCENDING)

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

    /**
     * [寫入]
     * 新增一筆公告。
     */
    suspend fun insert(announcement: Announcement) {
        val docRef = announcementsCollection.document()
        val newAnnouncement = announcement.copy(id = docRef.id)
        // 1. 操作雲端
        docRef.set(newAnnouncement).await()
        // 2. 更新本地
        announcementDao.insert(newAnnouncement)
    }

    /**
     * [修改]
     * 更新一筆公告。
     */
    suspend fun update(announcement: Announcement) {
        if (announcement.id.isBlank()) throw IllegalArgumentException("Announcement ID cannot be blank for update.")
        // 1. 操作雲端
        announcementsCollection.document(announcement.id).set(announcement).await()
        // 2. 更新本地
        announcementDao.update(announcement)
    }

    /**
     * [刪除]
     * 刪除一筆公告。
     */
    suspend fun delete(announcement: Announcement) {
        if (announcement.id.isBlank()) throw IllegalArgumentException("Announcement ID cannot be blank for delete.")
        // 1. 操作雲端
        announcementsCollection.document(announcement.id).delete().await()
        // 2. 更新本地
        announcementDao.delete(announcement)
    }
}