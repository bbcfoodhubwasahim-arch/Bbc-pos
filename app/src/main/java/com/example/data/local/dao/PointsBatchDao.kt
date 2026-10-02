package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.PointsBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsBatchDao {

    @Query("SELECT * FROM points_batches WHERE id = :id LIMIT 1")
    suspend fun getBatchById(id: String): PointsBatchEntity?

    @Query("SELECT * FROM points_batches WHERE customerId = :customerId ORDER BY earnDate DESC")
    fun getBatchesByCustomer(customerId: String): Flow<List<PointsBatchEntity>>

    @Query("SELECT * FROM points_batches WHERE customerId = :customerId AND isExpired = 0 AND remainingPoints > 0 AND expiryDate > :now ORDER BY expiryDate ASC")
    suspend fun getValidBatchesForCustomer(customerId: String, now: Long = System.currentTimeMillis()): List<PointsBatchEntity>

    @Query("SELECT * FROM points_batches WHERE customerId = :customerId AND batchType = :batchType AND isExpired = 0 AND remainingPoints > 0 AND expiryDate > :now ORDER BY expiryDate ASC")
    suspend fun getValidBatchesForCustomerByType(customerId: String, batchType: String, now: Long = System.currentTimeMillis()): List<PointsBatchEntity>

    @Query("SELECT * FROM points_batches WHERE isExpired = 0 AND remainingPoints > 0 AND expiryDate > :now")
    fun getAllActiveBatches(now: Long = System.currentTimeMillis()): Flow<List<PointsBatchEntity>>

    @Query("SELECT * FROM points_batches WHERE isExpired = 0 AND remainingPoints > 0 AND expiryDate > :now")
    suspend fun getAllActiveBatchesList(now: Long = System.currentTimeMillis()): List<PointsBatchEntity>

    @Query("SELECT * FROM points_batches WHERE isExpired = 0 AND remainingPoints > 0 AND expiryDate BETWEEN :now AND :thresholdTime ORDER BY expiryDate ASC")
    fun getExpiringBatches(now: Long, thresholdTime: Long): Flow<List<PointsBatchEntity>>

    @Query("SELECT * FROM points_batches WHERE billId = :billId")
    suspend fun getBatchesByBillId(billId: String): List<PointsBatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(batch: PointsBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(batches: List<PointsBatchEntity>)

    @Query("UPDATE points_batches SET remainingPoints = :remaining, isExpired = :isExpired, updatedAt = :now WHERE id = :id")
    suspend fun updateRemainingPoints(id: String, remaining: Int, isExpired: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE points_batches SET reminderSent = 1, reminderSentDate = :sentDate WHERE id = :id")
    suspend fun markReminderSent(id: String, sentDate: Long = System.currentTimeMillis())

    @Query("DELETE FROM points_batches WHERE id = :id")
    suspend fun deleteById(id: String)
}
