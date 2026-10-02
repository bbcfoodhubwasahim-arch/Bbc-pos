package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.ExpenseCategoryEntity
import com.example.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    // Categories
    @Query("SELECT * FROM expense_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories ORDER BY name ASC")
    suspend fun getAllCategoriesDirect(): List<ExpenseCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: ExpenseCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCategories(categories: List<ExpenseCategoryEntity>)

    @Query("SELECT * FROM expense_categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: String): ExpenseCategoryEntity?

    @Query("DELETE FROM expense_categories WHERE id = :id")
    suspend fun deleteCategoryById(id: String)

    @Query("SELECT COUNT(*) FROM expense_categories")
    fun getCategoriesCount(): Flow<Int>

    // Expenses
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAllExpensesDirect(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE restaurantId = :restaurantId ORDER BY timestamp DESC")
    fun getExpensesByRestaurant(restaurantId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getExpensesBetween(startTime: Long, endTime: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE restaurantId = :restaurantId AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getExpensesByRestaurantBetween(restaurantId: String, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}
