package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.RecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes")
    fun getAllRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes")
    suspend fun getAllRecipesDirect(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE menuItemId = :menuItemId LIMIT 1")
    fun getRecipeForMenuItem(menuItemId: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE menuItemId = :menuItemId LIMIT 1")
    suspend fun getRecipeForMenuItemDirect(menuItemId: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE restaurantId = :restaurantId")
    fun getRecipesByRestaurant(restaurantId: String): Flow<List<RecipeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<RecipeEntity>)

    @Query("DELETE FROM recipes WHERE menuItemId = :menuItemId")
    suspend fun deleteRecipeByMenuItemId(menuItemId: String)

    @Query("DELETE FROM recipes")
    suspend fun clearAll()
}
