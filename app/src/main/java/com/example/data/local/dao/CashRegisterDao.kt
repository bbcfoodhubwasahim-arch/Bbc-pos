package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CashRegisterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashRegisterDao {
    @Query("SELECT * FROM cash_registers ORDER BY openedAt DESC")
    fun getAllRegisters(): Flow<List<CashRegisterEntity>>

    @Query("SELECT * FROM cash_registers ORDER BY openedAt DESC")
    suspend fun getAllRegistersDirect(): List<CashRegisterEntity>

    @Query("SELECT * FROM cash_registers WHERE restaurantId = :restaurantId ORDER BY openedAt DESC")
    fun getRegistersByRestaurant(restaurantId: String): Flow<List<CashRegisterEntity>>

    @Query("SELECT * FROM cash_registers WHERE restaurantId = :restaurantId AND status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    fun getOpenRegister(restaurantId: String): Flow<CashRegisterEntity?>

    @Query("SELECT * FROM cash_registers WHERE restaurantId = :restaurantId AND dateString = :dateString LIMIT 1")
    suspend fun getRegisterForDate(restaurantId: String, dateString: String): CashRegisterEntity?

    @Query("SELECT * FROM cash_registers WHERE id = :id LIMIT 1")
    suspend fun getRegisterById(id: String): CashRegisterEntity?

    @Query("SELECT COUNT(*) FROM cash_registers")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cash_registers")
    suspend fun getCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(register: CashRegisterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(registers: List<CashRegisterEntity>)

    @Query("DELETE FROM cash_registers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cash_registers")
    suspend fun clearAll()
}
