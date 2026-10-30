package com.example.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettings
import com.example.data.repository.CloudDataBundle
import com.example.data.repository.FinanceRepository
import com.example.data.repository.FirestoreSyncRepository
import com.example.util.GoogleAuthManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Success(val message: String, val timestamp: Long = System.currentTimeMillis()) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

enum class TransactionFilterType {
    ALL, INCOME, EXPENSE, TRANSFER
}

enum class DateRangeFilter {
    ALL, TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR
}

enum class SortOption {
    DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC
}

enum class ReportPeriod {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

data class CategorySpend(
    val categoryName: String,
    val totalAmount: Double,
    val percentage: Float,
    val colorHex: String,
    val count: Int
)

data class BarChartItem(
    val label: String,
    val income: Double,
    val expense: Double
)

data class AccountBalanceItem(
    val account: AccountEntity,
    val currentBalance: Double
)

data class BudgetProgressItem(
    val budget: BudgetEntity,
    val spentAmount: Double,
    val percentage: Float, // 0.0 to 1.0+
    val remainingAmount: Double,
    val isOverBudget: Boolean,
    val isNearBudget: Boolean // >= 80%
)

data class DashboardState(
    val totalBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val totalSavings: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val monthlySavings: Double = 0.0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val monthlyCategorySpends: List<CategorySpend> = emptyList(),
    val chartData: List<BarChartItem> = emptyList(),
    val accountBalances: List<AccountBalanceItem> = emptyList(),
    val budgetAlerts: List<String> = emptyList()
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = FinanceRepository(database)
    private val prefsRepository = UserPreferencesRepository(application)
    private val firestoreSyncRepository = FirestoreSyncRepository(application)
    private val auth = Firebase.auth
    private val credentialManager = CredentialManager.create(application)

    val currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val isGuestMode = MutableStateFlow(false)
    val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    val userSettings: StateFlow<UserSettings> = prefsRepository.userSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allCategories: StateFlow<List<CategoryEntity>> = repository.allCategories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allAccounts: StateFlow<List<AccountEntity>> = repository.allAccounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allBudgets: StateFlow<List<BudgetEntity>> = repository.allBudgets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allSavingsGoals: StateFlow<List<SavingsGoalEntity>> = repository.allSavingsGoals.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Transaction History Filter States
    val searchQuery = MutableStateFlow("")
    val filterType = MutableStateFlow(TransactionFilterType.ALL)
    val filterDateRange = MutableStateFlow(DateRangeFilter.ALL)
    val filterCategoryId = MutableStateFlow(0L)
    val filterAccountId = MutableStateFlow(0L)
    val sortOption = MutableStateFlow(SortOption.DATE_DESC)

    // Report Period State
    val reportPeriod = MutableStateFlow(ReportPeriod.MONTHLY)

    // App Lock State
    val isAppLocked = MutableStateFlow(false)

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        currentUser.value = user
        if (user != null) {
            viewModelScope.launch {
                val displayName = user.displayName ?: user.email?.substringBefore("@") ?: "User"
                val email = user.email ?: ""
                prefsRepository.updateProfile(displayName, email)
                if (userSettings.value.isAutoSyncEnabled) {
                    triggerAutoSync()
                }
            }
        }
    }

    init {
        auth.addAuthStateListener(authListener)

        // Attempt silent sign-in if existing session credentials exist
        GoogleAuthManager.attemptAutoSignIn(
            context = application,
            credentialManager = credentialManager,
            onAuthSuccess = {
                currentUser.value = auth.currentUser
            },
            onUnauthenticated = {},
            scope = viewModelScope
        )

        viewModelScope.launch {
            userSettings.collect { settings ->
                if (settings.isPinLockEnabled && settings.pinCode.isNotBlank()) {
                    isAppLocked.value = true
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    fun unlockApp() {
        isAppLocked.value = false
    }

    // Dashboard Combined State
    val dashboardState: StateFlow<DashboardState> = combine(
        allTransactions,
        allAccounts,
        allCategories,
        allBudgets
    ) { transactions, accounts, categories, budgets ->
        calculateDashboard(transactions, accounts, categories, budgets)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardState()
    )

    // Budget Progress List
    val budgetProgressList: StateFlow<List<BudgetProgressItem>> = combine(
        allBudgets,
        allTransactions
    ) { budgets, transactions ->
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        calendar.set(currentYear, currentMonth, 1, 0, 0, 0)
        val monthStart = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        val monthEnd = calendar.timeInMillis

        budgets.map { budget ->
            val spent = transactions.filter {
                it.type == "EXPENSE" &&
                (it.categoryId == budget.categoryId || it.categoryName.equals(budget.categoryName, ignoreCase = true)) &&
                it.dateMillis in monthStart until monthEnd
            }.sumOf { it.amount }

            val percentage = if (budget.monthlyLimit > 0) (spent / budget.monthlyLimit).toFloat() else 0f
            val remaining = (budget.monthlyLimit - spent).coerceAtLeast(0.0)

            BudgetProgressItem(
                budget = budget,
                spentAmount = spent,
                percentage = percentage,
                remainingAmount = remaining,
                isOverBudget = spent > budget.monthlyLimit,
                isNearBudget = percentage >= 0.80f && spent <= budget.monthlyLimit
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered Transactions
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        searchQuery,
        filterType,
        filterDateRange,
        filterCategoryId,
        filterAccountId,
        sortOption
    ) { args: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        val txs = args[0] as List<TransactionEntity>
        val query = (args[1] as String).trim().lowercase()
        val type = args[2] as TransactionFilterType
        val dateRange = args[3] as DateRangeFilter
        val catId = args[4] as Long
        val accId = args[5] as Long
        val sort = args[6] as SortOption

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        val startTime = when (dateRange) {
            DateRangeFilter.ALL -> 0L
            DateRangeFilter.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            DateRangeFilter.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            DateRangeFilter.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            DateRangeFilter.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
        }

        var result = txs.filter { tx ->
            val matchesQuery = query.isEmpty() ||
                tx.description.lowercase().contains(query) ||
                tx.categoryName.lowercase().contains(query) ||
                tx.accountName.lowercase().contains(query) ||
                tx.amount.toString().contains(query)

            val matchesType = when (type) {
                TransactionFilterType.ALL -> true
                TransactionFilterType.INCOME -> tx.type == "INCOME"
                TransactionFilterType.EXPENSE -> tx.type == "EXPENSE"
                TransactionFilterType.TRANSFER -> tx.type == "TRANSFER"
            }

            val matchesDate = tx.dateMillis >= startTime
            val matchesCategory = catId == 0L || tx.categoryId == catId
            val matchesAccount = accId == 0L || tx.accountId == accId || tx.toAccountId == accId

            matchesQuery && matchesType && matchesDate && matchesCategory && matchesAccount
        }

        result = when (sort) {
            SortOption.DATE_DESC -> result.sortedByDescending { it.dateMillis }
            SortOption.DATE_ASC -> result.sortedBy { it.dateMillis }
            SortOption.AMOUNT_DESC -> result.sortedByDescending { it.amount }
            SortOption.AMOUNT_ASC -> result.sortedBy { it.amount }
            else -> result.sortedByDescending { it.dateMillis }
        }

        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun calculateDashboard(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>
    ): DashboardState {
        var totalIncome = 0.0
        var totalExpense = 0.0

        transactions.forEach { tx ->
            if (tx.type == "INCOME") totalIncome += tx.amount
            if (tx.type == "EXPENSE") totalExpense += tx.amount
            // TRANSFERS are excluded from income and expenses!
        }

        val openingBalance = accounts.sumOf { it.initialBalance }
        val totalBalance = openingBalance + totalIncome - totalExpense
        val totalSavings = totalIncome - totalExpense

        // Monthly calculations
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        calendar.set(currentYear, currentMonth, 1, 0, 0, 0)
        val monthStart = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        val monthEnd = calendar.timeInMillis

        val monthTxs = transactions.filter { it.dateMillis in monthStart until monthEnd }
        val monthlyIncome = monthTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
        val monthlyExpense = monthTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val monthlySavings = monthlyIncome - monthlyExpense

        // Category breakdown for current month expenses
        val catMap = mutableMapOf<String, Double>()
        val catCount = mutableMapOf<String, Int>()
        monthTxs.filter { it.type == "EXPENSE" }.forEach { tx ->
            catMap[tx.categoryName] = (catMap[tx.categoryName] ?: 0.0) + tx.amount
            catCount[tx.categoryName] = (catCount[tx.categoryName] ?: 0) + 1
        }

        val categoryColors = categories.associate { it.name to it.colorHex }
        val monthlyCategorySpends = catMap.map { (name, amount) ->
            val percentage = if (monthlyExpense > 0) ((amount / monthlyExpense) * 100).toFloat() else 0f
            CategorySpend(
                categoryName = name,
                totalAmount = amount,
                percentage = percentage,
                colorHex = categoryColors[name] ?: "#3B82F6",
                count = catCount[name] ?: 1
            )
        }.sortedByDescending { it.totalAmount }

        // Bar Chart items for last 6 months
        val chartData = mutableListOf<BarChartItem>()
        val monthCal = Calendar.getInstance()
        for (i in 5 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -i)
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH)
            c.set(y, m, 1, 0, 0, 0)
            val mStart = c.timeInMillis
            c.add(Calendar.MONTH, 1)
            val mEnd = c.timeInMillis

            val mLabel = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(java.util.Date(mStart))
            val mIncome = transactions.filter { it.type == "INCOME" && it.dateMillis in mStart until mEnd }.sumOf { it.amount }
            val mExpense = transactions.filter { it.type == "EXPENSE" && it.dateMillis in mStart until mEnd }.sumOf { it.amount }
            chartData.add(BarChartItem(label = mLabel, income = mIncome, expense = mExpense))
        }

        // Account balances
        val accountBalances = accounts.map { acc ->
            var bal = acc.initialBalance
            transactions.forEach { tx ->
                if (tx.accountId == acc.id) {
                    when (tx.type) {
                        "INCOME" -> bal += tx.amount
                        "EXPENSE" -> bal -= tx.amount
                        "TRANSFER" -> bal -= tx.amount
                    }
                }
                if (tx.toAccountId == acc.id && tx.type == "TRANSFER") {
                    bal += tx.amount
                }
            }
            AccountBalanceItem(account = acc, currentBalance = bal)
        }

        // Budget Alerts
        val alerts = mutableListOf<String>()
        budgets.forEach { budget ->
            val spent = monthTxs.filter {
                it.type == "EXPENSE" &&
                (it.categoryId == budget.categoryId || it.categoryName.equals(budget.categoryName, ignoreCase = true))
            }.sumOf { it.amount }
            if (spent > budget.monthlyLimit) {
                alerts.add("Warning: Exceeded budget for ${budget.categoryName} by ${spent - budget.monthlyLimit}")
            } else if (budget.monthlyLimit > 0 && (spent / budget.monthlyLimit) >= 0.80) {
                alerts.add("Notice: Reached 80% of ${budget.categoryName} budget")
            }
        }

        return DashboardState(
            totalBalance = totalBalance,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            totalSavings = totalSavings,
            monthlyIncome = monthlyIncome,
            monthlyExpense = monthlyExpense,
            monthlySavings = monthlySavings,
            recentTransactions = transactions.take(6),
            monthlyCategorySpends = monthlyCategorySpends,
            chartData = chartData,
            accountBalances = accountBalances,
            budgetAlerts = alerts
        )
    }

    // CRUD Actions
    fun addTransaction(
        type: String,
        amount: Double,
        category: CategoryEntity,
        account: AccountEntity,
        dateMillis: Long,
        description: String,
        isRecurring: Boolean = false,
        recurringFrequency: String? = null
    ) {
        viewModelScope.launch {
            repository.insertTransaction(
                TransactionEntity(
                    type = type,
                    amount = amount,
                    categoryId = category.id,
                    categoryName = category.name,
                    accountId = account.id,
                    accountName = account.name,
                    dateMillis = dateMillis,
                    description = description,
                    isRecurring = isRecurring,
                    recurringFrequency = recurringFrequency
                )
            )
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun transferMoney(
        fromAccount: AccountEntity,
        toAccount: AccountEntity,
        amount: Double,
        note: String,
        dateMillis: Long
    ) {
        viewModelScope.launch {
            repository.recordAccountTransfer(fromAccount, toAccount, amount, note, dateMillis)
        }
    }

    fun addCategory(name: String, type: String, iconName: String, colorHex: String) {
        viewModelScope.launch {
            repository.insertCategory(
                CategoryEntity(
                    name = name,
                    type = type,
                    iconName = iconName,
                    colorHex = colorHex,
                    isDefault = false
                )
            )
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    fun addAccount(name: String, type: String, initialBalance: Double, accountNumber: String, colorHex: String, iconName: String) {
        viewModelScope.launch {
            repository.insertAccount(
                AccountEntity(
                    name = name,
                    type = type,
                    initialBalance = initialBalance,
                    accountNumber = accountNumber,
                    colorHex = colorHex,
                    iconName = iconName
                )
            )
        }
    }

    fun updateAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.updateAccount(account)
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.deleteAccount(account)
        }
    }

    fun setBudget(categoryId: Long, categoryName: String, monthlyLimit: Double) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val monthYear = "${calendar.get(Calendar.YEAR)}-${String.format("%02d", calendar.get(Calendar.MONTH) + 1)}"
            repository.insertBudget(
                BudgetEntity(
                    categoryId = categoryId,
                    categoryName = categoryName,
                    monthlyLimit = monthlyLimit,
                    monthYear = monthYear
                )
            )
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }

    fun addSavingsGoal(title: String, targetAmount: Double, targetDateMillis: Long, colorHex: String, iconName: String, notes: String) {
        viewModelScope.launch {
            repository.insertSavingsGoal(
                SavingsGoalEntity(
                    title = title,
                    targetAmount = targetAmount,
                    currentSaved = 0.0,
                    targetDateMillis = targetDateMillis,
                    colorHex = colorHex,
                    iconName = iconName,
                    notes = notes
                )
            )
        }
    }

    fun updateSavingsGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.updateSavingsGoal(goal)
        }
    }

    fun depositToSavingsGoal(goal: SavingsGoalEntity, amount: Double, fromAccount: AccountEntity) {
        viewModelScope.launch {
            repository.recordSavingsDeposit(goal, amount, fromAccount)
        }
    }

    fun deleteSavingsGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(goal)
        }
    }

    // Settings
    fun updateCurrency(code: String, symbol: String) {
        viewModelScope.launch {
            prefsRepository.updateCurrency(code, symbol)
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefsRepository.updateThemeMode(mode)
        }
    }

    fun setPin(pin: String, enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setPinCode(pin, enabled)
        }
    }

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setBiometricEnabled(enabled)
        }
    }

    fun setDailyReminder(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setDailyReminder(enabled)
        }
    }

    fun setBudgetAlerts(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setBudgetAlerts(enabled)
        }
    }

    fun updateProfile(name: String, email: String) {
        viewModelScope.launch {
            prefsRepository.updateProfile(name, email)
            val user = auth.currentUser
            if (user != null && name.isNotBlank()) {
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name.trim())
                        .build()
                    user.updateProfile(profileUpdates).await()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun resetData() {
        viewModelScope.launch {
            repository.resetDatabase()
        }
    }

    fun getCsvContent(): String {
        return repository.generateCsvExport(allTransactions.value)
    }

    fun getJsonBackup(): String {
        return repository.generateJsonBackup(
            allTransactions.value,
            allCategories.value,
            allAccounts.value,
            allBudgets.value,
            allSavingsGoals.value
        )
    }

    suspend fun restoreFromJson(jsonString: String, clearExisting: Boolean = true): com.example.data.repository.RestoreResult {
        return repository.restoreFromJson(jsonString, clearExisting)
    }

    fun continueAsGuest() {
        isGuestMode.value = true
    }

    fun signInWithEmail(
        email: String,
        password: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val user = result.user
                currentUser.value = user
                isGuestMode.value = false
                if (user != null) {
                    val name = user.displayName?.ifBlank { null } ?: user.email?.substringBefore("@") ?: "User"
                    val userEmail = user.email ?: email.trim()
                    prefsRepository.updateProfile(name, userEmail)
                    if (userSettings.value.isAutoSyncEnabled) {
                        triggerAutoSync()
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("no user record", ignoreCase = true) == true -> "No account found with this email. Please create an account."
                    e.message?.contains("invalid-credential", ignoreCase = true) == true ||
                    e.message?.contains("wrong-password", ignoreCase = true) == true -> "Incorrect email or password."
                    e.message?.contains("badly formatted", ignoreCase = true) == true -> "Invalid email address format."
                    else -> e.localizedMessage ?: "Sign-in failed. Please check your credentials."
                }
                onError(msg)
            }
        }
    }

    fun signUpWithEmail(
        name: String,
        email: String,
        password: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = result.user
                if (user != null) {
                    try {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(name.trim())
                            .build()
                        user.updateProfile(profileUpdates).await()
                    } catch (ignored: Exception) {}

                    currentUser.value = user
                    isGuestMode.value = false
                    val finalName = name.trim().ifBlank { user.email?.substringBefore("@") ?: "User" }
                    prefsRepository.updateProfile(finalName, user.email ?: email.trim())
                    if (userSettings.value.isAutoSyncEnabled) {
                        triggerAutoSync()
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("already in use", ignoreCase = true) == true -> "An account with this email already exists. Please log in."
                    e.message?.contains("weak-password", ignoreCase = true) == true -> "Password should be at least 6 characters."
                    e.message?.contains("badly formatted", ignoreCase = true) == true -> "Invalid email address format."
                    else -> e.localizedMessage ?: "Sign-up failed. Please try again."
                }
                onError(msg)
            }
        }
    }

    fun sendPasswordReset(
        email: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email.trim()).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to send password reset email.")
            }
        }
    }

    // Google Sign-In & Cloud Sync
    fun signInWithGoogle(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        GoogleAuthManager.signInWithGoogle(
            activity = activity,
            credentialManager = credentialManager,
            onAuthSuccess = {
                val user = auth.currentUser
                currentUser.value = user
                isGuestMode.value = false
                if (user != null) {
                    viewModelScope.launch {
                        val displayName = user.displayName ?: user.email?.substringBefore("@") ?: "User"
                        val email = user.email ?: ""
                        prefsRepository.updateProfile(displayName, email)
                        triggerAutoSync()
                    }
                }
                onSuccess()
            },
            onAuthError = { err ->
                syncStatus.value = SyncStatus.Error(err)
                onError(err)
            },
            scope = viewModelScope
        )
    }

    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        isGuestMode.value = false
        GoogleAuthManager.signOut(
            context = context,
            credentialManager = credentialManager,
            onSignOutComplete = {
                currentUser.value = null
                syncStatus.value = SyncStatus.Idle
                onComplete()
            },
            scope = viewModelScope
        )
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setAutoSync(enabled)
            if (enabled && currentUser.value != null) {
                triggerAutoSync()
            }
        }
    }

    fun triggerAutoSync() {
        val user = currentUser.value ?: return
        if (!userSettings.value.isAutoSyncEnabled) return
        viewModelScope.launch {
            syncStatus.value = SyncStatus.Syncing
            val success = firestoreSyncRepository.uploadAll(
                userId = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "User",
                transactions = allTransactions.value,
                accounts = allAccounts.value,
                categories = allCategories.value,
                budgets = allBudgets.value,
                savingsGoals = allSavingsGoals.value
            )
            if (success) {
                val now = System.currentTimeMillis()
                prefsRepository.setLastSyncTimestamp(now)
                syncStatus.value = SyncStatus.Success("Synced to cloud", now)
            } else {
                syncStatus.value = SyncStatus.Error("Failed to sync data to cloud")
            }
        }
    }

    fun syncToCloudNow(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val user = currentUser.value
        if (user == null) {
            onComplete(false, "Please sign in with Google to sync to cloud.")
            return
        }
        viewModelScope.launch {
            syncStatus.value = SyncStatus.Syncing
            val success = firestoreSyncRepository.uploadAll(
                userId = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "User",
                transactions = allTransactions.value,
                accounts = allAccounts.value,
                categories = allCategories.value,
                budgets = allBudgets.value,
                savingsGoals = allSavingsGoals.value
            )
            if (success) {
                val now = System.currentTimeMillis()
                prefsRepository.setLastSyncTimestamp(now)
                syncStatus.value = SyncStatus.Success("Cloud backup completed successfully", now)
                onComplete(true, "Cloud backup completed successfully")
            } else {
                syncStatus.value = SyncStatus.Error("Failed to backup to cloud")
                onComplete(false, "Failed to backup to cloud")
            }
        }
    }

    fun restoreFromCloudNow(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val user = currentUser.value
        if (user == null) {
            onComplete(false, "Please sign in with Google to restore from cloud.")
            return
        }
        viewModelScope.launch {
            syncStatus.value = SyncStatus.Syncing
            val bundle = firestoreSyncRepository.downloadAll(user.uid)
            if (bundle == null) {
                syncStatus.value = SyncStatus.Error("Failed to download cloud backup")
                onComplete(false, "Failed to download cloud backup")
                return@launch
            }
            val res = repository.restoreFromCloud(bundle, clearExisting = true)
            if (res.success) {
                val now = System.currentTimeMillis()
                prefsRepository.setLastSyncTimestamp(now)
                syncStatus.value = SyncStatus.Success("Restored ${res.transactionsCount} transactions", now)
                onComplete(true, "Successfully restored ${res.transactionsCount} transactions & ${res.accountsCount} accounts")
            } else {
                syncStatus.value = SyncStatus.Error(res.errorMessage ?: "Failed to restore")
                onComplete(false, res.errorMessage ?: "Failed to restore")
            }
        }
    }

    private fun autoSyncIfEnabled() {
        if (userSettings.value.isAutoSyncEnabled && currentUser.value != null) {
            triggerAutoSync()
        }
    }
}
