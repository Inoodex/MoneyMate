package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val currentUser = Firebase.auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

data class CloudDataBundle(
    val transactions: List<TransactionEntity>,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val budgets: List<BudgetEntity>,
    val savingsGoals: List<SavingsGoalEntity>
)

class FirestoreSyncRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(getFirestoreInstance(context))
    constructor() : this(getFirestoreInstance(null))

    companion object {
        private const val TAG = "FirestoreSync"

        /**
         * Resolves the Firestore instance dynamically.
         * If a custom database ID resource exists (e.g. from firebase_applet_config.xml), it uses it.
         * Otherwise, it seamlessly falls back to the default FirebaseFirestore instance.
         * This avoids compile-time unresolved reference errors in local Android Studio environments.
         */
        fun getFirestoreInstance(context: Context? = null): FirebaseFirestore {
            return try {
                if (context != null) {
                    val appContext = context.applicationContext
                    val resId = appContext.resources.getIdentifier(
                        "firestore_database_id",
                        "string",
                        appContext.packageName
                    )
                    if (resId != 0) {
                        val dbId = appContext.getString(resId)
                        if (dbId.isNotBlank() && dbId != "(default)") {
                            return FirebaseFirestore.getInstance(dbId)
                        }
                    }
                }
                FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "Fallback to default FirebaseFirestore instance: ${e.message}")
                FirebaseFirestore.getInstance()
            }
        }
    }

    private fun requireUserId(): String {
        return Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("User must be authenticated before performing Firestore operations.")
    }

    suspend fun uploadAll(
        userId: String,
        email: String,
        displayName: String,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        savingsGoals: List<SavingsGoalEntity>
    ): Boolean {
        return try {
            val userDocRef = db.collection("users").document(userId)

            // Save user profile metadata
            userDocRef.set(
                mapOf(
                    "email" to email,
                    "displayName" to displayName,
                    "lastSyncTime" to System.currentTimeMillis(),
                    "autoSyncEnabled" to true
                ),
                SetOptions.merge()
            ).await()

            // Upload transactions
            val txCol = userDocRef.collection("transactions")
            for (tx in transactions) {
                val data = mapOf(
                    "type" to tx.type,
                    "amount" to tx.amount,
                    "categoryId" to tx.categoryId,
                    "categoryName" to tx.categoryName,
                    "accountId" to tx.accountId,
                    "accountName" to tx.accountName,
                    "toAccountId" to (tx.toAccountId ?: 0L),
                    "toAccountName" to (tx.toAccountName ?: ""),
                    "dateMillis" to tx.dateMillis,
                    "description" to tx.description,
                    "isRecurring" to tx.isRecurring,
                    "recurringFrequency" to (tx.recurringFrequency ?: "")
                )
                txCol.document(tx.id.toString()).set(data, SetOptions.merge()).await()
            }

            // Upload accounts
            val accCol = userDocRef.collection("accounts")
            for (acc in accounts) {
                val data = mapOf(
                    "name" to acc.name,
                    "type" to acc.type,
                    "initialBalance" to acc.initialBalance,
                    "accountNumber" to (acc.accountNumber ?: ""),
                    "colorHex" to acc.colorHex,
                    "iconName" to acc.iconName
                )
                accCol.document(acc.id.toString()).set(data, SetOptions.merge()).await()
            }

            // Upload categories
            val catCol = userDocRef.collection("categories")
            for (cat in categories) {
                val data = mapOf(
                    "name" to cat.name,
                    "type" to cat.type,
                    "iconName" to cat.iconName,
                    "colorHex" to cat.colorHex,
                    "isDefault" to cat.isDefault
                )
                catCol.document(cat.id.toString()).set(data, SetOptions.merge()).await()
            }

            // Upload budgets
            val budgetCol = userDocRef.collection("budgets")
            for (b in budgets) {
                val data = mapOf(
                    "categoryId" to b.categoryId,
                    "categoryName" to b.categoryName,
                    "monthlyLimit" to b.monthlyLimit,
                    "monthYear" to b.monthYear
                )
                budgetCol.document(b.id.toString()).set(data, SetOptions.merge()).await()
            }

            // Upload savings goals
            val goalCol = userDocRef.collection("savingsGoals")
            for (g in savingsGoals) {
                val data = mapOf(
                    "title" to g.title,
                    "targetAmount" to g.targetAmount,
                    "currentSaved" to g.currentSaved,
                    "targetDateMillis" to g.targetDateMillis,
                    "colorHex" to g.colorHex,
                    "iconName" to g.iconName,
                    "notes" to g.notes
                )
                goalCol.document(g.id.toString()).set(data, SetOptions.merge()).await()
            }

            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId")
            false
        }
    }

    suspend fun downloadAll(userId: String): CloudDataBundle? {
        return try {
            val userDocRef = db.collection("users").document(userId)

            val txDocs = userDocRef.collection("transactions").get().await()
            val transactions = txDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.id.toLongOrNull() ?: 0L
                    TransactionEntity(
                        id = id,
                        type = doc.getString("type") ?: "EXPENSE",
                        amount = doc.getDouble("amount") ?: 0.0,
                        categoryId = doc.getLong("categoryId") ?: 1L,
                        categoryName = doc.getString("categoryName") ?: "Other",
                        accountId = doc.getLong("accountId") ?: 1L,
                        accountName = doc.getString("accountName") ?: "Cash Wallet",
                        toAccountId = doc.getLong("toAccountId")?.takeIf { it > 0 },
                        toAccountName = doc.getString("toAccountName").takeIf { !it.isNullOrBlank() },
                        dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis(),
                        description = doc.getString("description") ?: "",
                        isRecurring = doc.getBoolean("isRecurring") ?: false,
                        recurringFrequency = doc.getString("recurringFrequency").takeIf { !it.isNullOrBlank() }
                    )
                } catch (e: Exception) {
                    null
                }
            }

            val accDocs = userDocRef.collection("accounts").get().await()
            val accounts = accDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.id.toLongOrNull() ?: 0L
                    AccountEntity(
                        id = id,
                        name = doc.getString("name") ?: "Account",
                        type = doc.getString("type") ?: "CASH",
                        initialBalance = doc.getDouble("initialBalance") ?: 0.0,
                        accountNumber = doc.getString("accountNumber"),
                        colorHex = doc.getString("colorHex") ?: "#1E6FD9",
                        iconName = doc.getString("iconName") ?: "AccountBalance"
                    )
                } catch (e: Exception) {
                    null
                }
            }

            val catDocs = userDocRef.collection("categories").get().await()
            val categories = catDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.id.toLongOrNull() ?: 0L
                    CategoryEntity(
                        id = id,
                        name = doc.getString("name") ?: "Other",
                        type = doc.getString("type") ?: "EXPENSE",
                        iconName = doc.getString("iconName") ?: "MoreHoriz",
                        colorHex = doc.getString("colorHex") ?: "#3B82F6",
                        isDefault = doc.getBoolean("isDefault") ?: false
                    )
                } catch (e: Exception) {
                    null
                }
            }

            val budgetDocs = userDocRef.collection("budgets").get().await()
            val budgets = budgetDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.id.toLongOrNull() ?: 0L
                    BudgetEntity(
                        id = id,
                        categoryId = doc.getLong("categoryId") ?: 1L,
                        categoryName = doc.getString("categoryName") ?: "Food",
                        monthlyLimit = doc.getDouble("monthlyLimit") ?: 5000.0,
                        monthYear = doc.getString("monthYear") ?: "2026-10"
                    )
                } catch (e: Exception) {
                    null
                }
            }

            val goalDocs = userDocRef.collection("savingsGoals").get().await()
            val savingsGoals = goalDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.id.toLongOrNull() ?: 0L
                    SavingsGoalEntity(
                        id = id,
                        title = doc.getString("title") ?: "Goal",
                        targetAmount = doc.getDouble("targetAmount") ?: 10000.0,
                        currentSaved = doc.getDouble("currentSaved") ?: 0.0,
                        targetDateMillis = doc.getLong("targetDateMillis") ?: (System.currentTimeMillis() + 86400000L * 30),
                        colorHex = doc.getString("colorHex") ?: "#10B981",
                        iconName = doc.getString("iconName") ?: "Savings",
                        notes = doc.getString("notes") ?: ""
                    )
                } catch (e: Exception) {
                    null
                }
            }

            CloudDataBundle(
                transactions = transactions,
                accounts = accounts,
                categories = categories,
                budgets = budgets,
                savingsGoals = savingsGoals
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$userId")
            null
        }
    }

    suspend fun syncTransaction(userId: String, tx: TransactionEntity) {
        try {
            val data = mapOf(
                "type" to tx.type,
                "amount" to tx.amount,
                "categoryId" to tx.categoryId,
                "categoryName" to tx.categoryName,
                "accountId" to tx.accountId,
                "accountName" to tx.accountName,
                "toAccountId" to (tx.toAccountId ?: 0L),
                "toAccountName" to (tx.toAccountName ?: ""),
                "dateMillis" to tx.dateMillis,
                "description" to tx.description,
                "isRecurring" to tx.isRecurring,
                "recurringFrequency" to (tx.recurringFrequency ?: "")
            )
            db.collection("users").document(userId)
                .collection("transactions").document(tx.id.toString())
                .set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId/transactions/${tx.id}")
        }
    }

    suspend fun deleteTransaction(userId: String, txId: Long) {
        try {
            db.collection("users").document(userId)
                .collection("transactions").document(txId.toString())
                .delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId/transactions/$txId")
        }
    }
}
