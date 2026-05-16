package com.ledga.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ledga.app.data.db.entity.MpesaAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface MpesaAccountDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: MpesaAccount): Long

    @Update
    suspend fun update(account: MpesaAccount)

    @Query("SELECT * FROM mpesa_accounts ORDER BY isPrimary DESC, createdAt ASC")
    fun observeAll(): Flow<List<MpesaAccount>>

    @Query("SELECT * FROM mpesa_accounts ORDER BY isPrimary DESC, createdAt ASC")
    suspend fun getAll(): List<MpesaAccount>

    @Query("SELECT * FROM mpesa_accounts WHERE subscriptionId = :subscriptionId LIMIT 1")
    suspend fun findBySubscriptionId(subscriptionId: Int): MpesaAccount?

    @Query("SELECT * FROM mpesa_accounts WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): MpesaAccount?

    @Query("UPDATE mpesa_accounts SET isPrimary = (id = :id)")
    suspend fun setPrimary(id: Long)

    @Query("DELETE FROM mpesa_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM mpesa_accounts")
    fun count(): Flow<Int>
}
