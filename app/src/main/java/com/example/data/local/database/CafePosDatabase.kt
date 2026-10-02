package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.*

@Database(
    entities = [
        RestaurantEntity::class,
        CategoryEntity::class,
        MenuItemEntity::class,
        CafeTableEntity::class,
        CustomerEntity::class,
        BillEntity::class,
        InventoryItemEntity::class,
        StockAdditionEntity::class,
        ExpenseCategoryEntity::class,
        ExpenseEntity::class,
        CashRegisterEntity::class,
        ComboEntity::class,
        RecipeEntity::class,
        InventoryBatchEntity::class,
        BillBatchDeductionEntity::class,
        StockCountEntity::class,
        StockTransactionEntity::class,
        CustomerPaymentEntity::class,
        OfferEntity::class,
        PointsBatchEntity::class,
        PointsLedgerEntity::class,
        AddonDefinitionEntity::class
    ],
    version = 23,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CafePosDatabase : RoomDatabase() {

    abstract fun restaurantDao(): RestaurantDao
    abstract fun categoryDao(): CategoryDao
    abstract fun menuItemDao(): MenuItemDao
    abstract fun cafeTableDao(): CafeTableDao
    abstract fun customerDao(): CustomerDao
    abstract fun billDao(): BillDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun cashRegisterDao(): CashRegisterDao
    abstract fun comboDao(): ComboDao
    abstract fun recipeDao(): RecipeDao
    abstract fun inventoryBatchDao(): InventoryBatchDao
    abstract fun stockCountDao(): StockCountDao
    abstract fun stockTransactionDao(): StockTransactionDao
    abstract fun customerPaymentDao(): CustomerPaymentDao
    abstract fun offerDao(): OfferDao
    abstract fun pointsBatchDao(): PointsBatchDao
    abstract fun pointsLedgerDao(): PointsLedgerDao
    abstract fun addonDao(): AddonDao

    companion object {
        @Volatile
        private var INSTANCE: CafePosDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE restaurants ADD COLUMN fssaiNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE restaurants ADD COLUMN isFssaiEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE restaurants ADD COLUMN showFssaiOnBill INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE restaurants ADD COLUMN gstNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE restaurants ADD COLUMN gstRate REAL NOT NULL DEFAULT 5.0")
                db.execSQL("ALTER TABLE restaurants ADD COLUMN isGstEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE restaurants ADD COLUMN showGstOnBill INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE menu_items ADD COLUMN imageUri TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS combos (
                        id TEXT NOT NULL PRIMARY KEY,
                        restaurantId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        price REAL NOT NULL,
                        badge TEXT NOT NULL DEFAULT '',
                        description TEXT NOT NULL DEFAULT '',
                        isActive INTEGER NOT NULL DEFAULT 1,
                        slots TEXT NOT NULL DEFAULT '[]',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN purchasePrice REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE bills ADD COLUMN isStockDeducted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN totalFoodCost REAL NOT NULL DEFAULT 0.0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recipes (
                        menuItemId TEXT NOT NULL PRIMARY KEY,
                        restaurantId TEXT NOT NULL,
                        menuItemName TEXT NOT NULL DEFAULT '',
                        ingredients TEXT NOT NULL DEFAULT '[]',
                        notes TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE restaurants ADD COLUMN billFormat TEXT NOT NULL DEFAULT 'A4'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS inventory_batches (
                        id TEXT NOT NULL PRIMARY KEY,
                        restaurantId TEXT NOT NULL,
                        inventoryItemId TEXT NOT NULL,
                        itemName TEXT NOT NULL,
                        initialQuantity REAL NOT NULL,
                        remainingQuantity REAL NOT NULL,
                        purchaseRate REAL NOT NULL,
                        unit TEXT NOT NULL,
                        batchType TEXT NOT NULL DEFAULT 'PURCHASE',
                        notes TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL DEFAULT 0,
                        isConsumed INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS bill_batch_deductions (
                        id TEXT NOT NULL PRIMARY KEY,
                        billId TEXT NOT NULL,
                        inventoryItemId TEXT NOT NULL,
                        batchId TEXT,
                        quantityDeducted REAL NOT NULL,
                        rate REAL NOT NULL,
                        cost REAL NOT NULL,
                        isNegativeStock INTEGER NOT NULL DEFAULT 0,
                        timestamp INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stock_counts (
                        id TEXT NOT NULL PRIMARY KEY,
                        restaurantId TEXT NOT NULL,
                        countDate INTEGER NOT NULL DEFAULT 0,
                        title TEXT NOT NULL DEFAULT '',
                        totalItemsChecked INTEGER NOT NULL DEFAULT 0,
                        totalVarianceCost REAL NOT NULL DEFAULT 0.0,
                        isAdjusted INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        items TEXT NOT NULL DEFAULT '[]',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add required FIFO fields to inventory_batches
                db.execSQL("ALTER TABLE inventory_batches ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE inventory_batches ADD COLUMN createdByUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE inventory_batches ADD COLUMN referenceTransactionId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE inventory_batches ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")

                // Add immutable snapshot audit fields to bill_batch_deductions
                db.execSQL("ALTER TABLE bill_batch_deductions ADD COLUMN menuItemId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bill_batch_deductions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bill_batch_deductions ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE bill_batch_deductions ADD COLUMN referenceId TEXT NOT NULL DEFAULT ''")

                // Create stock audit ledger table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stock_transactions (
                        id TEXT NOT NULL PRIMARY KEY,
                        restaurantId TEXT NOT NULL DEFAULT '',
                        inventoryItemId TEXT NOT NULL,
                        itemName TEXT NOT NULL DEFAULT '',
                        transactionType TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        unitRate REAL NOT NULL DEFAULT 0.0,
                        balanceAfter REAL NOT NULL DEFAULT 0.0,
                        batchId TEXT,
                        billId TEXT,
                        referenceId TEXT NOT NULL DEFAULT '',
                        notes TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add isCreditCustomer to customers table
                db.execSQL("ALTER TABLE customers ADD COLUMN isCreditCustomer INTEGER NOT NULL DEFAULT 0")

                // Create customer_payments table for credit payments
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS customer_payments (
                        id TEXT NOT NULL PRIMARY KEY,
                        customerId TEXT NOT NULL,
                        customerName TEXT NOT NULL DEFAULT '',
                        customerPhone TEXT NOT NULL DEFAULT '',
                        amount REAL NOT NULL,
                        paymentMode TEXT NOT NULL DEFAULT 'CASH',
                        notes TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add isDiscountEligible to categories table (default 1 = ON)
                db.execSQL("ALTER TABLE categories ADD COLUMN isDiscountEligible INTEGER NOT NULL DEFAULT 1")

                // Add isDiscountEligible to menu_items table (default 1 = ON)
                db.execSQL("ALTER TABLE menu_items ADD COLUMN isDiscountEligible INTEGER NOT NULL DEFAULT 1")

                // Add cashAmount and upiAmount to bills table
                db.execSQL("ALTER TABLE bills ADD COLUMN cashAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE bills ADD COLUMN upiAmount REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add billPrefix and billPrefixCountersJson to restaurants table
                db.execSQL("ALTER TABLE restaurants ADD COLUMN billPrefix TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE restaurants ADD COLUMN billPrefixCountersJson TEXT NOT NULL DEFAULT '{}'")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Customer loyalty fields
                db.execSQL("ALTER TABLE customers ADD COLUMN loyaltyVisitCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN loyaltyStartDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE customers ADD COLUMN loyaltyExpiryDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE customers ADD COLUMN currentLoyaltyProgramId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE customers ADD COLUMN loyaltyHistoryJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE customers ADD COLUMN rewardPointsBalance INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN giftPointsBalance INTEGER NOT NULL DEFAULT 0")

                // Bill loyalty & points fields
                db.execSQL("ALTER TABLE bills ADD COLUMN appliedRewardType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE bills ADD COLUMN appliedOfferId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE bills ADD COLUMN appliedOfferName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN rewardPointsEarned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN pointsRedeemed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN rewardPointsRedeemed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN giftPointsRedeemed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN isVoided INTEGER NOT NULL DEFAULT 0")

                // Offers table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS offers (
                        id TEXT NOT NULL PRIMARY KEY,
                        restaurantId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        offerType TEXT NOT NULL,
                        rewardType TEXT NOT NULL,
                        rewardValue REAL NOT NULL DEFAULT 0.0,
                        minBillAmount REAL NOT NULL DEFAULT 0.0,
                        applicableOrderTypes TEXT NOT NULL DEFAULT 'DINE_IN,TAKEAWAY',
                        isActive INTEGER NOT NULL DEFAULT 1,
                        totalVisitsInProgram INTEGER NOT NULL DEFAULT 6,
                        visitNumber INTEGER NOT NULL DEFAULT 1,
                        validityDays INTEGER NOT NULL DEFAULT 45,
                        freeItemQuantity INTEGER NOT NULL DEFAULT 1,
                        eligibleMenuItemIds TEXT NOT NULL DEFAULT '[]',
                        allowSameItemMultipleTimes INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Points Batches table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS points_batches (
                        id TEXT NOT NULL PRIMARY KEY,
                        customerId TEXT NOT NULL,
                        customerPhone TEXT NOT NULL DEFAULT '',
                        customerName TEXT NOT NULL DEFAULT '',
                        batchType TEXT NOT NULL,
                        initialPoints INTEGER NOT NULL DEFAULT 0,
                        remainingPoints INTEGER NOT NULL DEFAULT 0,
                        earnDate INTEGER NOT NULL DEFAULT 0,
                        expiryDate INTEGER NOT NULL DEFAULT 0,
                        billId TEXT DEFAULT NULL,
                        billNumber TEXT DEFAULT NULL,
                        notes TEXT NOT NULL DEFAULT '',
                        isExpired INTEGER NOT NULL DEFAULT 0,
                        reminderSent INTEGER NOT NULL DEFAULT 0,
                        reminderSentDate INTEGER DEFAULT NULL,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Points Ledger table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS points_ledger (
                        id TEXT NOT NULL PRIMARY KEY,
                        customerId TEXT NOT NULL,
                        customerPhone TEXT NOT NULL DEFAULT '',
                        customerName TEXT NOT NULL DEFAULT '',
                        transactionType TEXT NOT NULL,
                        pointsAmount INTEGER NOT NULL DEFAULT 0,
                        balanceType TEXT NOT NULL DEFAULT 'REWARD',
                        billId TEXT DEFAULT NULL,
                        billNumber TEXT DEFAULT NULL,
                        expiryDate INTEGER DEFAULT NULL,
                        rewardPointsBalanceAfter INTEGER NOT NULL DEFAULT 0,
                        giftPointsBalanceAfter INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN isEnrolledInLoyalty INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE customers ADD COLUMN lastVisitTimestamp INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS offers_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        restaurantId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        offerType TEXT NOT NULL,
                        rewardType TEXT NOT NULL,
                        rewardValue REAL NOT NULL,
                        minBillAmount REAL NOT NULL,
                        applicableOrderTypes TEXT NOT NULL,
                        isActive INTEGER NOT NULL,
                        totalVisitsInProgram INTEGER NOT NULL,
                        visitNumber INTEGER NOT NULL,
                        validityDays INTEGER NOT NULL,
                        freeItemQuantity INTEGER NOT NULL,
                        eligibleMenuItemIds TEXT NOT NULL,
                        allowSameItemMultipleTimes INTEGER NOT NULL,
                        freeItemName TEXT NOT NULL,
                        maxDiscountCap REAL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                val cursor = db.query("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='offers'")
                var tableExists = false
                if (cursor.moveToFirst()) {
                    tableExists = cursor.getInt(0) > 0
                }
                cursor.close()

                if (tableExists) {
                    db.execSQL("""
                        INSERT INTO offers_new (
                            id, restaurantId, name, offerType, rewardType, rewardValue,
                            minBillAmount, applicableOrderTypes, isActive, totalVisitsInProgram,
                            visitNumber, validityDays, freeItemQuantity, eligibleMenuItemIds,
                            allowSameItemMultipleTimes, freeItemName, maxDiscountCap, createdAt, updatedAt
                        )
                        SELECT 
                            id, restaurantId, name, offerType, rewardType, rewardValue,
                            minBillAmount, applicableOrderTypes, isActive, totalVisitsInProgram,
                            visitNumber, validityDays, freeItemQuantity, eligibleMenuItemIds,
                            allowSameItemMultipleTimes, '', NULL, createdAt, updatedAt
                        FROM offers
                    """.trimIndent())
                    db.execSQL("DROP TABLE offers")
                }
                db.execSQL("ALTER TABLE offers_new RENAME TO offers")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN birthday TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN status TEXT NOT NULL DEFAULT 'SETTLED'")
                db.execSQL("ALTER TABLE bills ADD COLUMN isCancelled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN cancellationReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN cancelledAt INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE menu_items ADD COLUMN variantsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE menu_items ADD COLUMN addonsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 17 enhances selected addons with quantity & amount, and recipes with applyMode
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS addons (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        price REAL NOT NULL DEFAULT 0.0,
                        selectionType TEXT NOT NULL DEFAULT 'MULTI',
                        groupName TEXT NOT NULL DEFAULT 'Add-ons',
                        isRequired INTEGER NOT NULL DEFAULT 0,
                        minCount INTEGER NOT NULL DEFAULT 0,
                        maxCount INTEGER NOT NULL DEFAULT 2147483647,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        inventoryItemId TEXT DEFAULT NULL,
                        inventoryItemName TEXT DEFAULT NULL,
                        inventoryQty REAL NOT NULL DEFAULT 1.0,
                        usageUnit TEXT NOT NULL DEFAULT 'Nos',
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE categories ADD COLUMN defaultAddonIdsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE menu_items ADD COLUMN excludedAddonIdsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE menu_items ADD COLUMN itemAddonIdsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 19 schema sync
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE offers ADD COLUMN cooldownDays INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Column may already exist
                }
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN altPhone TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN tagline TEXT NOT NULL DEFAULT 'Taste the Best, Love the Rest!'")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN customTermsNote TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN upiId TEXT NOT NULL DEFAULT 'bbcfoodhubwasahim@okaxis'")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN upiQrEnabled INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN wifiDetails TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN socialHandle TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN showTokenOnBill INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN showSavingsOnBill INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN showPointsOnBill INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN showPreviousDueOnBill INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN paperWidth TEXT NOT NULL DEFAULT '80MM'")
                } catch (e: Exception) {
                    // Columns may already exist
                }
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN customLogoBase64 TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    // Column may already exist
                }
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE restaurants ADD COLUMN showLogoOnBill INTEGER NOT NULL DEFAULT 1")
                } catch (e: Exception) {
                    // Column may already exist
                }
            }
        }

        fun getDatabase(context: Context): CafePosDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CafePosDatabase::class.java,
                    "cafe_pos_database"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                        MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                        MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

