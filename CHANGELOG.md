# BBC POS - Changelog & Master Baseline Record

All future development follows the **Master Project Rules** and builds strictly on top of this verified master baseline.

---

## [v4.4.1] - Google-Only Authentication Enforced - 2026-09-25

### Changes:
- **Guest Mode Removed**:
  - Removed "Continue as Guest / Offline Mode" button from `LoginScreen.kt`.
  - Removed "Start as Guest" fallback button from error banners.
  - Added dedicated Google Cloud Sync & security informational badge on login screen.
- **Strict Google Authentication**:
  - Enforced `!fbUser.isAnonymous` and verified email requirement in `AuthManager.kt`.
  - Automatically sign out and clear any old anonymous/guest sessions upon app start.
  - Enhanced error messages directing users to sign in with their Google account in device settings.
- **Verification**:
  - App compiled successfully (`compile_applet`).
  - All Robolectric and unit tests passed (`:app:testDebugUnitTest` successful).

---

## [v4.4.0] - Baseline Established - 2026-09-25

### Status: MASTER BASELINE VERIFIED & TESTED
- **Version Name**: 4.4
- **Version Code**: 44
- **APK Target**: Full Universal APK (~32 MB, all architectures & DEX included)
- **Database**: Room `CafePosDatabase` Schema Version 13
- **Build Status**: Verified & Succeeded
- **Unit & Robolectric Tests**: 100% Passed (`:app:testDebugUnitTest` successful)
  - `FifoInventoryTest` (FIFO deduction & batch costing)
  - `OrderPlacementTest` (Table order placement & KOT generation)
  - `RecipePersistenceAndCogsTest` (Recipe ingredient mapping & COGS)
  - `BillFormatConsistencyTest` (A4 PDF & thermal receipt format)
  - `DataIsolationTest` (Multi-user / multi-tenant data boundaries)
  - `SectionsVerificationTest` (Navigation & screen component integrity)

### Preserved Core Architecture & Functionalities
1. **Billing & Settlement**:
   - Dine-In / Takeaway / Quick Order modes
   - Table Grid with occupancy & active cart tracking
   - Menu ordering with category filter, search, variants & add-ons
   - KOT (Kitchen Order Ticket) generation & printing
   - Combo selection with choice groups & discounts
   - Customer loyalty rewards, points earn/redeem (PointsBatch & FIFO Points Ledger)
   - Settlement with Cash, Card, UPI, Split, and Due / Credit Ledger
   - Bill preview dialog, thermal receipt generation, and A4 PDF invoice generator
2. **FIFO Inventory & Costing**:
   - Multi-batch inventory tracking (`InventoryBatchEntity`)
   - First-In, First-Out (FIFO) stock deduction on sale completion
   - Low stock warnings & reorder alerts
   - Physical stock take & reconciliation (`StockCountEntity`)
   - Food cost & COGS calculation (`FoodCostCalculator`, `RecipeEntity`)
3. **Menu & Recipe Management**:
   - Menu items with categories, portions, images, dietary tags
   - Recipe editor linking raw inventory materials to menu items
   - Combo pack definitions (`ComboEntity`)
4. **Financials & Reporting**:
   - Day opening & closing cash register (`CashRegisterEntity`)
   - Expense tracking by categories (`ExpenseEntity`, `ExpenseCategoryEntity`)
   - Daily, weekly, monthly sales reports with GST/tax breakdown
   - Best-selling items, category analytics, profit & loss summary
5. **Multi-User Auth & Cloud Sync**:
   - Firebase Authentication with role-based access control (Admin, Manager, Cashier, Waiter)
   - Multi-device two-way cloud sync with Firestore (`FirestoreSyncManager`)
   - Offline-first Room database resilience
