package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.BillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY billTimestamp DESC")
    fun getAllBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills ORDER BY billTimestamp DESC")
    suspend fun getAllBillsDirect(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE restaurantId = :restaurantId ORDER BY billTimestamp DESC")
    fun getBillsByRestaurant(restaurantId: String): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE billTimestamp >= :startTime AND billTimestamp <= :endTime ORDER BY billTimestamp DESC")
    fun getBillsBetween(startTime: Long, endTime: Long): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE restaurantId = :restaurantId AND billTimestamp >= :startTime AND billTimestamp <= :endTime ORDER BY billTimestamp DESC")
    fun getBillsByRestaurantBetween(restaurantId: String, startTime: Long, endTime: Long): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun getBillById(id: String): BillEntity?

    @Query("SELECT * FROM bills WHERE tableId = :tableId AND isSettled = 0 AND isCancelled = 0 AND isVoided = 0 LIMIT 1")
    suspend fun getActiveBillForTable(tableId: String): BillEntity?

    @Query("SELECT * FROM bills WHERE isSettled = 0 AND isCancelled = 0 AND isVoided = 0 ORDER BY billTimestamp ASC")
    fun getActiveUnsettledBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE customerPhone = :phone AND customerPhone != '' ORDER BY billTimestamp DESC")
    fun getBillsByCustomerPhone(phone: String): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE customerPhone = :phone AND customerPhone != '' ORDER BY billTimestamp DESC")
    suspend fun getBillsByCustomerPhoneDirect(phone: String): List<BillEntity>

    @Query("SELECT COUNT(*) FROM bills")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM bills")
    suspend fun getCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bills: List<BillEntity>)

    @Delete
    suspend fun delete(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bills")
    suspend fun clearAll()
}
