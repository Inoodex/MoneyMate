package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinanceRepository(private val database: AppDatabase) {
    private val transactionDao = database.transactionDao()
    private val categoryDao = database.categoryDao()
    private val accountDao = database.accountDao()
    private val budgetDao = database.budgetDao()
    private val savingsGoalDao = database.savingsGoalDao()

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()
    val allSavingsGoals: Flow<List<SavingsGoalEntity>> = savingsGoalDao.getAllSavingsGoals()

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Long) {
        transactionDao.deleteTransactionById(id)
    }

    suspend fun insertCategory(category: CategoryEntity): Long {
        return categoryDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        categoryDao.deleteCategory(category)
    }

    suspend fun insertAccount(account: AccountEntity): Long {
        return accountDao.insertAccount(account)
    }

    suspend fun updateAccount(account: AccountEntity) {
        accountDao.updateAccount(account)
    }

    suspend fun deleteAccount(account: AccountEntity) {
        accountDao.deleteAccount(account)
    }

    suspend fun insertBudget(budget: BudgetEntity): Long {
        return budgetDao.insertBudget(budget)
    }

    suspend fun updateBudget(budget: BudgetEntity) {
        budgetDao.updateBudget(budget)
    }

    suspend fun deleteBudget(budget: BudgetEntity) {
        budgetDao.deleteBudget(budget)
    }

    suspend fun insertSavingsGoal(goal: SavingsGoalEntity): Long {
        return savingsGoalDao.insertSavingsGoal(goal)
    }

    suspend fun updateSavingsGoal(goal: SavingsGoalEntity) {
        savingsGoalDao.updateSavingsGoal(goal)
    }

    suspend fun deleteSavingsGoal(goal: SavingsGoalEntity) {
        savingsGoalDao.deleteSavingsGoal(goal)
    }

    suspend fun recordAccountTransfer(
        fromAccount: AccountEntity,
        toAccount: AccountEntity,
        amount: Double,
        note: String,
        dateMillis: Long
    ) {
        val transferTx = TransactionEntity(
            type = "TRANSFER",
            amount = amount,
            categoryId = 0L,
            categoryName = "Account Transfer",
            accountId = fromAccount.id,
            accountName = fromAccount.name,
            toAccountId = toAccount.id,
            toAccountName = toAccount.name,
            dateMillis = dateMillis,
            description = if (note.isBlank()) "Transfer to ${toAccount.name}" else note
        )
        transactionDao.insertTransaction(transferTx)
    }

    suspend fun recordSavingsDeposit(
        goal: SavingsGoalEntity,
        depositAmount: Double,
        fromAccount: AccountEntity
    ) {
        val newSaved = goal.currentSaved + depositAmount
        savingsGoalDao.updateSavingsGoal(goal.copy(currentSaved = newSaved))

        // Record a transaction as well so balance reflects and user sees where money went
        val transferTx = TransactionEntity(
            type = "EXPENSE",
            amount = depositAmount,
            categoryId = 0L,
            categoryName = "Savings Deposit",
            accountId = fromAccount.id,
            accountName = fromAccount.name,
            dateMillis = System.currentTimeMillis(),
            description = "Deposit into ${goal.title}"
        )
        transactionDao.insertTransaction(transferTx)
    }

    suspend fun resetDatabase() {
        transactionDao.deleteAllTransactions()
        AppDatabase.populateInitialData(database)
    }

    fun generateCsvExport(transactions: List<TransactionEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("ID,Date,Type,Category,Amount,Account,To Account,Description\n")
        transactions.forEach { tx ->
            val formattedDate = dateFormat.format(Date(tx.dateMillis))
            val cleanDesc = tx.description.replace(",", " ")
            val toAcc = tx.toAccountName ?: ""
            sb.append("${tx.id},\"$formattedDate\",${tx.type},\"${tx.categoryName}\",${tx.amount},\"${tx.accountName}\",\"$toAcc\",\"$cleanDesc\"\n")
        }
        return sb.toString()
    }

    fun generateJsonBackup(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>,
        budgets: List<BudgetEntity>,
        savingsGoals: List<SavingsGoalEntity>
    ): String {
        val root = JSONObject()
        root.put("app", "MoneyMate")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("formattedDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        // Transactions
        val txArray = JSONArray()
        transactions.forEach { tx ->
            val obj = JSONObject()
            obj.put("type", tx.type)
            obj.put("amount", tx.amount)
            obj.put("categoryName", tx.categoryName)
            obj.put("accountName", tx.accountName)
            obj.put("toAccountName", tx.toAccountName ?: "")
            obj.put("dateMillis", tx.dateMillis)
            obj.put("description", tx.description)
            obj.put("isRecurring", tx.isRecurring)
            obj.put("recurringFrequency", tx.recurringFrequency ?: "")
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        // Categories
        val catArray = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("name", cat.name)
            obj.put("type", cat.type)
            obj.put("iconName", cat.iconName)
            obj.put("colorHex", cat.colorHex)
            obj.put("isDefault", cat.isDefault)
            catArray.put(obj)
        }
        root.put("categories", catArray)

        // Accounts
        val accArray = JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject()
            obj.put("name", acc.name)
            obj.put("type", acc.type)
            obj.put("initialBalance", acc.initialBalance)
            obj.put("accountNumber", acc.accountNumber ?: "")
            obj.put("colorHex", acc.colorHex)
            obj.put("iconName", acc.iconName)
            accArray.put(obj)
        }
        root.put("accounts", accArray)

        // Budgets
        val budgetArray = JSONArray()
        budgets.forEach { b ->
            val obj = JSONObject()
            obj.put("categoryName", b.categoryName)
            obj.put("monthlyLimit", b.monthlyLimit)
            obj.put("monthYear", b.monthYear)
            budgetArray.put(obj)
        }
        root.put("budgets", budgetArray)

        // Savings Goals
        val goalArray = JSONArray()
        savingsGoals.forEach { g ->
            val obj = JSONObject()
            obj.put("title", g.title)
            obj.put("targetAmount", g.targetAmount)
            obj.put("currentSaved", g.currentSaved)
            obj.put("targetDateMillis", g.targetDateMillis)
            obj.put("colorHex", g.colorHex)
            obj.put("iconName", g.iconName)
            obj.put("notes", g.notes)
            goalArray.put(obj)
        }
        root.put("savingsGoals", goalArray)

        return root.toString(2)
    }

    suspend fun restoreFromJson(jsonString: String, clearExisting: Boolean = true): RestoreResult {
        return try {
            val root = JSONObject(jsonString)

            if (clearExisting) {
                transactionDao.deleteAllTransactions()
                budgetDao.deleteAllBudgets()
                savingsGoalDao.deleteAllSavingsGoals()
                categoryDao.deleteAllCategories()
                accountDao.deleteAllAccounts()
            }

            // 1. Restore Accounts
            val accArray = root.optJSONArray("accounts") ?: JSONArray()
            val accountIdMap = mutableMapOf<String, Long>()
            for (i in 0 until accArray.length()) {
                val obj = accArray.getJSONObject(i)
                val name = obj.optString("name", "Account")
                val entity = AccountEntity(
                    name = name,
                    type = obj.optString("type", "CASH"),
                    initialBalance = obj.optDouble("initialBalance", 0.0),
                    accountNumber = obj.optString("accountNumber").takeIf { it.isNotEmpty() },
                    colorHex = obj.optString("colorHex", "#1E6FD9"),
                    iconName = obj.optString("iconName", "AccountBalance")
                )
                val id = accountDao.insertAccount(entity)
                accountIdMap[name.lowercase()] = id
            }

            // 2. Restore Categories
            val catArray = root.optJSONArray("categories") ?: JSONArray()
            val categoryIdMap = mutableMapOf<String, Long>()
            for (i in 0 until catArray.length()) {
                val obj = catArray.getJSONObject(i)
                val name = obj.optString("name", "Other")
                val entity = CategoryEntity(
                    name = name,
                    type = obj.optString("type", "EXPENSE"),
                    iconName = obj.optString("iconName", "MoreHoriz"),
                    colorHex = obj.optString("colorHex", "#3B82F6"),
                    isDefault = obj.optBoolean("isDefault", false)
                )
                val id = categoryDao.insertCategory(entity)
                categoryIdMap[name.lowercase()] = id
            }

            // Fallback default category & account if empty
            if (accountDao.getAccountCount() == 0) {
                AppDatabase.populateInitialData(database)
            }

            // 3. Restore Transactions
            val txArray = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until txArray.length()) {
                val obj = txArray.getJSONObject(i)
                val catName = obj.optString("categoryName", "Other")
                val accName = obj.optString("accountName", "Cash Wallet")
                val toAccName = obj.optString("toAccountName").takeIf { it.isNotEmpty() }

                val catId = categoryIdMap[catName.lowercase()] ?: 1L
                val accId = accountIdMap[accName.lowercase()] ?: 1L
                val toAccId = toAccName?.let { accountIdMap[it.lowercase()] }

                val entity = TransactionEntity(
                    type = obj.optString("type", "EXPENSE"),
                    amount = obj.optDouble("amount", 0.0),
                    categoryId = catId,
                    categoryName = catName,
                    accountId = accId,
                    accountName = accName,
                    toAccountId = toAccId,
                    toAccountName = toAccName,
                    dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                    description = obj.optString("description", ""),
                    isRecurring = obj.optBoolean("isRecurring", false),
                    recurringFrequency = obj.optString("recurringFrequency").takeIf { it.isNotEmpty() }
                )
                transactionDao.insertTransaction(entity)
            }

            // 4. Restore Budgets
            val budgetArray = root.optJSONArray("budgets") ?: JSONArray()
            for (i in 0 until budgetArray.length()) {
                val obj = budgetArray.getJSONObject(i)
                val catName = obj.optString("categoryName", "Food")
                val catId = categoryIdMap[catName.lowercase()] ?: 1L
                val entity = BudgetEntity(
                    categoryId = catId,
                    categoryName = catName,
                    monthlyLimit = obj.optDouble("monthlyLimit", 5000.0),
                    monthYear = obj.optString("monthYear", "2026-10")
                )
                budgetDao.insertBudget(entity)
            }

            // 5. Restore Savings Goals
            val goalArray = root.optJSONArray("savingsGoals") ?: JSONArray()
            for (i in 0 until goalArray.length()) {
                val obj = goalArray.getJSONObject(i)
                val entity = SavingsGoalEntity(
                    title = obj.optString("title", "Goal"),
                    targetAmount = obj.optDouble("targetAmount", 10000.0),
                    currentSaved = obj.optDouble("currentSaved", 0.0),
                    targetDateMillis = obj.optLong("targetDateMillis", System.currentTimeMillis() + 86400000L * 30),
                    colorHex = obj.optString("colorHex", "#10B981"),
                    iconName = obj.optString("iconName", "Savings"),
                    notes = obj.optString("notes", "")
                )
                savingsGoalDao.insertSavingsGoal(entity)
            }

            RestoreResult(
                success = true,
                transactionsCount = txArray.length(),
                accountsCount = accArray.length(),
                categoriesCount = catArray.length(),
                budgetsCount = budgetArray.length(),
                goalsCount = goalArray.length()
            )
        } catch (e: Exception) {
            RestoreResult(
                success = false,
                transactionsCount = 0,
                accountsCount = 0,
                categoriesCount = 0,
                budgetsCount = 0,
                goalsCount = 0,
                errorMessage = e.localizedMessage ?: "Invalid backup file format"
            )
        }
    }

    suspend fun restoreFromCloud(bundle: CloudDataBundle, clearExisting: Boolean = true): RestoreResult {
        return try {
            if (clearExisting) {
                transactionDao.deleteAllTransactions()
                budgetDao.deleteAllBudgets()
                savingsGoalDao.deleteAllSavingsGoals()
                categoryDao.deleteAllCategories()
                accountDao.deleteAllAccounts()
            }

            val accountIdMap = mutableMapOf<String, Long>()
            bundle.accounts.forEach { acc ->
                val id = accountDao.insertAccount(
                    AccountEntity(
                        name = acc.name,
                        type = acc.type,
                        initialBalance = acc.initialBalance,
                        accountNumber = acc.accountNumber,
                        colorHex = acc.colorHex,
                        iconName = acc.iconName
                    )
                )
                accountIdMap[acc.name.lowercase()] = id
            }

            val categoryIdMap = mutableMapOf<String, Long>()
            bundle.categories.forEach { cat ->
                val id = categoryDao.insertCategory(
                    CategoryEntity(
                        name = cat.name,
                        type = cat.type,
                        iconName = cat.iconName,
                        colorHex = cat.colorHex,
                        isDefault = cat.isDefault
                    )
                )
                categoryIdMap[cat.name.lowercase()] = id
            }

            if (accountDao.getAccountCount() == 0) {
                AppDatabase.populateInitialData(database)
            }

            bundle.transactions.forEach { tx ->
                val catId = categoryIdMap[tx.categoryName.lowercase()] ?: tx.categoryId
                val accId = accountIdMap[tx.accountName.lowercase()] ?: tx.accountId
                val toAccId = tx.toAccountName?.let { accountIdMap[it.lowercase()] } ?: tx.toAccountId

                val entity = TransactionEntity(
                    type = tx.type,
                    amount = tx.amount,
                    categoryId = catId,
                    categoryName = tx.categoryName,
                    accountId = accId,
                    accountName = tx.accountName,
                    toAccountId = toAccId,
                    toAccountName = tx.toAccountName,
                    dateMillis = tx.dateMillis,
                    description = tx.description,
                    isRecurring = tx.isRecurring,
                    recurringFrequency = tx.recurringFrequency
                )
                transactionDao.insertTransaction(entity)
            }

            bundle.budgets.forEach { b ->
                val catId = categoryIdMap[b.categoryName.lowercase()] ?: b.categoryId
                budgetDao.insertBudget(
                    BudgetEntity(
                        categoryId = catId,
                        categoryName = b.categoryName,
                        monthlyLimit = b.monthlyLimit,
                        monthYear = b.monthYear
                    )
                )
            }

            bundle.savingsGoals.forEach { g ->
                savingsGoalDao.insertSavingsGoal(
                    SavingsGoalEntity(
                        title = g.title,
                        targetAmount = g.targetAmount,
                        currentSaved = g.currentSaved,
                        targetDateMillis = g.targetDateMillis,
                        colorHex = g.colorHex,
                        iconName = g.iconName,
                        notes = g.notes
                    )
                )
            }

            RestoreResult(
                success = true,
                transactionsCount = bundle.transactions.size,
                accountsCount = bundle.accounts.size,
                categoriesCount = bundle.categories.size,
                budgetsCount = bundle.budgets.size,
                goalsCount = bundle.savingsGoals.size
            )
        } catch (e: Exception) {
            RestoreResult(
                success = false,
                transactionsCount = 0,
                accountsCount = 0,
                categoriesCount = 0,
                budgetsCount = 0,
                goalsCount = 0,
                errorMessage = e.localizedMessage ?: "Failed to restore data from cloud"
            )
        }
    }
}

data class RestoreResult(
    val success: Boolean,
    val transactionsCount: Int,
    val accountsCount: Int,
    val categoriesCount: Int,
    val budgetsCount: Int,
    val goalsCount: Int,
    val errorMessage: String? = null
)

