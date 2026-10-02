package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.PointsLedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsLedgerDao {

    @Query("SELECT * FROM points_ledger ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<PointsLedgerEntity>>

    @Query("SELECT * FROM points_ledger ORDER BY timestamp DESC")
    suspend fun getAllEntriesDirect(): List<PointsLedgerEntity>

    @Query("SELECT * FROM points_ledger WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getEntriesByCustomer(customerId: String): Flow<List<PointsLedgerEntity>>

    @Query("SELECT * FROM points_ledger WHERE customerId = :customerId ORDER BY timestamp DESC")
    suspend fun getEntriesByCustomerDirect(customerId: String): List<PointsLedgerEntity>

    @Query("SELECT * FROM points_ledger WHERE billId = :billId")
    suspend fun getEntriesByBillId(billId: String): List<PointsLedgerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PointsLedgerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PointsLedgerEntity>)

    @Query("DELETE FROM points_ledger WHERE id = :id")
    suspend fun deleteById(id: String)
}
