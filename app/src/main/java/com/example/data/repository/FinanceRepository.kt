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
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        val catArray = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("name", cat.name)
            obj.put("type", cat.type)
            obj.put("iconName", cat.iconName)
            obj.put("colorHex", cat.colorHex)
            catArray.put(obj)
        }
        root.put("categories", catArray)

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

        return root.toString(2)
    }
}
