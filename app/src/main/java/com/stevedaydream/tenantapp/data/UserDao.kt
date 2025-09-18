package com.stevedaydream.tenantapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM users WHERE username = :username AND password = :password")
    suspend fun login(username: String, password: String): User?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun findByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): User?

    @Query("SELECT * FROM users WHERE role = 'landlord'")
    fun getAllLandlords(): Flow<List<User>>

    // --- 新增此方法 ---
    @Query("SELECT * FROM users WHERE landlordCode = :code AND role = 'landlord' LIMIT 1")
    suspend fun getLandlordByCode(code: String): User?
}