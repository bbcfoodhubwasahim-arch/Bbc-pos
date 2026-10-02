package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.OfferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {

    @Query("SELECT * FROM offers WHERE restaurantId = :restaurantId ORDER BY offerType ASC, visitNumber ASC, name ASC")
    fun getOffersByRestaurant(restaurantId: String): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers WHERE restaurantId = :restaurantId AND isActive = 1 ORDER BY visitNumber ASC, name ASC")
    fun getActiveOffersByRestaurant(restaurantId: String): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers ORDER BY visitNumber ASC, name ASC")
    fun getAllOffers(): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers ORDER BY visitNumber ASC, name ASC")
    suspend fun getAllOffersDirect(): List<OfferEntity>

    @Query("SELECT * FROM offers WHERE id = :id")
    suspend fun getOfferById(id: String): OfferEntity?

    @Query("SELECT * FROM offers WHERE offerType = 'VISIT_BASED' AND visitNumber = :visitNumber AND isActive = 1 LIMIT 1")
    suspend fun getActiveVisitOffer(visitNumber: Int): OfferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(offer: OfferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(offers: List<OfferEntity>)

    @Query("DELETE FROM offers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM offers WHERE restaurantId = :restaurantId")
    suspend fun deleteByRestaurantId(restaurantId: String)
}
