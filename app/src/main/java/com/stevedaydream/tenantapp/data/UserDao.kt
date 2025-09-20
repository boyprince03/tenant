package com.stevedaydream.tenantapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    // 【核心修改】改為 OnConflictStrategy.REPLACE，方便從雲端更新本機快取
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Update
    suspend fun updateUser(user: User)

    // 【核心修改】這個方法將被 AuthRepository 取代，暫時註解或刪除
    // @Query("SELECT * FROM users WHERE username = :username AND password = :password")
    // suspend fun login(username: String, password: String): User?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun findByUsername(username: String): User?

    // 【核心修改】id 的類型從 Int 改為 String
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): User? // <-- Int 改為 String

    @Query("SELECT * FROM users WHERE role = 'landlord'")
    fun getAllLandlords(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE landlordCode = :code AND role = 'landlord' LIMIT 1")
    suspend fun getLandlordByCode(code: String): User?
}