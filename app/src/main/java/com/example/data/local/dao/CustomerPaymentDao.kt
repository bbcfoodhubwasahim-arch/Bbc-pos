package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CustomerPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerPaymentDao {

    @Query("SELECT * FROM customer_payments ORDER BY timestamp DESC")
    fun getAllPayments(): Flow<List<CustomerPaymentEntity>>

    @Query("SELECT * FROM customer_payments ORDER BY timestamp DESC")
    suspend fun getAllPaymentsDirect(): List<CustomerPaymentEntity>

    @Query("SELECT * FROM customer_payments WHERE customerId = :customerId OR customerPhone = :phone ORDER BY timestamp DESC")
    fun getPaymentsForCustomer(customerId: String, phone: String): Flow<List<CustomerPaymentEntity>>

    @Query("SELECT * FROM customer_payments WHERE customerId = :customerId OR customerPhone = :phone ORDER BY timestamp DESC")
    suspend fun getPaymentsForCustomerDirect(customerId: String, phone: String): List<CustomerPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: CustomerPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<CustomerPaymentEntity>)

    @Query("DELETE FROM customer_payments WHERE id = :id")
    suspend fun deleteById(id: String)
}
