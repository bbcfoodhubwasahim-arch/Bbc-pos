package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.BillBatchDeductionEntity
import com.example.data.local.entity.InventoryBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryBatchDao {
    @Query("SELECT * FROM inventory_batches WHERE inventoryItemId = :itemId ORDER BY timestamp DESC")
    fun getBatchesForItem(itemId: String): Flow<List<InventoryBatchEntity>>

    @Query("SELECT * FROM inventory_batches WHERE inventoryItemId = :itemId ORDER BY timestamp DESC")
    suspend fun getBatchesForItemDirect(itemId: String): List<InventoryBatchEntity>

    @Query("SELECT * FROM inventory_batches WHERE inventoryItemId = :itemId AND remainingQuantity > 0.000001 AND status != 'ARCHIVED' ORDER BY timestamp ASC, createdAt ASC, id ASC")
    suspend fun getActiveBatchesForItemFifo(itemId: String): List<InventoryBatchEntity>

    @Query("SELECT SUM(remainingQuantity) FROM inventory_batches WHERE inventoryItemId = :itemId AND remainingQuantity > 0.000001 AND status != 'ARCHIVED'")
    suspend fun getTotalRemainingStockForItem(itemId: String): Double?

    @Query("SELECT * FROM inventory_batches WHERE inventoryItemId = :itemId ORDER BY timestamp DESC, createdAt DESC LIMIT 1")
    suspend fun getLatestBatchForItem(itemId: String): InventoryBatchEntity?

    @Query("SELECT * FROM inventory_batches WHERE restaurantId = :restaurantId ORDER BY timestamp DESC")
    fun getBatchesByRestaurant(restaurantId: String): Flow<List<InventoryBatchEntity>>

    @Query("SELECT * FROM inventory_batches ORDER BY timestamp DESC")
    fun getAllBatches(): Flow<List<InventoryBatchEntity>>

    @Query("SELECT * FROM inventory_batches ORDER BY timestamp DESC")
    suspend fun getAllBatchesDirect(): List<InventoryBatchEntity>

    @Query("SELECT * FROM inventory_batches WHERE id = :id LIMIT 1")
    suspend fun getBatchById(id: String): InventoryBatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: InventoryBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBatches(batches: List<InventoryBatchEntity>)

    @Update
    suspend fun updateBatch(batch: InventoryBatchEntity)

    @Query("DELETE FROM inventory_batches WHERE id = :id")
    suspend fun deleteBatchById(id: String)

    @Query("DELETE FROM inventory_batches WHERE inventoryItemId = :itemId")
    suspend fun deleteBatchesByItemId(itemId: String)

    @Query("DELETE FROM inventory_batches")
    suspend fun clearAllBatches()

    // Bill batch deductions for refund/cancel restoration & audit
    @Query("SELECT * FROM bill_batch_deductions WHERE billId = :billId")
    suspend fun getDeductionsForBill(billId: String): List<BillBatchDeductionEntity>

    @Query("SELECT * FROM bill_batch_deductions ORDER BY timestamp DESC")
    suspend fun getAllDeductionsDirect(): List<BillBatchDeductionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeductions(deductions: List<BillBatchDeductionEntity>)

    @Update
    suspend fun updateDeductions(deductions: List<BillBatchDeductionEntity>)

    @Update
    suspend fun updateDeduction(deduction: BillBatchDeductionEntity)

    @Query("DELETE FROM bill_batch_deductions WHERE billId = :billId")
    suspend fun deleteDeductionsForBill(billId: String)
}
