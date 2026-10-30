package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.BudgetDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.SavingsGoalDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moneymate_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val categoryDao = database.categoryDao()
            val accountDao = database.accountDao()
            val budgetDao = database.budgetDao()
            val savingsGoalDao = database.savingsGoalDao()
            val transactionDao = database.transactionDao()

            if (categoryDao.getCategoryCount() == 0) {
                val incomeCategories = listOf(
                    CategoryEntity(name = "Salary", type = "INCOME", iconName = "Work", colorHex = "#10B981"),
                    CategoryEntity(name = "Business", type = "INCOME", iconName = "Store", colorHex = "#3B82F6"),
                    CategoryEntity(name = "Freelancing", type = "INCOME", iconName = "Laptop", colorHex = "#8B5CF6"),
                    CategoryEntity(name = "Investment", type = "INCOME", iconName = "TrendingUp", colorHex = "#F59E0B"),
                    CategoryEntity(name = "Gift", type = "INCOME", iconName = "CardGiftcard", colorHex = "#EC4899"),
                    CategoryEntity(name = "Bonus", type = "INCOME", iconName = "AttachMoney", colorHex = "#14B8A6"),
                    CategoryEntity(name = "Other", type = "INCOME", iconName = "MoreHoriz", colorHex = "#64748B")
                )

                val expenseCategories = listOf(
                    CategoryEntity(name = "Food", type = "EXPENSE", iconName = "Restaurant", colorHex = "#EF4444"),
                    CategoryEntity(name = "Shopping", type = "EXPENSE", iconName = "ShoppingCart", colorHex = "#F97316"),
                    CategoryEntity(name = "Transport", type = "EXPENSE", iconName = "DirectionsCar", colorHex = "#06B6D4"),
                    CategoryEntity(name = "Rent", type = "EXPENSE", iconName = "Home", colorHex = "#8B5CF6"),
                    CategoryEntity(name = "Bills", type = "EXPENSE", iconName = "Receipt", colorHex = "#6366F1"),
                    CategoryEntity(name = "Healthcare", type = "EXPENSE", iconName = "LocalHospital", colorHex = "#EC4899"),
                    CategoryEntity(name = "Education", type = "EXPENSE", iconName = "School", colorHex = "#3B82F6"),
                    CategoryEntity(name = "Entertainment", type = "EXPENSE", iconName = "Theaters", colorHex = "#A855F7"),
                    CategoryEntity(name = "Travel", type = "EXPENSE", iconName = "Flight", colorHex = "#10B981"),
                    CategoryEntity(name = "Groceries", type = "EXPENSE", iconName = "LocalGroceryStore", colorHex = "#84CC16"),
                    CategoryEntity(name = "Other", type = "EXPENSE", iconName = "MoreHoriz", colorHex = "#64748B")
                )

                categoryDao.insertCategories(incomeCategories + expenseCategories)
            }

            if (accountDao.getAccountCount() == 0) {
                val accounts = listOf(
                    AccountEntity(name = "Cash Wallet", type = "CASH", initialBalance = 5000.0, accountNumber = "Cash in Hand", colorHex = "#10B981", iconName = "AccountBalanceWallet"),
                    AccountEntity(name = "City Bank", type = "BANK", initialBalance = 45000.0, accountNumber = "Ac: ****4391", colorHex = "#1E6FD9", iconName = "AccountBalance"),
                    AccountEntity(name = "bKash Mobile Wallet", type = "MOBILE_BANKING", initialBalance = 12500.0, accountNumber = "017******89", colorHex = "#D91E5B", iconName = "PhoneAndroid"),
                    AccountEntity(name = "DPS Savings Account", type = "SAVINGS", initialBalance = 80000.0, accountNumber = "DPS #8271", colorHex = "#059669", iconName = "Savings"),
                    AccountEntity(name = "Visa Platinum Credit", type = "CREDIT_CARD", initialBalance = 0.0, accountNumber = "****1289", colorHex = "#6366F1", iconName = "CreditCard")
                )
                accountDao.insertAccounts(accounts)

                // Add starter sample budget and savings goal for immediate interactive experience
                val now = System.currentTimeMillis()
                val calendar = java.util.Calendar.getInstance()
                val currentYear = calendar.get(java.util.Calendar.YEAR)
                val currentMonth = String.format("%02d", calendar.get(java.util.Calendar.MONTH) + 1)
                val monthYear = "$currentYear-$currentMonth"

                budgetDao.insertBudget(BudgetEntity(categoryId = 1, categoryName = "Food", monthlyLimit = 8000.0, monthYear = monthYear))
                budgetDao.insertBudget(BudgetEntity(categoryId = 2, categoryName = "Shopping", monthlyLimit = 5000.0, monthYear = monthYear))
                budgetDao.insertBudget(BudgetEntity(categoryId = 3, categoryName = "Transport", monthlyLimit = 4000.0, monthYear = monthYear))

                calendar.add(java.util.Calendar.MONTH, 6)
                savingsGoalDao.insertSavingsGoal(
                    SavingsGoalEntity(
                        title = "Emergency Fund",
                        targetAmount = 100000.0,
                        currentSaved = 35000.0,
                        targetDateMillis = calendar.timeInMillis,
                        colorHex = "#10B981",
                        iconName = "Security",
                        notes = "6 months living expenses"
                    )
                )

                savingsGoalDao.insertSavingsGoal(
                    SavingsGoalEntity(
                        title = "New Laptop",
                        targetAmount = 85000.0,
                        currentSaved = 40000.0,
                        targetDateMillis = calendar.timeInMillis + 86400000L * 90,
                        colorHex = "#3B82F6",
                        iconName = "Laptop",
                        notes = "For work and development"
                    )
                )

                // Starter initial transactions
                val dayMillis = 86400000L
                transactionDao.insertTransaction(
                    TransactionEntity(
                        type = "INCOME",
                        amount = 65000.0,
                        categoryId = 1,
                        categoryName = "Salary",
                        accountId = 2,
                        accountName = "City Bank",
                        dateMillis = now - dayMillis * 4,
                        description = "Monthly Salary Deposit"
                    )
                )
                transactionDao.insertTransaction(
                    TransactionEntity(
                        type = "EXPENSE",
                        amount = 1200.0,
                        categoryId = 1,
                        categoryName = "Food",
                        accountId = 3,
                        accountName = "bKash Mobile Wallet",
                        dateMillis = now - dayMillis * 2,
                        description = "Family Dinner"
                    )
                )
                transactionDao.insertTransaction(
                    TransactionEntity(
                        type = "EXPENSE",
                        amount = 2500.0,
                        categoryId = 10,
                        categoryName = "Groceries",
                        accountId = 1,
                        accountName = "Cash Wallet",
                        dateMillis = now - dayMillis,
                        description = "Weekly Vegetables & Groceries"
                    )
                )
            }
        }
    }
}
